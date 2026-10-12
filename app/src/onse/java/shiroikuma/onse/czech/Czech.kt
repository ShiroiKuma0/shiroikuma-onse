package shiroikuma.onse.czech

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
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
 * Czech speech for 白い熊 音声: Piper cs_CZ voices (VITS) through sherpa-onnx — the same model
 * releases `shiroikuma/voices/gen-czech.py` built the bundled catalogue and samples from
 * (`assets/voices/czech.json`, `assets/voices/czech/<voice>.ogg`). Asked for by 言語島, which
 * reads the Czech source side of cs→ja sentences (2026-10-11).
 *
 * Each voice is its own model, downloaded on its own: **jirka** (male, k2-fsa's package) and
 * **kasandra** (female, converted by us and hosted on our GitHub release `voices`, 2026-10-12).
 * Piper renders at 22.05 kHz; output is resampled to 24 kHz so it goes through the same Opus path
 * as Japanese and English.
 */
data class CzechVoice(
    val sid: Int,
    val name: String,
    val female: Boolean,
    val license: String,
    val sample: String,
    val model: String,
    val onnx: String,
    val url: String,
    val size: Long,
    val sha256: String,
) {
    val label: String get() = "$name (${if (female) "female" else "male"})"
}

class CzechCatalog private constructor(
    val keep: List<String>,
    val defaultVoice: String,
    val sampleText: String,
    val voices: List<CzechVoice>,
) {
    fun voice(name: String?): CzechVoice? = voices.firstOrNull { it.name == name }

    companion object {
        @Volatile private var cached: CzechCatalog? = null

        fun get(context: Context): CzechCatalog = cached ?: synchronized(this) {
            cached ?: run {
                val o = JSONObject(context.assets.open("voices/czech.json").bufferedReader().use { it.readText() })
                val keep = o.getJSONArray("keep").let { a -> (0 until a.length()).map(a::getString) }
                val voices = o.getJSONArray("voices").let { a ->
                    (0 until a.length()).map { i ->
                        val v = a.getJSONObject(i)
                        CzechVoice(
                            0, v.getString("name"), v.getBoolean("female"), v.getString("license"), v.getString("sample"),
                            v.getString("model"), v.getString("onnx"), v.getString("url"), v.getLong("size"), v.getString("sha256"),
                        )
                    }
                }
                CzechCatalog(keep, o.getString("defaultVoice"), o.getString("sampleText"), voices).also { cached = it }
            }
        }
    }
}

/**
 * The Piper models on the phone, one per voice: downloaded on request (≈67 MB `.tar.bz2` each),
 * verified against the catalogue's SHA-256, unpacked into `filesDir/voices/<model>.part/`, then
 * renamed to `filesDir/voices/<model>/` — so a half-installed model is never loaded.
 */
object CzechStore {
    sealed interface State {
        data object NotInstalled : State
        data class Downloading(val done: Long, val total: Long) : State
        data object Unpacking : State
        data object Installed : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    /** Per voice name. A voice missing from the map is [State.NotInstalled]. */
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    private fun set(voice: String, state: State) { _states.value = _states.value + (voice to state) }

    fun dir(context: Context, voice: CzechVoice): File = File(File(context.filesDir, "voices"), voice.model)

    fun isInstalled(context: Context, voice: CzechVoice): Boolean =
        File(dir(context, voice), voice.onnx).isFile && File(dir(context, voice), "tokens.txt").isFile

    fun isInstalled(context: Context, voiceName: String?): Boolean =
        CzechCatalog.get(context).voice(voiceName)?.let { isInstalled(context, it) } == true

    fun installedVoices(context: Context): List<CzechVoice> = CzechCatalog.get(context).voices.filter { isInstalled(context, it) }

    fun refresh(context: Context) {
        _states.value = CzechCatalog.get(context).voices.associate { v ->
            v.name to (_states.value[v.name]?.takeIf { it is State.Downloading || it is State.Unpacking }
                ?: if (isInstalled(context, v)) State.Installed else State.NotInstalled)
        }
    }

    fun download(context: Context, voice: CzechVoice) {
        if (jobs[voice.name]?.isActive == true || isInstalled(context, voice)) return
        val app = context.applicationContext
        val keep = CzechCatalog.get(app).keep + voice.onnx
        jobs[voice.name] = scope.launch {
            val voicesDir = File(app.filesDir, "voices").apply { mkdirs() }
            val archive = File(voicesDir, "${voice.model}.tar.bz2.part")
            val staging = File(voicesDir, "${voice.model}.part")
            try {
                set(voice.name, State.Downloading(0, voice.size))
                val conn = (URL(voice.url).openConnection() as HttpURLConnection).apply {
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
                            if (done - last > 1_000_000) { last = done; set(voice.name, State.Downloading(done, voice.size)) }
                        }
                    }
                }
                if (md.digest().joinToString("") { "%02x".format(it) } != voice.sha256) throw IOException("checksum mismatch")
                set(voice.name, State.Unpacking)
                staging.deleteRecursively()
                staging.mkdirs()
                TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(archive.inputStream(), 1 shl 20))).use { tar ->
                    val root = staging.canonicalFile
                    while (true) {
                        val e = tar.nextEntry ?: break
                        // Entries are "<model>/<path>"; keep only what the engine reads.
                        val rel = e.name.substringAfter('/', "")
                        if (rel.isEmpty() || keep.none { k -> if (k.endsWith("/")) rel.startsWith(k) else rel == k }) continue
                        val target = File(staging, rel).canonicalFile
                        if (!target.path.startsWith(root.path + File.separator)) throw IOException("bad entry ${e.name}")
                        if (e.isDirectory) target.mkdirs() else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { tar.copyTo(it) }
                        }
                    }
                }
                val final = dir(app, voice)
                final.deleteRecursively()
                if (!staging.renameTo(final)) throw IOException("could not install ${voice.model}")
                set(voice.name, State.Installed)
            } catch (e: Exception) {
                staging.deleteRecursively()
                set(voice.name, if (e.message == "cancelled") State.NotInstalled else State.Failed(e.message ?: "download failed"))
            } finally {
                archive.delete()
            }
        }
    }

    fun cancel(voice: CzechVoice) { jobs[voice.name]?.cancel() }

    fun delete(context: Context, voice: CzechVoice) {
        cancel(voice)
        CzechEngine.release()
        dir(context, voice).deleteRecursively()
        set(voice.name, State.NotInstalled)
    }
}

