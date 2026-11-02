package shiroikuma.onse.render

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.ztssst.voicevox_tts.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONObject
import shiroikuma.onse.automation.StateExportReceiver
import shiroikuma.onse.voice.OnseEngine
import shiroikuma.onse.voice.VoiceCatalog
import shiroikuma.onse.voice.VoiceSettings
import shiroikuma.onse.voice.VoiceSettingsStore
import shiroikuma.onse.voice.VoiceStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Renders batches of sentences to audio files for sister apps. Requests are queued and worked
 * one at a time on a single worker (the synthesizer is one per process); each item is answered as
 * soon as its file is in place, then the request gets one `done`. Foreground (specialUse) with a
 * partial wakelock — synthesis runs a few seconds per sentence on the phone.
 *
 * Two lanes: a `priority=high` request goes ahead of every queued normal one, and a running normal
 * request yields to it at its next item boundary, then carries on (地図's "next turn" prompt
 * overtaking its look-ahead batch). High requests run among themselves in arrival order.
 *
 * [onStartCommand] keeps the family recipe: read the reply address → guarded `startForeground`
 * (a refusal is answered) → only then validation and queuing.
 */
class RenderService : Service() {

    private data class Request(
        val requestId: String,
        val replyAction: String,
        val replyPackage: String,
        val batchPath: String,
        val params: Intent,
        val high: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** One token per queued request; the worker takes whichever request is first in line. */
    private val wake = Channel<Unit>(Channel.UNLIMITED)
    private val urgent = ArrayDeque<Request>()
    private val normal = ArrayDeque<Request>()
    private val pending = AtomicInteger(0)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch { for (t in wake) next()?.let { runRequest(it) } }
    }

    private fun next(): Request? = synchronized(urgent) { urgent.removeFirstOrNull() ?: normal.removeFirstOrNull() }

