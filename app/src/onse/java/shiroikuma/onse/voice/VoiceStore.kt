package shiroikuma.onse.voice

import android.content.Context
import dev.ztssst.voicevox_tts.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The installed voice models. The No.7 model (`6.vvm`) ships inside the APK as `res/raw/model`;
 * every other model is downloaded on request from the official voicevox_vvm release into
 * `filesDir/voices/`, verified against the catalogue's SHA-256, and only then renamed into place
 * (`<file>.part` → `<file>`), so a half-downloaded model is never loaded.
 */
object VoiceStore {

    sealed interface State {
        data object NotInstalled : State
        data class Downloading(val done: Long, val total: Long) : State
        data object Installed : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    fun dir(context: Context): File = File(context.filesDir, "voices").apply { mkdirs() }

    fun file(context: Context, model: VoiceModel): File = File(dir(context), model.file)

    fun isInstalled(context: Context, model: VoiceModel): Boolean =
        model.bundled || file(context, model).let { it.isFile && it.length() == model.size }

    /** Refresh the state map from disk (called when the screen opens). */
    fun refresh(context: Context) {
        val catalog = VoiceCatalog.get(context)
        // Voices a restore asked for: download what is still missing, forget what has arrived.
        val pending = shiroikuma.onse.ui.Backup.pendingVoices(context)
        if (pending.isNotEmpty()) {
            val stillMissing = catalog.models.filter { it.file in pending && !isInstalled(context, it) }
            shiroikuma.onse.ui.Backup.setPendingVoices(context, stillMissing.map { it.file }.toSet())
            stillMissing.forEach { download(context, it) }
        }
        _states.value = catalog.models.associate { m ->
            m.file to (_states.value[m.file]?.takeIf { it is State.Downloading }
                ?: if (isInstalled(context, m)) State.Installed else State.NotInstalled)
        }
    }

    /**
     * The on-disk path of [model] for the engine. The bundled model is copied out of the APK once
     * per app update (the core loads a file path, not a stream).
     */
    fun pathFor(context: Context, model: VoiceModel): File {
        if (!model.bundled) return file(context, model)
        val target = file(context, model)
        val stamp = File(dir(context), "${model.file}.stamp")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (!target.isFile || !stamp.isFile || stamp.readText() != installed) {
            val part = File(target.path + ".part")
            context.resources.openRawResource(R.raw.model).use { input -> part.outputStream().use { input.copyTo(it) } }
            if (!part.renameTo(target)) throw IOException("could not install ${model.file}")
            stamp.writeText(installed)
        }
        return target
    }

    fun download(context: Context, model: VoiceModel) {
        if (model.bundled || jobs[model.file]?.isActive == true) return
        val app = context.applicationContext
        jobs[model.file] = scope.launch {
            val target = file(app, model)
            val part = File(target.path + ".part")
            setState(model, State.Downloading(0, model.size))
            try {
                val conn = (URL(model.url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20_000
                    readTimeout = 60_000
                }
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                var lastReport = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            if (!isActive) throw IOException("cancelled")
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            if (done - lastReport > 512 * 1024) {
                                lastReport = done
                                setState(model, State.Downloading(done, model.size))
                            }
                        }
                    }
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                if (hex != model.sha256) throw IOException("checksum mismatch")
                if (!part.renameTo(target)) throw IOException("could not install ${model.file}")
                setState(model, State.Installed)
            } catch (e: Exception) {
                part.delete()
                setState(model, if (e.message == "cancelled") State.NotInstalled else State.Failed(e.message ?: "download failed"))
            }
        }
    }

    fun cancel(model: VoiceModel) {
        jobs.remove(model.file)?.cancel()
    }

    /** Remove a downloaded model (never the bundled one). */
    fun delete(context: Context, model: VoiceModel) {
        if (model.bundled) return
        cancel(model)
        OnseEngine.unload(model)
        file(context, model).delete()
        setState(model, State.NotInstalled)
    }

    private fun setState(model: VoiceModel, state: State) {
        _states.value = _states.value + (model.file to state)
    }
}
