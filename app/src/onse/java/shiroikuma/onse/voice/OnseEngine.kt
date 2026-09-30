package shiroikuma.onse.voice

import android.content.Context
import android.util.Log
import dev.ztssst.voicevox_tts.R
import dev.ztssst.voicevox_tts.unzipSafely
import jp.hiroshiba.voicevoxcore.blocking.Onnxruntime
import jp.hiroshiba.voicevoxcore.blocking.OpenJtalk
import jp.hiroshiba.voicevoxcore.blocking.Synthesizer
import jp.hiroshiba.voicevoxcore.blocking.VoiceModelFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * One VOICEVOX synthesizer per process, shared by the system TTS service and the app's own
 * screens. Models are loaded on demand — the one holding the requested style — and at most
 * [MAX_LOADED] stay resident (least recently used is unloaded), since each costs well over a
 * hundred megabytes once loaded.
 *
 * Every entry point is synchronized: the core's blocking API is not meant to be driven from two
 * threads at once, and the TTS framework and the UI can both call in.
 */
object OnseEngine {
    private const val TAG = "OnseEngine"
    private const val MAX_LOADED = 2
    const val SAMPLE_RATE = 24000

    private var synthesizer: Synthesizer? = null
    private val loaded = LinkedHashMap<String, UUID>() // model file → voice model id, LRU order

    @Synchronized
    private fun synth(context: Context): Synthesizer {
        synthesizer?.let { return it }
        val t0 = System.currentTimeMillis()
        val ort = Onnxruntime.loadOnce().perform()
        val jtalk = OpenJtalk(dictionary(context).absolutePath)
        return Synthesizer.builder(ort, jtalk).build().also {
            synthesizer = it
            Log.i(TAG, "synthesizer ready in ${System.currentTimeMillis() - t0} ms")
        }
    }

    /** OpenJTalk's dictionary, unpacked from the APK once per app update. */
    private fun dictionary(context: Context): File {
        val dir = File(context.filesDir, "open_jtalk_dict")
        val stamp = File(context.filesDir, "open_jtalk_dict.stamp")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (!dir.isDirectory || !stamp.isFile || stamp.readText() != installed) {
            dir.deleteRecursively()
            context.resources.openRawResource(R.raw.open_jtalk_dict).use { unzipSafely(it, context.filesDir) }
            stamp.writeText(installed)
        }
        return dir
    }

    @Synchronized
    private fun ensureLoaded(context: Context, model: VoiceModel) {
        val s = synth(context)
        loaded.remove(model.file)?.let { id ->
            if (s.isLoadedVoiceModel(id)) {
                loaded[model.file] = id // move to most-recent
                return
            }
        }
        while (loaded.size >= MAX_LOADED) {
            val (file, id) = loaded.entries.first()
            loaded.remove(file)
            runCatching { s.unloadVoiceModel(id) }
            Log.i(TAG, "unloaded $file")
        }
        val path = VoiceStore.pathFor(context, model)
        VoiceModelFile(path.absolutePath).use { vm ->
            s.loadVoiceModel(vm).perform()
            loaded[model.file] = vm.id
        }
        Log.i(TAG, "loaded ${model.file}")
    }

    @Synchronized
    fun unload(model: VoiceModel) {
        val id = loaded.remove(model.file) ?: return
        runCatching { synthesizer?.unloadVoiceModel(id) }
    }

    /**
     * The style actually used for [requested]: itself when its model is installed, otherwise the
     * preferred No.7 voice, which is always bundled.
     */
    fun resolveStyle(context: Context, requested: Int): Int {
        val catalog = VoiceCatalog.get(context)
        val model = catalog.modelFor(requested)
        val ok = model != null && catalog.choiceFor(requested) != null && VoiceStore.isInstalled(context, model)
        return if (ok) requested else VoiceSettings.PREFERRED_STYLE
    }

    /**
     * Synthesize [text] as 16-bit mono PCM at [SAMPLE_RATE] Hz with the given settings.
     * [rateFactor] multiplies the speed (the Android TTS speech rate, 1.0 = normal).
     */
    @Synchronized
    fun synthesizePcm(
        context: Context,
        text: String,
        styleId: Int,
        settings: VoiceSettings,
        rateFactor: Float = 1f,
    ): ByteArray = pcmFromWav(synthesizeWav(context, text, styleId, settings, rateFactor))

    @Synchronized
    fun synthesizeWav(
        context: Context,
        text: String,
        styleId: Int,
        settings: VoiceSettings,
        rateFactor: Float = 1f,
    ): ByteArray {
        val style = resolveStyle(context, styleId)
        val model = VoiceCatalog.get(context).modelFor(style) ?: error("no model for style $style")
        ensureLoaded(context, model)
        val s = synth(context)
        val t0 = System.currentTimeMillis()
        val q = s.createAudioQuery(text, style)
        q.speedScale = (settings.speedPct / 100.0 * rateFactor).coerceIn(0.5, 2.0)
        q.pitchScale = settings.pitchPct / 100.0
        q.intonationScale = settings.intonationPct / 100.0
        q.volumeScale = settings.volumePct / 100.0
        q.prePhonemeLength = settings.prePauseMs / 1000.0
        q.postPhonemeLength = settings.postPauseMs / 1000.0
        val wav = s.synthesis(q, style).perform()
        Log.d(TAG, "style $style: ${text.length} chars in ${System.currentTimeMillis() - t0} ms")
        return wav
    }

    /** The PCM payload of a WAV (skips the RIFF header chunks). */
    fun pcmFromWav(wav: ByteArray): ByteArray {
        val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(12)
        while (buf.remaining() >= 8) {
            val id = String(wav, buf.position(), 4, Charsets.US_ASCII)
            buf.position(buf.position() + 4)
            val size = buf.int
            if (id == "data") return wav.copyOfRange(buf.position(), buf.position() + size)
            buf.position(buf.position() + size + (size and 1))
        }
        throw IllegalArgumentException("data chunk not found in WAV")
    }
}
