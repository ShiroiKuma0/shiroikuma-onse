package shiroikuma.onse.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How 白い熊 音声 speaks: the selected style and the VOICEVOX audio-query knobs.
 *
 * The defaults are the 白い熊 voice of `shiroikuma-jisho-subtitles`: **No.7 / 読み聞かせ
 * (style 31)** at speedScale **1.15**, pitch 0, intonation 1.0. Scales are stored in hundredths
 * (115 = 1.15) so every one is a slider; pauses in milliseconds.
 */
data class VoiceSettings(
    val styleId: Int = PREFERRED_STYLE,
    val speedPct: Int = 115,
    /** pitchScale × 100: VOICEVOX's range is −0.15…+0.15. */
    val pitchPct: Int = 0,
    val intonationPct: Int = 100,
    val volumePct: Int = 100,
    val prePauseMs: Int = 100,
    val postPauseMs: Int = 100,
    /** Follow the calling app's speech-rate setting (Android TTS) on top of [speedPct]. */
    val followSystemRate: Boolean = true,
) {
    companion object {
        /** No.7 / 読み聞かせ — the 白い熊 voice. */
        const val PREFERRED_STYLE = 31
        const val PREFERRED_LABEL = "No.7（読み聞かせ）"
    }
}

object VoiceSettingsStore {
    const val PREFS_FILE = "onse_voice"

    private lateinit var appContext: Context
    private val _settings = MutableStateFlow(VoiceSettings())
    val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _settings.value = load()
    }

    /** Settings for a process that may not have run [init] (the TTS service). */
    fun current(context: Context): VoiceSettings {
        init(context)
        return _settings.value
    }

    fun update(transform: (VoiceSettings) -> VoiceSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        save(next)
    }

    fun reload() {
        if (::appContext.isInitialized) _settings.value = load()
    }

    private fun sp() = appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun load(): VoiceSettings {
        val p = sp()
        val d = VoiceSettings()
        return VoiceSettings(
            styleId = p.getInt("styleId", d.styleId),
            speedPct = p.getInt("speedPct", d.speedPct),
            pitchPct = p.getInt("pitchPct", d.pitchPct),
            intonationPct = p.getInt("intonationPct", d.intonationPct),
            volumePct = p.getInt("volumePct", d.volumePct),
            prePauseMs = p.getInt("prePauseMs", d.prePauseMs),
            postPauseMs = p.getInt("postPauseMs", d.postPauseMs),
            followSystemRate = p.getBoolean("followSystemRate", d.followSystemRate),
        )
    }

    private fun save(v: VoiceSettings) {
        sp().edit()
            .putInt("styleId", v.styleId)
            .putInt("speedPct", v.speedPct)
            .putInt("pitchPct", v.pitchPct)
            .putInt("intonationPct", v.intonationPct)
            .putInt("volumePct", v.volumePct)
            .putInt("prePauseMs", v.prePauseMs)
            .putInt("postPauseMs", v.postPauseMs)
            .putBoolean("followSystemRate", v.followSystemRate)
            .commit() // the TTS service reads this file from its own process start
    }
}