    /**
     * Called by a normal request at each item boundary: runs every waiting high request to its
     * `done` first. A token left behind in [wake] then finds nothing and is skipped.
     */
    private fun yieldToUrgent(current: Request) {
        if (current.high) return
        while (true) runRequest(synchronized(urgent) { urgent.removeFirstOrNull() } ?: return)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val replyAction = intent?.getStringExtra(RenderReceiver.EXTRA_REPLY_ACTION)
        val replyPackage = intent?.getStringExtra(RenderReceiver.EXTRA_REPLY_PACKAGE)
        val requestId = intent?.getStringExtra(RenderReceiver.EXTRA_REQUEST_ID)
        try {
            startForeground(NOTIFICATION_ID, notification("音声を作成しています…"))
        } catch (e: Exception) {
            if (replyAction != null && replyPackage != null && requestId != null) {
                RenderReceiver.done(applicationContext, replyAction, replyPackage, requestId, StateExportReceiver.wireError(applicationContext, e))
            }
            if (pending.get() == 0) stopSelf(startId)
            return START_NOT_STICKY
        }
        val batch = intent?.getStringExtra(RenderReceiver.EXTRA_BATCH_PATH)
        if (intent == null || replyAction == null || replyPackage == null || requestId == null || batch == null) {
            if (pending.get() == 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId) }
            return START_NOT_STICKY
        }
        pending.incrementAndGet()
        acquireWakeLock()
        val high = intent.getStringExtra(RenderReceiver.EXTRA_PRIORITY).equals("high", true)
        val request = Request(requestId, replyAction, replyPackage, batch, intent, high)
        synchronized(urgent) { (if (high) urgent else normal).addLast(request) }
        wake.trySend(Unit)
        return START_NOT_STICKY
    }

    private fun runRequest(r: Request) {
        val app = applicationContext
        var ok = 0
        var failed = 0
        var total = 0
        try {
            val params = r.params
            if (!Environment.isExternalStorageManager()) {
                RenderReceiver.done(app, r.replyAction, r.replyPackage, r.requestId, "ERROR:no-storage-access")
                return
            }
            val items = JSONObject(File(r.batchPath).readText()).getJSONArray("items")
            total = items.length()
            when (params.getStringExtra(RenderReceiver.EXTRA_LANG)?.lowercase()) {
                "en" -> { renderEnglish(r, items, total).let { (o, f) -> ok = o; failed = f }; return }
                "cs" -> { renderCzech(r, items, total).let { (o, f) -> ok = o; failed = f }; return }
            }
            val catalog = VoiceCatalog.get(app)
            val speaker = params.getStringExtra(RenderReceiver.EXTRA_SPEAKER)
            // speaker=tts: exactly what the system TTS engine would speak — the voice and every
            // knob set in 音声, and the caller's Android speech rate when 「follow」 is on.
            val asTts = speaker.equals(SPEAKER_TTS, true)
            val stored = VoiceSettingsStore.current(app)
            val style = if (asTts) OnseEngine.resolveStyle(app, stored.styleId)
            else speaker?.toIntOrNull() ?: VoiceSettings.PREFERRED_STYLE
            val model = catalog.modelFor(style)
            if (model == null || catalog.choiceFor(style) == null) {
                RenderReceiver.done(app, r.replyAction, r.replyPackage, r.requestId, "ERROR:unknown speaker: $style")
                return
            }
            if (!VoiceStore.isInstalled(app, model)) {
                RenderReceiver.done(app, r.replyAction, r.replyPackage, r.requestId, "ERROR:voice not installed: $style (${model.file})")
                return
            }
            val wantWav = params.getStringExtra(RenderReceiver.EXTRA_FORMAT).equals("wav", true)
            if (!wantWav && !OggOpusWriter.isAvailable()) {
                RenderReceiver.done(app, r.replyAction, r.replyPackage, r.requestId, "ERROR:no-opus-encoder (ask for format=wav)")
                return
            }
            val settings = settingsFrom(params, if (asTts) stored.copy(styleId = style) else VoiceSettings(styleId = style))
            val rateFactor = if (asTts && stored.followSystemRate) {
                params.getStringExtra(RenderReceiver.EXTRA_RATE)?.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
            } else 1f
            val bitrate = params.getStringExtra(RenderReceiver.EXTRA_BITRATE)?.toIntOrNull() ?: DEFAULT_BITRATE_KBPS
            for (i in 0 until total) {
                yieldToUrgent(r)
                if (cancelledRequests.remove(r.requestId) != null) {
                    RenderReceiver.done(app, r.replyAction, r.replyPackage, r.requestId, "ERROR:cancelled|$ok|$failed|$total")
                    return
                }
                val item = items.getJSONObject(i)
                val id = item.optString("id")
                val outPath = item.optString("out_path")
                updateNotification("音声 ${i + 1}/$total")
                val t0 = System.currentTimeMillis()
                try {
                    val text = item.getString("text")
                    require(outPath.startsWith("/")) { "out_path must be absolute" }
                    val (wav, kana) = OnseEngine.synthesizeWavKana(app, text, style, settings, rateFactor)
                    val pcm = OnseEngine.pcmFromWav(wav)
                    if (wantWav) OggOpusWriter.writeWav(wav, File(outPath))
                    else OggOpusWriter.write(pcm, OnseEngine.SAMPLE_RATE, bitrate, File(outPath))
                    val durationMs = pcm.size / 2L * 1000 / OnseEngine.SAMPLE_RATE
                    ok++
                    RenderReceiver.item(app, r.replyAction, r.replyPackage, r.requestId, id, true, outPath, durationMs, null, i + 1, total, kana, System.currentTimeMillis() - t0)
                } catch (e: Exception) {
                    failed++
                    Log.w(TAG, "item $id failed", e)
                    RenderReceiver.item(app, r.replyAction, r.replyPackage, r.requestId, id, false, outPath, 0, e.message ?: e.javaClass.simpleName, i + 1, total)
                }
            }
            RenderReceiver.done(app, r.replyAction, r.replyPackage, r.requestId, "OK:$ok|$failed|$total")
        } catch (e: Exception) {
            RenderReceiver.done(app, r.replyAction, r.replyPackage, r.requestId, "ERROR:${e.message ?: e.javaClass.simpleName}|$ok|$failed|$total")
        } finally {
            if (pending.decrementAndGet() <= 0) {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /**
     * `lang=en`: Kokoro English. `en_voice` (default the app's English voice, am_michael) and
     * `speed` (default the app's English speed). Same files, replies and `done` as Japanese.
     * Returns (ok, failed).
     */
    private fun renderEnglish(r: Request, items: org.json.JSONArray, total: Int): Pair<Int, Int> {
        val app = applicationContext
        val settings = shiroikuma.onse.voice.VoiceSettingsStore.current(app)
        val voice = r.params.getStringExtra(RenderReceiver.EXTRA_EN_VOICE)?.takeIf { it.isNotBlank() } ?: settings.enVoice
        return renderPcm(
            r, items, total, "English",
            refusal = when {
                !shiroikuma.onse.english.EnglishStore.isInstalled(app) -> "english model not installed"
                shiroikuma.onse.english.EnglishCatalog.get(app).voice(voice) == null -> "unknown english voice: $voice"
                else -> null
            },
            speed = r.params.getStringExtra(RenderReceiver.EXTRA_SPEED)?.toFloatOrNull() ?: (settings.enSpeedPct / 100f),
            rate = shiroikuma.onse.english.EnglishEngine.SAMPLE_RATE,
        ) { text, speed -> shiroikuma.onse.english.EnglishEngine.synthesizePcm(app, text, voice, speed) }
    }

    /**
     * `lang=cs`: Piper Czech. `cs_voice` (default the app's Czech voice, jirka) and `speed`
     * (default the app's Czech speed). Same files, replies and `done` as Japanese.
     * Returns (ok, failed).
     */
    private fun renderCzech(r: Request, items: org.json.JSONArray, total: Int): Pair<Int, Int> {
        val app = applicationContext
        val settings = shiroikuma.onse.voice.VoiceSettingsStore.current(app)
        val voice = r.params.getStringExtra(RenderReceiver.EXTRA_CS_VOICE)?.takeIf { it.isNotBlank() } ?: settings.csVoice
        return renderPcm(
            r, items, total, "Čeština",
            refusal = when {
                shiroikuma.onse.czech.CzechCatalog.get(app).voice(voice) == null -> "unknown czech voice: $voice"
                !shiroikuma.onse.czech.CzechStore.isInstalled(app, voice) -> "czech model not installed"
                else -> null
            },
            speed = r.params.getStringExtra(RenderReceiver.EXTRA_SPEED)?.toFloatOrNull() ?: (settings.csSpeedPct / 100f),
            rate = shiroikuma.onse.czech.CzechEngine.SAMPLE_RATE,
        ) { text, speed -> shiroikuma.onse.czech.CzechEngine.synthesizePcm(app, text, voice, speed) }
    }

    /**
     * The batch loop of the sherpa-onnx languages: [refusal] (if any) answers the whole request,
     * otherwise each item is synthesised to 16-bit mono PCM at [rate] and written as OGG/Opus (or
     * WAV). Returns (ok, failed).
     */
    private fun renderPcm(
        r: Request,
        items: org.json.JSONArray,
        total: Int,
        label: String,
        refusal: String?,
        speed: Float,
        rate: Int,
        synth: (String, Float) -> ByteArray,
    ): Pair<Int, Int> {
        val app = applicationContext
        val params = r.params
        if (refusal != null) {
            RenderReceiver.done(app, r.replyAction, r.replyPackage, r.requestId, "ERROR:$refusal")
            return 0 to 0
        }
        val wantWav = params.getStringExtra(RenderReceiver.EXTRA_FORMAT).equals("wav", true)
        if (!wantWav && !OggOpusWriter.isAvailable()) {
            RenderReceiver.done(app, r.replyAction, r.replyPackage, r.requestId, "ERROR:no-opus-encoder (ask for format=wav)")
            return 0 to 0
        }
        val bitrate = params.getStringExtra(RenderReceiver.EXTRA_BITRATE)?.toIntOrNull() ?: DEFAULT_BITRATE_KBPS
        var ok = 0
        var failed = 0
        for (i in 0 until total) {
            yieldToUrgent(r)
            if (cancelledRequests.remove(r.requestId) != null) {
                RenderReceiver.done(app, r.replyAction, r.replyPackage, r.requestId, "ERROR:cancelled|$ok|$failed|$total")
                return ok to failed
            }
            val item = items.getJSONObject(i)
            val id = item.optString("id")
            val outPath = item.optString("out_path")
            updateNotification("$label ${i + 1}/$total")
            val t0 = System.currentTimeMillis()
            try {
                require(outPath.startsWith("/")) { "out_path must be absolute" }
                val pcm = synth(item.getString("text"), speed)
                if (wantWav) OggOpusWriter.writeWav(shiroikuma.onse.english.EnglishEngine.wav(pcm, rate), File(outPath))
                else OggOpusWriter.write(pcm, rate, bitrate, File(outPath))
                ok++
                RenderReceiver.item(app, r.replyAction, r.replyPackage, r.requestId, id, true, outPath, pcm.size / 2L * 1000 / rate, null, i + 1, total, renderMs = System.currentTimeMillis() - t0)
            } catch (e: Exception) {
                failed++
                Log.w(TAG, "$label item $id failed", e)
                RenderReceiver.item(app, r.replyAction, r.replyPackage, r.requestId, id, false, outPath, 0, e.message ?: e.javaClass.simpleName, i + 1, total)
            }
        }
        RenderReceiver.done(app, r.replyAction, r.replyPackage, r.requestId, "OK:$ok|$failed|$total")
        return ok to failed
    }

    /**
     * Request parameters over [base] — the contract's defaults, or with `speaker=tts` 音声's own
     * settings; scales as decimals (`1.15`).
     */
    private fun settingsFrom(i: Intent, base: VoiceSettings): VoiceSettings {
        fun pct(key: String, def: Int) = i.getStringExtra(key)?.toDoubleOrNull()?.let { (it * 100).toInt() } ?: def
        fun ms(key: String, def: Int) = i.getStringExtra(key)?.toDoubleOrNull()?.let { (it * 1000).toInt() } ?: def
        return base.copy(
            speedPct = pct(RenderReceiver.EXTRA_SPEED, base.speedPct),
            pitchPct = pct(RenderReceiver.EXTRA_PITCH, base.pitchPct),
            intonationPct = pct(RenderReceiver.EXTRA_INTONATION, base.intonationPct),
            volumePct = pct(RenderReceiver.EXTRA_VOLUME, base.volumePct),
            prePauseMs = ms(RenderReceiver.EXTRA_GAP_PRE, base.prePauseMs),
            postPauseMs = ms(RenderReceiver.EXTRA_GAP_POST, base.postPauseMs),
            followSystemRate = false,
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shiroikuma-onse:render")
            ?.apply { setReferenceCounted(false); acquire(60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun notification(text: String): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "音声の作成", NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(text)) }
    }

    override fun onDestroy() {
        releaseWakeLock()
        wake.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OnseRender"
        private const val CHANNEL = "onse_render"
        private const val NOTIFICATION_ID = 92_003
        const val DEFAULT_BITRATE_KBPS = 48
        const val SPEAKER_TTS = "tts"

        private val cancelledRequests = ConcurrentHashMap<String, Boolean>()

        /** Stop [requestId] at its next item boundary (the finished items stay). Silent otherwise. */
        fun requestCancel(requestId: String?) {
            requestId?.let { cancelledRequests[it] = true }
        }
    }
}
