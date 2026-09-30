/*
 * 白い熊 音声 (shiroikuma-onse) fork: where the headless 保存復元 export actually runs (§1).
 * Ported from shiroikuma-jinsoningen.
 */

package shiroikuma.onse.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.ztssst.voicevox_tts.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import shiroikuma.onse.ui.Backup
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the export off the broadcast window, reports progress with real counts and sends exactly
 * one terminal reply. The recipe in [onStartCommand] is the contract's: read the extras → go
 * foreground inside a `try` (a refusal is answered) → only then the early returns; and the
 * "already running" flag is claimed only after the promotion succeeded.
 */
class StateExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val replyAction = intent?.getStringExtra(StateExportReceiver.EXTRA_REPLY_ACTION)
        val replyPackage = intent?.getStringExtra(StateExportReceiver.EXTRA_REPLY_PACKAGE)
        val replyId = intent?.getStringExtra(StateExportReceiver.EXTRA_REPLY_ID)

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (exception: Exception) {
            if (replyAction != null && replyPackage != null && replyId != null) {
                StateExportReceiver.reply(applicationContext, replyAction, replyPackage, replyId, StateExportReceiver.wireError(applicationContext, exception))
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val request = intent
        if (request == null || replyAction == null || replyPackage == null || replyId == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running.compareAndSet(false, true)) {
            StateExportReceiver.reply(applicationContext, replyAction, replyPackage, replyId, "ERROR:export already running")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        cancelled = false
        runningReplyId = replyId

        val replied = AtomicBoolean(false)
        val reply: (String) -> Unit = { result ->
            if (replied.compareAndSet(false, true)) {
                StateExportReceiver.reply(applicationContext, replyAction, replyPackage, replyId, result)
            }
        }

        scope.launch {
            val wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
                ?.apply { setReferenceCounted(false) }
            try {
                wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)
                runExport(request, replyId, replyPackage, reply)
            } catch (e: Backup.Cancelled) {
                reply("ERROR:cancelled")
            } catch (exception: Exception) {
                reply("ERROR:${exception.message ?: exception.javaClass.simpleName}")
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                runningReplyId = null
                running.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun runExport(request: Intent, replyId: String, replyPackage: String, reply: (String) -> Unit) {
        val progressAction = request.getStringExtra(StateExportReceiver.EXTRA_PROGRESS_ACTION)
        // `items` absent means OUR DEFAULT SET (the `on` categories), not everything.
        val cats = request.getStringExtra(StateExportReceiver.EXTRA_ITEMS).orEmpty()
            .split(',').map { it.trim() }.filter { it.isNotBlank() }
            .mapNotNull { Backup.Cat.byId(it) }.toSet()
            .ifEmpty { Backup.Cat.defaultSelection }

        // Directory precedence: `path` → the configured directory → ERROR:no-directory.
        val dir = request.getStringExtra(StateExportReceiver.EXTRA_PATH)?.takeIf { it.isNotBlank() }
            ?: Backup.dir(applicationContext)
            ?: run { reply("ERROR:no-directory"); return }
        // This app DECLARES MANAGE_EXTERNAL_STORAGE, so the keyed refusal is the correct answer —
        // never a fallback somewhere else. Checked before touching the path.
        if (!Environment.isExternalStorageManager()) {
            reply("ERROR:no-storage-access")
            return
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val result = Backup.exportToDirectory(
            context = applicationContext,
            cats = cats,
            appVersion = version,
            dirPath = dir,
            isCancelled = { cancelled },
            onProgress = { cat, position, total ->
                AutomationProgress.send(this, progressAction, replyPackage, replyId, cat, position, total)
            },
        )
        val size = result.file.length()
        reply("OK:${result.file.absolutePath}|$size|${Backup.humanSize(size)}|${result.categories} categories")
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "自動化の書き出し", NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("設定を書き出しています…")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        runningReplyId = null
        running.set(false)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "onse_automation_export"
        private const val NOTIFICATION_ID = 92_001
        private const val WAKELOCK_TAG = "shiroikuma-onse:automation-export"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L

        /** Process-local, never persisted — a persisted flag wedges the app after one crash. */
        private val running = AtomicBoolean(false)
        @Volatile private var cancelled = false
        @Volatile private var runningReplyId: String? = null

        /** Unwind the running export at its next category boundary; a silent no-op otherwise. */
        fun requestCancel(replyId: String?) {
            if (!running.get()) return
            if (replyId != null && replyId != runningReplyId) return
            cancelled = true
        }
    }
}
