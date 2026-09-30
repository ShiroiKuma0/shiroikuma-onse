/*
 * 白い熊 音声 (shiroikuma-onse) fork: the 保存復元 automation entry point (contract v2 §1).
 * Ported from shiroikuma-jinsoningen.
 */

package shiroikuma.onse.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import shiroikuma.onse.ui.Backup

/**
 * The exported receiver 白い熊 自由作業盤 fires at: `EXPORT_STATE`, `LIST_CATEGORIES`,
 * `CANCEL_EXPORT`. It does **no work** — gate, answer `LIST_CATEGORIES` inline, hand
 * `EXPORT_STATE` to [StateExportService] (a manifest receiver that runs an export gets ANR'd and
 * killed mid-write), signal a running export on `CANCEL_EXPORT`.
 *
 * Deliberately the **unauthenticated** half of the surface: it only writes where it is told and
 * reports what it did. `import` lives only behind [AutomationProvider], which knows its caller.
 */
class StateExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val token = intent.getStringExtra(EXTRA_TOKEN)

        when (action) {
            "${app.packageName}$ACTION_LIST_CATEGORIES" -> {
                val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION) ?: return
                val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE) ?: return
                val replyId = intent.getStringExtra(EXTRA_REPLY_ID) ?: return
                val result = AutomationAuth.refuse(app, token) ?: ("OK:" + categoryLines())
                reply(app, replyAction, replyPackage, replyId, result)
            }

            "${app.packageName}$ACTION_EXPORT_STATE" -> {
                val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION) ?: return
                val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE) ?: return
                val replyId = intent.getStringExtra(EXTRA_REPLY_ID) ?: return

                AutomationAuth.refuse(app, token)?.let { refusal ->
                    reply(app, replyAction, replyPackage, replyId, refusal)
                    return
                }
                val items = intent.getStringExtra(EXTRA_ITEMS).orEmpty()
                val unknown = items.split(',').map { it.trim() }.filter { it.isNotBlank() && Backup.Cat.byId(it) == null }
                if (unknown.isNotEmpty()) {
                    reply(app, replyAction, replyPackage, replyId, "ERROR:unknown category in items: ${unknown.joinToString(",")}")
                    return
                }
                // A broadcast is a BACKGROUND start on API 31+: guarded, and a refusal is ANSWERED
                // rather than swallowed — a silent no-export is indistinguishable from no contract.
                try {
                    ContextCompat.startForegroundService(
                        app,
                        Intent(app, StateExportService::class.java).apply {
                            putExtra(EXTRA_PATH, intent.getStringExtra(EXTRA_PATH))
                            putExtra(EXTRA_ITEMS, items)
                            putExtra(EXTRA_PROGRESS_ACTION, intent.getStringExtra(EXTRA_PROGRESS_ACTION))
                            putExtra(EXTRA_REPLY_ACTION, replyAction)
                            putExtra(EXTRA_REPLY_PACKAGE, replyPackage)
                            putExtra(EXTRA_REPLY_ID, replyId)
                        },
                    )
                } catch (exception: Exception) {
                    reply(app, replyAction, replyPackage, replyId, wireError(app, exception))
                }
            }

            "${app.packageName}$ACTION_CANCEL_EXPORT" -> {
                // Fire-and-forget; a silent no-op when nothing runs.
                if (AutomationAuth.refuse(app, token) != null) return
                StateExportService.requestCancel(intent.getStringExtra(EXTRA_REPLY_ID))
            }
        }
    }

    /** `id⇥label⇥parent⇥on|off`. The fourth field is positional: an `off` top-level needs the empty third. */
    private fun categoryLines(): String =
        Backup.Cat.entries.joinToString("\n") { cat ->
            val parent = cat.parent.orEmpty()
            if (cat.defaultOn) {
                if (parent.isEmpty()) "${cat.id}\t${cat.label}" else "${cat.id}\t${cat.label}\t$parent"
            } else {
                "${cat.id}\t${cat.label}\t$parent\toff"
            }
        }

    companion object {
        const val ACTION_EXPORT_STATE = ".action.EXPORT_STATE"
        const val ACTION_LIST_CATEGORIES = ".action.LIST_CATEGORIES"
        const val ACTION_CANCEL_EXPORT = ".action.CANCEL_EXPORT"

        const val EXTRA_TOKEN = "token"
        const val EXTRA_PATH = "path"
        const val EXTRA_ITEMS = "items"
        const val EXTRA_PROGRESS_ACTION = "progress_action"
        const val EXTRA_REPLY_ACTION = "reply_action"
        const val EXTRA_REPLY_PACKAGE = "reply_package"
        const val EXTRA_REPLY_ID = "reply_id"

        /**
         * A failed start as one wire line. `ERROR:no-foreground-start` (which earns the caller's
         * 「電池最適化を除外」 button) only when BOTH hold: the platform refused the foreground start
         * (matched by class NAME — the class is API 31) and this app is not already battery-exempt.
         */
        fun wireError(context: Context, exception: Exception): String {
            if (exemptionWouldFix(context, exception)) return "ERROR:no-foreground-start"
            val detail = exception.message?.takeIf { it.isNotBlank() } ?: exception.javaClass.simpleName
            return "ERROR:" + detail.replace('\n', ' ').replace('\r', ' ').trim()
        }

        private fun exemptionWouldFix(context: Context, exception: Exception): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
            if (exception.javaClass.name != "android.app.ForegroundServiceStartNotAllowedException") return false
            val power = context.getSystemService(PowerManager::class.java) ?: return false
            return !power.isIgnoringBatteryOptimizations(context.packageName)
        }

        /** A fresh broadcast — never a Binder, never the ordered result (EMUI severs both). */
        fun reply(context: Context, replyAction: String, replyPackage: String, replyId: String, result: String) {
            context.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra("result", result)
                },
            )
        }
    }
}
