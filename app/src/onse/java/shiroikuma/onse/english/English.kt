package shiroikuma.onse.english

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * English speech for 白い熊 音声: Kokoro-82M (Apache-2.0) through sherpa-onnx, the same model
 * release `shiroikuma/voices/gen-english.py` built the bundled catalogue and samples from
 * (`assets/voices/english.json`, `assets/voices/english/<voice>.ogg`).
 *
 * Default voice **am_michael** (白い熊, 2026-10-01).
 */
data class EnglishVoice(val sid: Int, val name: String, val accent: String, val female: Boolean, val sample: String) {
    val label: String get() = "$name (${accent} ${if (female) "female" else "male"})"
}

class EnglishCatalog private constructor(
    val model: String,
    val url: String,
    val size: Long,
    val sha256: String,
    val keep: List<String>,
    val defaultVoice: String,
    val sampleText: String,
    val voices: List<EnglishVoice>,
) {
    fun voice(name: String?): EnglishVoice? = voices.firstOrNull { it.name == name }

    companion object {
        @Volatile private var cached: EnglishCatalog? = null

        fun get(context: Context): EnglishCatalog = cached ?: synchronized(this) {
            cached ?: run {
                val o = JSONObject(context.assets.open("voices/english.json").bufferedReader().use { it.readText() })
                val keep = o.getJSONArray("keep").let { a -> (0 until a.length()).map(a::getString) }
                val voices = o.getJSONArray("voices").let { a ->
                    (0 until a.length()).map { i ->
                        val v = a.getJSONObject(i)
                        EnglishVoice(v.getInt("sid"), v.getString("name"), v.getString("accent"), v.getBoolean("female"), v.getString("sample"))
                    }
                }
                EnglishCatalog(
                    o.getString("model"), o.getString("url"), o.getLong("size"), o.getString("sha256"),
                    keep, o.getString("defaultVoice"), o.getString("sampleText"), voices,
                ).also { cached = it }
            }
        }
    }
}

/**
 * The Kokoro model on the phone: downloaded on request from the sherpa-onnx release (≈132 MB
 * `.tar.bz2`), verified against the catalogue's SHA-256, unpacked keeping only the English parts
 * into `filesDir/voices/<model>.part/`, then renamed to `filesDir/voices/<model>/` — so a
 * half-installed model is never loaded.
 */
object EnglishStore {
    sealed interface State {
        data object NotInstalled : State
        data class Downloading(val done: Long, val total: Long) : State
        data object Unpacking : State
        data object Installed : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<State>(State.NotInstalled)
    val state: StateFlow<State> = _state.asStateFlow()

    fun dir(context: Context): File = File(File(context.filesDir, "voices"), EnglishCatalog.get(context).model)

    fun isInstalled(context: Context): Boolean = File(dir(context), "model.int8.onnx").isFile && File(dir(context), "voices.bin").isFile

    fun refresh(context: Context) {
        if (_state.value is State.Downloading || _state.value is State.Unpacking) return
        _state.value = if (isInstalled(context)) State.Installed else State.NotInstalled
    }

