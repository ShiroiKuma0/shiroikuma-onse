/*
 * 白い熊 音声 (shiroikuma-onse) fork: the 保存復元 data door — contract v2 §2a.
 * Ported from shiroikuma-jinsoningen.
 */

package shiroikuma.onse.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import shiroikuma.onse.ui.Backup

/**
 * Export this app's state, and put it back, for a caller identified by exact name, uid and pinned
 * signing certificate ([AutomationCallers]). Four methods, all short, none carrying the payload —
 * the bytes go through a descriptor the caller opened, streamed by [AutomationDataService].
 * A refusal is RETURNED in the `result` string, never thrown across the binder.
 *
 * `describe` reads only the package manager and plain constants: a provider call can arrive before
 * `Application.onCreate` has run (the clean-phone case), so nothing here may depend on app state.
 */
class AutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context?.applicationContext ?: return result("ERROR:not ready")
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return result(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        AutomationAuth.refuse(ctx, extras?.getString(KEY_TOKEN))?.let { return result(it) }
        return when (method) {
            METHOD_DESCRIBE -> result(describe(ctx))
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                result("OK:cancelled")
            }
            else -> result("ERROR:unknown method: $method")
        }
    }

    /**
     * The header. `requires_permissions` is `[]` after following the writes: the import touches only
     * this app's own preferences and files. Voice models are not in the archive — the list of
     * installed ones is, and they are re-downloaded when the app next opens. No preference restored
     * here guards another surface (look, voice knobs, installed-voice list only).
     */
    private fun describe(ctx: Context): String {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        val code = info.versionCode
        val contains = (Backup.Cat.defaultSelection.map { it.label } + Backup.CONTAINS_NO_MODELS)
            .joinToString(",") { "\"" + it.replace("\"", "'") + "\"" }
        return "OK:{\"app_id\":\"${ctx.packageName}\",\"version_code\":$code," +
            "\"version_name\":\"${info.versionName.orEmpty()}\"," +
            "\"format\":$FORMAT,\"min_format_readable\":$MIN_FORMAT_READABLE," +
            "\"requires_launch_first\":false,\"requires_permissions\":[]," +
            "\"contains\":[$contains]}"
    }

    /** Duplicate the descriptor before `call()` returns (the original dies with the transaction). */
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        @Suppress("DEPRECATION")
        val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD) ?: return result("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return result("ERROR:descriptor unusable")
        val items = extras.getString(KEY_ITEMS).orEmpty()
        val unknown = items.split(',').map { it.trim() }.filter { it.isNotBlank() && Backup.Cat.byId(it) == null }
        if (unknown.isNotEmpty()) {
            runCatching { dup.close() }
            return result("ERROR:unknown category in items: ${unknown.joinToString(",")}")
        }
        val jobId = AutomationJobs.begin()
        return runCatching {
            AutomationDataService.start(ctx, jobId, dup, importing, extras)
            result("OK:$jobId")
        }.getOrElse { failure ->
            // Refused as the RETURN VALUE: no OK:<job_id> is ever handed out for a job that won't run.
            AutomationJobs.finish(jobId)
            AutomationDataService.discard(jobId)
            runCatching { dup.close() }
            result(StateExportReceiver.wireError(ctx, failure as? Exception ?: RuntimeException(failure)))
        }
    }

    private fun result(s: String) = Bundle().apply { putString(KEY_RESULT, s) }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
        throw UnsupportedOperationException("automation is call() only")
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("automation is call() only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = throw UnsupportedOperationException("automation is call() only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /** = the ZIP manifest's version; the manifest meta-data repeats it as a literal — keep in step. */
        const val FORMAT = Backup.VERSION
        const val MIN_FORMAT_READABLE = 1
    }
}
