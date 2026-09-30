package shiroikuma.onse.voice

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * 白い熊 音声 as Android's system text-to-speech engine — replaces upstream's single-voice
 * service (removed in the flavour manifest). Every installed readable VOICEVOX style is offered
 * as a voice; the default is the one selected in the app (No.7 / 読み聞かせ out of the box).
 */
class OnseTtsService : TextToSpeechService() {

    private fun catalog() = VoiceCatalog.get(this)

    private fun installedChoices(): List<VoiceChoice> =
        catalog().readable.filter { VoiceStore.isInstalled(this, it.model) }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        if (isJapanese(lang)) TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_NOT_SUPPORTED

    override fun onGetLanguage(): Array<String> = arrayOf("jpn", "JPN", "")

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        onIsLanguageAvailable(lang, country, variant)

    override fun onGetVoices(): List<Voice> = installedChoices().map { choice ->
        Voice(
            choice.label,
            Locale.JAPAN,
            Voice.QUALITY_HIGH,
            Voice.LATENCY_HIGH,
            false,
            emptySet(),
        )
    }

    override fun onIsValidVoiceName(voiceName: String?): Int =
        if (installedChoices().any { it.label == voiceName }) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(voiceName: String?): Int = onIsValidVoiceName(voiceName)

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        if (!isJapanese(lang)) return null
        val settings = VoiceSettingsStore.current(this)
        val style = OnseEngine.resolveStyle(this, settings.styleId)
        return catalog().choiceFor(style)?.label
    }

    override fun onStop() {}

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val text = request.charSequenceText?.toString().orEmpty()
        val settings = VoiceSettingsStore.current(this)
        val style = installedChoices().firstOrNull { it.label == request.voiceName }?.style?.id ?: settings.styleId
        val rate = if (settings.followSystemRate) request.speechRate / 100f else 1f
        if (callback.start(OnseEngine.SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
        if (text.isBlank()) {
            callback.done()
            return
        }
        try {
            val pcm = OnseEngine.synthesizePcm(this, text, style, settings, rate)
            val max = callback.maxBufferSize
            var offset = 0
            while (offset < pcm.size) {
                val n = minOf(max, pcm.size - offset)
                if (callback.audioAvailable(pcm, offset, n) != TextToSpeech.SUCCESS) return
                offset += n
            }
            callback.done()
        } catch (e: Exception) {
            Log.e("OnseTtsService", "synthesis failed", e)
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
        }
    }

    private fun isJapanese(lang: String?) = lang != null && (lang.equals("jpn", true) || lang.equals("ja", true))
}