    fun download(context: Context) {
        if (job?.isActive == true || isInstalled(context)) return
        val app = context.applicationContext
        val cat = EnglishCatalog.get(app)
        job = scope.launch {
            val voicesDir = File(app.filesDir, "voices").apply { mkdirs() }
            val archive = File(voicesDir, "${cat.model}.tar.bz2.part")
            val staging = File(voicesDir, "${cat.model}.part")
            try {
                _state.value = State.Downloading(0, cat.size)
                val conn = (URL(cat.url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true; connectTimeout = 20_000; readTimeout = 60_000
                }
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                val md = MessageDigest.getInstance("SHA-256")
                var done = 0L
                var last = 0L
                conn.inputStream.use { input ->
                    archive.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            if (!isActive) throw IOException("cancelled")
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); md.update(buf, 0, n); done += n
                            if (done - last > 1_000_000) { last = done; _state.value = State.Downloading(done, cat.size) }
                        }
                    }
                }
                if (md.digest().joinToString("") { "%02x".format(it) } != cat.sha256) throw IOException("checksum mismatch")
                _state.value = State.Unpacking
                staging.deleteRecursively()
                staging.mkdirs()
                TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(archive.inputStream(), 1 shl 20))).use { tar ->
                    val root = staging.canonicalFile
                    while (true) {
                        val e = tar.nextEntry ?: break
                        // Entries are "<model>/<path>"; keep only the English parts.
                        val rel = e.name.substringAfter('/', "")
                        if (rel.isEmpty() || cat.keep.none { k -> if (k.endsWith("/")) rel.startsWith(k) else rel == k }) continue
                        val target = File(staging, rel).canonicalFile
                        if (!target.path.startsWith(root.path + File.separator)) throw IOException("bad entry ${e.name}")
                        if (e.isDirectory) target.mkdirs() else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { tar.copyTo(it) }
                        }
                    }
                }
                val final = dir(app)
                final.deleteRecursively()
                if (!staging.renameTo(final)) throw IOException("could not install ${cat.model}")
                _state.value = State.Installed
            } catch (e: Exception) {
                staging.deleteRecursively()
                _state.value = if (e.message == "cancelled") State.NotInstalled else State.Failed(e.message ?: "download failed")
            } finally {
                archive.delete()
            }
        }
    }

    fun cancel() { job?.cancel() }

    fun delete(context: Context) {
        cancel()
        EnglishEngine.release()
        dir(context).deleteRecursively()
        _state.value = State.NotInstalled
    }
}

/**
 * One Kokoro engine per process, built for the lexicon of the voice's accent (US / UK — reloaded
 * only when the accent changes; the model is ~110 MB). Synchronized: sherpa-onnx's OfflineTts is
 * driven from one thread at a time here.
 */
object EnglishEngine {
    const val SAMPLE_RATE = 24000
    private var tts: OfflineTts? = null
    private var lexicon: String? = null

    @Synchronized
    private fun engineFor(context: Context, voice: EnglishVoice): OfflineTts {
        val lex = if (voice.accent == "UK") "lexicon-gb-en.txt" else "lexicon-us-en.txt"
        tts?.takeIf { lexicon == lex }?.let { return it }
        release()
        val d = EnglishStore.dir(context)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = File(d, "model.int8.onnx").path,
                    voices = File(d, "voices.bin").path,
                    tokens = File(d, "tokens.txt").path,
                    dataDir = File(d, "espeak-ng-data").path,
                    lexicon = File(d, lex).path,
                ),
                numThreads = 4,
            ),
        )
        val t0 = System.currentTimeMillis()
        return OfflineTts(null, config).also {
            tts = it
            lexicon = lex
            Log.i("EnglishEngine", "Kokoro ready ($lex) in ${System.currentTimeMillis() - t0} ms")
        }
    }

    @Synchronized
    fun release() {
        runCatching { tts?.release() }
        tts = null
        lexicon = null
    }

    /** 16-bit mono PCM at [SAMPLE_RATE] Hz. */
    @Synchronized
    fun synthesizePcm(context: Context, text: String, voiceName: String, speed: Float): ByteArray {
        if (!EnglishStore.isInstalled(context)) throw IOException("english model not installed")
        val catalog = EnglishCatalog.get(context)
        val voice = catalog.voice(voiceName) ?: throw IOException("unknown english voice: $voiceName")
        val audio = engineFor(context, voice).generate(text, voice.sid, speed.coerceIn(0.5f, 2f))
        val samples = audio.samples
        val pcm = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            pcm[2 * i] = (v and 0xFF).toByte()
            pcm[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    /** A RIFF WAV around [pcm] (for `format=wav` renders). */
    fun wav(pcm: ByteArray, rate: Int = SAMPLE_RATE): ByteArray {
        val out = java.io.ByteArrayOutputStream(44 + pcm.size)
        fun i32(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
        fun i16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        out.write("RIFF".toByteArray()); i32(36 + pcm.size); out.write("WAVEfmt ".toByteArray())
        i32(16); i16(1); i16(1); i32(rate); i32(rate * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(pcm.size); out.write(pcm)
        return out.toByteArray()
    }
}
