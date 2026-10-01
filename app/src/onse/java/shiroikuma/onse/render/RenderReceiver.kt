package shiroikuma.onse.render

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import shiroikuma.onse.automation.AutomationAuth
import shiroikuma.onse.automation.StateExportReceiver

/**
 * The render contract's entry point (`docs/sister-app-contract-onse-render.md`): `RENDER` queues a
 * batch on [RenderService] and returns at once; `CANCEL_RENDER` stops one. Gated by the same
 * switch and optional token as the 保存復元 automation. Every answer is a fresh broadcast with
 * string extras only (EMUI drops binders and ordered results).
 */
class RenderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val token = intent.getStringExtra(EXTRA_TOKEN)
        when (intent.action) {
            "${app.packageName}$ACTION_RENDER" -> {
                val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION) ?: return
                val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE) ?: return
                val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
                AutomationAuth.refuse(app, token)?.let {
                    done(app, replyAction, replyPackage, requestId, it)
                    return
                }
                if (intent.getStringExtra(EXTRA_BATCH_PATH).isNullOrBlank()) {
                    done(app, replyAction, replyPackage, requestId, "ERROR:no batch_path")
                    return
                }
                try {
                    ContextCompat.startForegroundService(
                        app,
                        Intent(app, RenderService::class.java).apply { putExtras(intent) },
                    )
                } catch (e: Exception) {
                    done(app, replyAction, replyPackage, requestId, StateExportReceiver.wireError(app, e))
                }
            }
            "${app.packageName}$ACTION_PING" -> {
                val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION) ?: return
                val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE) ?: return
                val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: ""
                pong(app, replyAction, replyPackage, requestId, AutomationAuth.refuse(app, token))
            }
            "${app.packageName}$ACTION_CANCEL_RENDER" -> {
                if (AutomationAuth.refuse(app, token) != null) return
                RenderService.requestCancel(intent.getStringExtra(EXTRA_REQUEST_ID))
            }
        }
    }

    companion object {
        const val ACTION_RENDER = ".action.RENDER"
        const val ACTION_CANCEL_RENDER = ".action.CANCEL_RENDER"
        const val ACTION_PING = ".action.PING"

        const val EXTRA_TOKEN = "token"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_REPLY_ACTION = "reply_action"
        const val EXTRA_REPLY_PACKAGE = "reply_package"
        const val EXTRA_BATCH_PATH = "batch_path"
        const val EXTRA_SPEAKER = "speaker"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_PITCH = "pitch"
        const val EXTRA_INTONATION = "intonation"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_GAP_PRE = "gap_pre"
        const val EXTRA_GAP_POST = "gap_post"
        const val EXTRA_BITRATE = "bitrate_kbps"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_LANG = "lang"
        const val EXTRA_EN_VOICE = "en_voice"

        /**
         * `PING` → `event=pong`: `result` is `OK` (or the gate's `ERROR:`), plus `version`,
         * `styles` (installed readable style ids, comma-separated), `storage` and `battery_exempt`
         * (`true`/`false`) — so a caller can tell "not running" from "slow", and see why a render
         * would fail before sending one.
         */
        fun pong(context: Context, replyAction: String, replyPackage: String, requestId: String, refusal: String?) {
            val catalog = shiroikuma.onse.voice.VoiceCatalog.get(context)
            val styles = catalog.readable.filter { shiroikuma.onse.voice.VoiceStore.isInstalled(context, it.model) }
                .joinToString(",") { it.style.id.toString() }
            context.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REQUEST_ID, requestId)
                    putExtra("event", "pong")
                    putExtra("result", refusal ?: "OK")
                    putExtra("version", runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty())
                    putExtra("styles", if (refusal == null) styles else "")
                    putExtra("storage", android.os.Environment.isExternalStorageManager().toString())
                    putExtra("battery_exempt", shiroikuma.onse.ui.isBatteryExempt(context).toString())
                    putExtra("en_installed", shiroikuma.onse.english.EnglishStore.isInstalled(context).toString())
                    putExtra("en_voices", if (refusal == null) shiroikuma.onse.english.EnglishCatalog.get(context).voices.joinToString(",") { it.name } else "")
                    putExtra("en_default", shiroikuma.onse.voice.VoiceSettingsStore.current(context).enVoice)
                },
            )
        }

        /** The terminal answer for a request: `event=done`, `result` = `OK:…` or `ERROR:…`. */
        fun done(context: Context, replyAction: String, replyPackage: String, requestId: String, result: String) {
            context.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REQUEST_ID, requestId)
                    putExtra("event", "done")
                    putExtra("result", result)
                },
            )
        }

        /** One item's answer: `event=item`, `id`, `status` OK/ERROR, `out_path`, `duration_ms`, `error`. */
        fun item(
            context: Context,
            replyAction: String,
            replyPackage: String,
            requestId: String,
            id: String,
            ok: Boolean,
            outPath: String,
            durationMs: Long,
            error: String?,
            index: Int,
            total: Int,
        ) {
            context.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REQUEST_ID, requestId)
                    putExtra("event", "item")
                    putExtra("id", id)
                    putExtra("status", if (ok) "OK" else "ERROR")
                    putExtra("out_path", outPath)
                    putExtra("duration_ms", durationMs.toString())
                    putExtra("index", index.toString())
                    putExtra("total", total.toString())
                    error?.let { putExtra("error", it.replace('\n', ' ')) }
                },
            )
        }
    }
}