/**
 * One Piper engine per process, for the last voice used (each model is ~61 MB). Synchronized: sherpa-onnx's OfflineTts is
 * driven from one thread at a time here.
 */
object CzechEngine {
    /** Output rate — Piper's 22.05 kHz is resampled to this, one of Opus's native rates. */
    const val SAMPLE_RATE = 24000
    private var tts: OfflineTts? = null
    private var loaded: String? = null

    @Synchronized
    private fun engine(context: Context, voice: CzechVoice): OfflineTts {
        tts?.takeIf { loaded == voice.model }?.let { return it }
        release()
        val d = CzechStore.dir(context, voice)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(d, voice.onnx).path,
                    tokens = File(d, "tokens.txt").path,
                    dataDir = File(d, "espeak-ng-data").path,
                ),
                numThreads = 4,
            ),
        )
        val t0 = System.currentTimeMillis()
        return OfflineTts(null, config).also {
            tts = it
            loaded = voice.model
            Log.i("CzechEngine", "Piper ${voice.name} ready in ${System.currentTimeMillis() - t0} ms")
        }
    }

    @Synchronized
    fun release() {
        runCatching { tts?.release() }
        tts = null
        loaded = null
    }

    /** 16-bit mono PCM at [SAMPLE_RATE] Hz. */
    @Synchronized
    fun synthesizePcm(context: Context, text: String, voiceName: String, speed: Float): ByteArray {
        val voice = CzechCatalog.get(context).voice(voiceName) ?: throw IOException("unknown czech voice: $voiceName")
        if (!CzechStore.isInstalled(context, voice)) throw IOException("czech model not installed")
        val audio = engine(context, voice).generate(text, voice.sid, speed.coerceIn(0.5f, 2f))
        val samples = resample(audio.samples, audio.sampleRate, SAMPLE_RATE)
        val pcm = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            pcm[2 * i] = (v and 0xFF).toByte()
            pcm[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    /** Linear-interpolation resampling; only ever an upsample here (22 050 → 24 000). */
    private fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || x.isEmpty()) return x
        val n = (x.size.toLong() * to / from).toInt()
        val step = from.toDouble() / to
        return FloatArray(n) { i ->
            val pos = i * step
            val j = pos.toInt()
            val f = (pos - j).toFloat()
            val a = x[j.coerceAtMost(x.size - 1)]
            val b = x[(j + 1).coerceAtMost(x.size - 1)]
            a + (b - a) * f
        }
    }
}
