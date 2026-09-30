package shiroikuma.onse.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * In-app playback: text synthesised by [OnseEngine] with the current settings (the try-it box,
 * the UI page's Listen), and the bundled per-style samples (the voice list). One sound at a time.
 */
object VoicePlayer {

    data class Status(val busy: Boolean = false, val message: String? = null, val playingSample: Int? = null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private var job: Job? = null
    private var track: AudioTrack? = null
    private var media: MediaPlayer? = null

    fun speak(context: Context, text: String, styleId: Int) {
        if (text.isBlank()) return
        stop()
        val app = context.applicationContext
        job = scope.launch {
            _status.value = Status(busy = true, message = "合成中…")
            val t0 = SystemClock.elapsedRealtime()
            try {
                val used = OnseEngine.resolveStyle(app, styleId)
                val pcm = OnseEngine.synthesizePcm(app, text, used, VoiceSettingsStore.current(app))
                val took = SystemClock.elapsedRealtime() - t0
                val seconds = pcm.size / 2.0 / OnseEngine.SAMPLE_RATE
                val note = if (used != styleId) " — voice not installed, used ${VoiceSettings.PREFERRED_LABEL}" else ""
                _status.value = Status(busy = true, message = "合成 ${took} ms · 音声 ${"%.1f".format(seconds)} s$note")
                playPcm(pcm)
                _status.value = _status.value.copy(busy = false)
            } catch (e: Exception) {
                _status.value = Status(message = "エラー: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun playPcm(pcm: ByteArray) {
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(OnseEngine.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size.coerceAtLeast(4096))
            .build()
        track = t
        t.write(pcm, 0, pcm.size)
        t.play()
        val ms = pcm.size / 2L * 1000 / OnseEngine.SAMPLE_RATE
        Thread.sleep(ms + 150)
        runCatching { t.stop(); t.release() }
        if (track === t) track = null
    }

    /** Play a bundled sample (`assets/voices/<path>`). */
    fun playSample(context: Context, styleId: Int, path: String) {
        stop()
        runCatching {
            val fd = context.assets.openFd("voices/$path")
            val mp = MediaPlayer()
            mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            fd.close()
            mp.setOnCompletionListener {
                it.release()
                if (media === it) media = null
                _status.value = _status.value.copy(playingSample = null)
            }
            mp.prepare()
            mp.start()
            media = mp
            _status.value = _status.value.copy(playingSample = styleId)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        track?.let { runCatching { it.stop(); it.release() } }
        track = null
        media?.let { runCatching { it.stop(); it.release() } }
        media = null
        _status.value = _status.value.copy(busy = false, playingSample = null)
    }
}
