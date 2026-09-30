/*
 * 白い熊 音声 (shiroikuma-onse) fork: where a 保存復元 data export or import actually runs —
 * contract v2 §2a. Ported from shiroikuma-jinsoningen.
 */

package shiroikuma.onse.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.ztssst.voicevox_tts.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import shiroikuma.onse.ui.Backup
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams the archive into (export) or out of (import) the caller's descriptor on a foreground
 * service. [onStartCommand] follows the contract's one recipe: read the extras → drain the
 * descriptor from [HANDOVER] → go foreground inside a `try` (a refusal is answered with the
 * terminal broadcast, since the provider already said OK) → only then the early returns. One
 * `handedOff` flag gives the descriptor exactly one owner on every path.
 */
class AutomationDataService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) ?: false
        val jobId = intent?.getStringExtra(EXTRA_JOB)
        val replyAction = intent?.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent?.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)
        val fd = jobId?.let { HANDOVER.remove(it) }
        var handedOff = false
        try {
            try {
                startForeground(NOTIFICATION_ID, notification(importing))
            } catch (exception: Exception) {
                if (jobId != null && !replyAction.isNullOrEmpty() && !replyPackage.isNullOrEmpty()) {
                    AutomationJobs.finish(jobId)
                    sendBroadcast(
                        Intent(replyAction).apply {
                            setPackage(replyPackage)
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                            putExtra(AutomationProvider.KEY_RESULT, StateExportReceiver.wireError(applicationContext, exception))
                        },
                    )
                }
                stopSelf(startId)
                return START_NOT_STICKY
            }
            // A stale or already-claimed job id stops SILENTLY — its one terminal reply was sent.
            if (jobId == null || fd == null) return stop(startId)
            handedOff = dispatch(intent, startId, jobId, fd, importing)
            return START_NOT_STICKY
        } finally {
            if (!handedOff) runCatching { fd?.close() }
        }
    }

    private fun dispatch(intent: Intent, startId: Int, jobId: String, fd: ParcelFileDescriptor, importing: Boolean): Boolean {
        val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)
        val progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)
        val items = intent.getStringExtra(AutomationProvider.KEY_ITEMS)

        val replied = AtomicBoolean(false)
        val reply: (String) -> Unit = { result ->
            if (replied.compareAndSet(false, true)) {
                AutomationJobs.finish(jobId)
                // No reply_package → nobody to answer; never an implicit broadcast.
                if (!replyAction.isNullOrEmpty() && !replyPackage.isNullOrEmpty()) {
                    sendBroadcast(
                        Intent(replyAction).apply {
                            setPackage(replyPackage)
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                            putExtra(AutomationProvider.KEY_RESULT, result)
                        },
                    )
                }
            }
        }

        scope.launch {
            val wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
                ?.apply { setReferenceCounted(false) }
            try {
                wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)
                fd.use { open ->
                    if (importing) runImport(open, reply) else runExport(jobId, open, items, progressAction, replyPackage, reply)
                }
            } catch (e: Backup.Cancelled) {
                reply("ERROR:cancelled")
            } catch (throwable: Throwable) {
                reply("ERROR:${throwable.message ?: throwable.javaClass.simpleName}")
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return true
    }

    /**
     * Streams the ZIP straight into the caller's descriptor, counting bytes as it goes (the caller
     * owns the file; it may be a pipe we cannot stat). The descriptor can stall for as long as the
     * caller is slow to drain it, so a heartbeat re-sends the last true progress line every 20 s.
     */
    private fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progressAction: String?,
        replyPackage: String?,
        reply: (String) -> Unit,
    ) {
        val cats = resolve(items) ?: run { reply("ERROR:unknown category in items: $items"); return }
        val counter = CountingStream(ParcelFileDescriptor.AutoCloseOutputStream(fd))
        var last: Triple<Backup.Cat, Int, Int>? = null
        val heartbeat = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                last?.let { (c, p, t) -> AutomationProgress.send(this@AutomationDataService, progressAction, replyPackage, jobId, c, p, t, counter.count) }
            }
        }
        val done = try {
            counter.use { out ->
                Backup.writeZip(
                    context = applicationContext,
                    cats = cats,
                    appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty(),
                    out = out,
                    isCancelled = { AutomationJobs.isCancelled(jobId) },
                    onProgress = { cat, position, total ->
                        last = Triple(cat, position, total)
                        AutomationProgress.send(this, progressAction, replyPackage, jobId, cat, position, total, counter.count)
                    },
                )
            }
        } finally {
            heartbeat.cancel()
        }
        last?.let { (c, _, t) -> AutomationProgress.send(this, progressAction, replyPackage, jobId, c, t, t, counter.count) }
        reply("OK:${counter.count}|${done.size} categories")
    }

    /**
     * Spool the whole archive to the cache directory first (never into memory — imported fonts are
     * unbounded), then restore only the categories it actually carries. Every write is durable
     * before the reply: 応用管理 force-stops the app the instant it hears OK.
     */
    private fun runImport(fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        val spool = File(cacheDir, SPOOL_NAME)
        try {
            val size = ParcelFileDescriptor.AutoCloseInputStream(fd).use { input -> spool.outputStream().use { input.copyTo(it) } }
            if (size == 0L) { reply("ERROR:empty archive"); return }
            val present = runCatching { Backup.categoriesIn(spool) }.getOrElse { reply("ERROR:archive unreadable"); return }
            if (present.isEmpty()) { reply("ERROR:archive carries no categories"); return }
            val result = Backup.import(applicationContext, spool, present)
            reply("OK:${result.lines.size} restored")
        } finally {
            spool.delete()
        }
    }

    /** `items` absent means OUR DEFAULT SET. An unknown id refuses the whole run. */
    private fun resolve(items: String?): Set<Backup.Cat>? {
        if (items.isNullOrBlank()) return Backup.Cat.defaultSelection
        val wanted = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { Backup.Cat.byId(it) }
        return if (found.size == wanted.size) found.toSet() else null
    }

    private fun notification(importing: Boolean): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "自動化データ", NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (importing) "データを戻しています…" else "データを書き出しています…")
            .setOngoing(true)
            .build()
    }

    private fun stop(startId: Int): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    /** A named class, not an anonymous object capturing a local — the shape that crashed AGP lint. */
    private class CountingStream(private val out: OutputStream) : OutputStream() {
        @Volatile var count = 0L
            private set
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
        override fun flush() = out.flush()
        override fun close() = out.close()
    }

    companion object {
        private const val CHANNEL = "onse_automation_data"
        private const val NOTIFICATION_ID = 92_002
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"
        private const val SPOOL_NAME = "onse-automation-import.zip"
        private const val WAKELOCK_TAG = "shiroikuma-onse:automation-data"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val HEARTBEAT_MS = 20_000L

        /** One open descriptor, one owner: handed across by job id, never inside an Intent. */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        fun start(context: Context, jobId: String, fd: ParcelFileDescriptor, importing: Boolean, extras: Bundle?) {
            HANDOVER[jobId] = fd
            ContextCompat.startForegroundService(
                context,
                Intent(context, AutomationDataService::class.java).apply {
                    putExtra(EXTRA_JOB, jobId)
                    putExtra(EXTRA_IMPORTING, importing)
                    putExtra(AutomationProvider.KEY_ITEMS, extras?.getString(AutomationProvider.KEY_ITEMS))
                    putExtra(AutomationProvider.KEY_REPLY_ACTION, extras?.getString(AutomationProvider.KEY_REPLY_ACTION))
                    putExtra(AutomationProvider.KEY_REPLY_PACKAGE, extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE))
                    putExtra(AutomationProvider.KEY_PROGRESS_ACTION, extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION))
                },
            )
        }

        /** Drop a descriptor handed over for a service that never started. */
        fun discard(jobId: String) {
            HANDOVER.remove(jobId)
        }
    }
}
