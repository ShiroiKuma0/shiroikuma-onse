package shiroikuma.onse.production

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Environment
import android.os.SystemClock
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shiroikuma.onse.english.EnglishEngine
import shiroikuma.onse.render.OggOpusWriter
import shiroikuma.onse.voice.OnseEngine
import shiroikuma.onse.voice.VoiceCatalog
import shiroikuma.onse.voice.VoicePlayer
import shiroikuma.onse.voice.VoiceSettings
import shiroikuma.onse.voice.VoiceSettingsStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** The main screen's tabs, one per language, in 白い熊's order. */
enum class Lang(val tab: String, val engine: String, val glyph: String) {
    JA("日本語", "VOICEVOX", "あ"),
    EN("English", "Kokoro", "A"),
    CS("Čeština", "Piper", "Č"),
}

/**
 * One setting the production dashboard shows and can change for the phrase at hand. [get] reads it
 * from a [VoiceSettings], [set] writes it into one — so the dashboard's settings are simply the
 * saved settings with the overridden knobs written over them.
 */
enum class Knob(val lang: Lang, val label: String, val range: IntRange = 0..0, val step: Int = 1) {
    JA_VOICE(Lang.JA, "Voice"),
    JA_SPEED(Lang.JA, "Speed", 50..200, 5),
    JA_PITCH(Lang.JA, "Pitch", -15..15),
    JA_INTONATION(Lang.JA, "Intonation", 0..200, 5),
    JA_VOLUME(Lang.JA, "Volume", 0..200, 5),
    JA_PRE(Lang.JA, "Pause before", 0..1000, 10),
    JA_POST(Lang.JA, "Pause after", 0..1000, 10),
    EN_VOICE(Lang.EN, "Voice"),
    EN_SPEED(Lang.EN, "Speed", 50..200, 5),
    CS_VOICE(Lang.CS, "Voice"),
    CS_SPEED(Lang.CS, "Speed", 50..200, 5);

    val isVoice: Boolean get() = this == JA_VOICE || this == EN_VOICE || this == CS_VOICE

    fun get(s: VoiceSettings): Any = when (this) {
        JA_VOICE -> s.styleId
        JA_SPEED -> s.speedPct
        JA_PITCH -> s.pitchPct
        JA_INTONATION -> s.intonationPct
        JA_VOLUME -> s.volumePct
        JA_PRE -> s.prePauseMs
        JA_POST -> s.postPauseMs
        EN_VOICE -> s.enVoice
        EN_SPEED -> s.enSpeedPct
        CS_VOICE -> s.csVoice
        CS_SPEED -> s.csSpeedPct
    }

    fun set(s: VoiceSettings, v: Any): VoiceSettings = when (this) {
        JA_VOICE -> s.copy(styleId = v as Int)
        JA_SPEED -> s.copy(speedPct = v as Int)
        JA_PITCH -> s.copy(pitchPct = v as Int)
        JA_INTONATION -> s.copy(intonationPct = v as Int)
        JA_VOLUME -> s.copy(volumePct = v as Int)
        JA_PRE -> s.copy(prePauseMs = v as Int)
        JA_POST -> s.copy(postPauseMs = v as Int)
        EN_VOICE -> s.copy(enVoice = v as String)
        EN_SPEED -> s.copy(enSpeedPct = v as Int)
        CS_VOICE -> s.copy(csVoice = v as String)
        CS_SPEED -> s.copy(csSpeedPct = v as Int)
    }

    /** The value as the dashboard prints it. */
    fun format(context: Context, v: Any): String = when (this) {
        JA_VOICE -> VoiceCatalog.get(context).choiceFor(v as Int)?.label ?: "style $v"
        EN_VOICE -> v as String
        CS_VOICE -> shiroikuma.onse.czech.CzechCatalog.get(context).voice(v as String)?.name ?: v
        JA_PITCH -> (v as Int).let { if (it > 0) "+${it / 100.0}" else "${it / 100.0}" }
        JA_PRE, JA_POST -> "$v ms"
        else -> "$v %"
    }

    companion object {
        fun of(lang: Lang) = entries.filter { it.lang == lang }
    }
}

/** A generated phrase: 16-bit mono PCM at [rate] Hz, with what it was made from ([key]). */
class Clip(
    val pcm: ByteArray,
    val rate: Int,
    val key: String,
    val text: String,
    val synthMs: Long,
    val note: String?,
) {
    val frames: Int get() = pcm.size / 2
    val durationMs: Long get() = frames * 1000L / rate

    /** Peak level per slice, 0…1, scaled to the loudest slice — what the waveform draws. */
    val envelope: FloatArray = run {
        val n = BARS
        val out = FloatArray(n)
        if (frames == 0) return@run out
        for (b in 0 until n) {
            val from = (b.toLong() * frames / n).toInt()
            val to = max(from + 1, ((b + 1).toLong() * frames / n).toInt())
            var peak = 0
            var i = from
            while (i < to) {
                val s = (pcm[2 * i].toInt() and 0xFF) or (pcm[2 * i + 1].toInt() shl 8)
                peak = max(peak, abs(s.toShort().toInt()))
                i += 4
            }
            out[b] = peak / 32768f
        }
        val top = out.maxOrNull()?.takeIf { it > 0f } ?: 1f
        for (b in 0 until n) out[b] = out[b] / top
        out
    }

    companion object {
        const val BARS = 96
    }
}

/** What the dashboard of one language holds. */
data class LangState(
    val text: String,
    val overrides: Map<Knob, Any> = emptyMap(),
    /** Japanese only: an edited reading (AquesTalk-style kana) spoken instead of the text; null = from the text. */
    val reading: String? = null,
    val clip: Clip? = null,
    /** "Generating…" / "Saving 3 / 12…" while busy, else null. */
    val busy: String? = null,
    val message: String? = null,
    val error: Boolean = false,
)

/**
 * One recent phrase: its text, the full set of values it was spoken with ([values], every knob of
 * the language — so 「Use」 brings it back exactly, whatever the saved settings are by then), and its
 * audio, kept as raw PCM in `files/production/history/<id>.pcm`.
 */
data class HistoryEntry(
    val id: String,
    val lang: Lang,
    val text: String,
    val reading: String?,
    val values: Map<Knob, Any>,
    val key: String,
    val durationMs: Long,
    val at: Long,
)

/** The one clip playing, if any — the dashboard's ([historyId] null) or a recent one — and its playhead. */
data class Playback(val lang: Lang? = null, val positionMs: Long = 0, val historyId: String? = null)

enum class OutFormat(val ext: String, val mime: String, val label: String) {
    OGG("ogg", "audio/ogg", "OGG / Opus"),
    WAV("wav", "audio/wav", "WAV"),
}

/** Where Save writes, in which format, at which Opus bitrate. */
data class OutPrefs(val dir: String, val format: OutFormat, val bitrateKbps: Int)

/** Save's optional sentence split: off by default, so the phrase is saved as it stands. */
data class Split(val on: Boolean = false, val joined: Boolean = false, val pauseMs: Int = 400)

/**
 * The main screen's voice production: per language a phrase, the settings it is spoken with —
 * the saved settings ([VoiceSettingsStore]) with whatever the dashboard changed written over them —
 * and the last clip, which can be played, saved to a directory or shared to another app; plus the
 * recent phrases of each language.
 *
 * The overrides and an edited reading live as long as the process; the phrases, the recent list,
 * the tab, the save directory, the format and the bitrate are kept (`onse_production` preferences,
 * the recent audio under `files/production/history/`).
 */
object Production {
    private const val PREFS = "onse_production"
    private const val SHARE_AUTHORITY_SUFFIX = ".share"
    const val BITRATE_MIN = 16
    const val BITRATE_MAX = 128
    const val HISTORY_MAX = 30

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: Context

    private val _states = MutableStateFlow(Lang.entries.associateWith { LangState(sampleText(it)) })
    val states: StateFlow<Map<Lang, LangState>> = _states.asStateFlow()

    private val _history = MutableStateFlow(Lang.entries.associateWith { emptyList<HistoryEntry>() })
    val history: StateFlow<Map<Lang, List<HistoryEntry>>> = _history.asStateFlow()

    private val _playback = MutableStateFlow(Playback())
    val playback: StateFlow<Playback> = _playback.asStateFlow()

    private val _out = MutableStateFlow(OutPrefs(defaultDir(), OutFormat.OGG, 48))
    val out: StateFlow<OutPrefs> = _out.asStateFlow()

    private val _tab = MutableStateFlow(Lang.JA)
    val tab: StateFlow<Lang> = _tab.asStateFlow()

    /** Bumped when text arrives from another app — the main screen scrolls its tab to the dashboard. */
    private val _arrivals = MutableStateFlow(0)
    val arrivals: StateFlow<Int> = _arrivals.asStateFlow()

    private val jobs = HashMap<Lang, Job>()
    private var track: AudioTrack? = null
    private var playJob: Job? = null

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        val sp = sp()
        _states.value = Lang.entries.associateWith { LangState(sp.getString("text.${it.name}", null) ?: sampleText(it)) }
        _out.value = OutPrefs(
            dir = sp.getString("dir", null)?.takeIf { it.isNotBlank() } ?: defaultDir(),
            format = runCatching { OutFormat.valueOf(sp.getString("format", "OGG")!!) }.getOrDefault(OutFormat.OGG),
            bitrateKbps = sp.getInt("bitrate", 48).coerceIn(BITRATE_MIN, BITRATE_MAX),
        )
        _tab.value = runCatching { Lang.valueOf(sp.getString("tab", "JA")!!) }.getOrDefault(Lang.JA)
        _history.value = Lang.entries.associateWith { loadHistory(it) }
    }

    private fun sp() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun defaultDir() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "白い熊 音声").path

    private fun sampleText(lang: Lang) = when (lang) {
        Lang.JA -> "こんにちは。白い熊の音声です。今日はいい天気ですね。"
        Lang.EN -> "I worked seven years in Russia, and I still read the news in Russian."
        Lang.CS -> "Sedm let jsem pracoval v Rusku a zprávy dodnes čtu rusky."
    }

    private fun edit(lang: Lang, f: (LangState) -> LangState) {
        synchronized(_states) { _states.value = _states.value + (lang to f(_states.value.getValue(lang))) }
    }

    // ---- tab, phrase, overrides, reading ----------------------------------------------------

    fun selectTab(lang: Lang) {
        if (_tab.value == lang) return
        _tab.value = lang
        sp().edit().putString("tab", lang.name).apply()
    }

    fun setText(lang: Lang, text: String) {
        edit(lang) { it.copy(text = text, reading = if (text == it.text) it.reading else null, message = null) }
        sp().edit().putString("text.${lang.name}", text).apply()
    }

    /** The language a text is in: kana or kanji → Japanese; a Czech letter → Czech; else English. */
    fun languageOf(text: String): Lang = when {
        text.any { Character.UnicodeScript.of(it.code) in JAPANESE_SCRIPTS } -> Lang.JA
        text.any { it.lowercaseChar() in "áčďéěíňóřšťúůýž" } -> Lang.CS
        else -> Lang.EN
    }

    private val JAPANESE_SCRIPTS = setOf(Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HAN)

    /** Text shared or selected in another app: into the tab of its language, ready to Generate. */
    fun receive(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val lang = languageOf(t)
        setText(lang, t)
        selectTab(lang)
        _arrivals.value++
    }

    /** Change [knob] for this phrase only; choosing the saved value again simply drops the change. */
    fun setOverride(knob: Knob, value: Any) {
        val saved = knob.get(VoiceSettingsStore.settings.value)
        edit(knob.lang) { s -> s.copy(overrides = if (value == saved) s.overrides - knob else s.overrides + (knob to value)) }
    }

    fun clearOverride(knob: Knob) = edit(knob.lang) { it.copy(overrides = it.overrides - knob) }

    /** Back to the saved settings: every change made on the dashboard, an edited reading too, is dropped. */
    fun resetToSaved(lang: Lang) = edit(lang) { it.copy(overrides = emptyMap(), reading = null) }

    /** Write this language's changes into the saved settings (the app's voice everywhere) and clear them. */
    fun makeSaved(lang: Lang) {
        val overrides = _states.value.getValue(lang).overrides
        if (overrides.isEmpty()) return
        VoiceSettingsStore.update { saved -> overrides.entries.fold(saved) { s, (k, v) -> k.set(s, v) } }
        edit(lang) { it.copy(overrides = emptyMap()) }
    }

    /** An edited reading for the Japanese phrase; null or the text's own reading drops it. */
    fun setReading(reading: String?, auto: String?) {
        val r = reading?.trim()?.takeIf { it.isNotEmpty() && it != auto?.trim() }
        edit(Lang.JA) { it.copy(reading = r) }
    }

    /** The reading VOICEVOX gives the Japanese phrase with the dashboard's voice. Blocking. */
    fun autoReading(): String {
        val s = _states.value.getValue(Lang.JA)
        return OnseEngine.kanaFor(app, s.text.trim(), effective(Lang.JA).styleId)
    }

    /** The settings the phrase is spoken with: [saved] with this language's overrides over it. */
    fun effective(lang: Lang, saved: VoiceSettings = VoiceSettingsStore.settings.value): VoiceSettings =
        _states.value.getValue(lang).overrides.entries.fold(saved) { s, (k, v) -> k.set(s, v) }

    /** Overridden and actually different from the saved value. */
    fun isOverridden(knob: Knob, saved: VoiceSettings, state: LangState): Boolean =
        state.overrides[knob]?.let { it != knob.get(saved) } == true

    fun keyOf(lang: Lang, text: String, eff: VoiceSettings, reading: String? = null): String =
        lang.name + "\u0000" + text.trim() + "\u0000" + (reading ?: "") + "\u0000" + Knob.of(lang).joinToString("|") { "${it.get(eff)}" }

    // ---- generate and play -----------------------------------------------------------------

    fun generate(lang: Lang, play: Boolean = true) {
        if (jobs[lang]?.isActive == true) return
        jobs[lang] = scope.launch {
            val clip = clipOrNull(lang) ?: return@launch
            if (play) play(lang, clip)
        }
    }

    /** The current clip, synthesised first when the phrase or its settings changed. Null on failure. */
    private suspend fun clipOrNull(lang: Lang): Clip? {
        val s = _states.value.getValue(lang)
        val text = s.text.trim()
        if (text.isEmpty()) return null
        val eff = effective(lang)
        val reading = s.reading.takeIf { lang == Lang.JA }
        val key = keyOf(lang, s.text, eff, reading)
        s.clip?.takeIf { it.key == key }?.let { return it }
        edit(lang) { it.copy(busy = "Generating…", message = null, error = false) }
        return try {
            val clip = withContext(Dispatchers.Default) { synthesize(lang, text, eff, key, reading) }
            edit(lang) { it.copy(clip = clip, busy = null) }
            remember(lang, clip, reading, eff)
            clip
        } catch (e: Exception) {
            edit(lang) { it.copy(busy = null, message = "Could not generate: ${e.message ?: e.javaClass.simpleName}", error = true) }
            null
        }
    }

    private fun synthesize(lang: Lang, text: String, eff: VoiceSettings, key: String, reading: String? = null): Clip {
        val t0 = SystemClock.elapsedRealtime()
        var note: String? = null
        val pcm = pcmFor(lang, text, eff, reading) { note = it }
        return Clip(pcm, OnseEngine.SAMPLE_RATE, key, text, SystemClock.elapsedRealtime() - t0, note)
    }

    private fun pcmFor(lang: Lang, text: String, eff: VoiceSettings, reading: String?, onNote: (String) -> Unit = {}): ByteArray = when (lang) {
        Lang.JA -> {
            val used = OnseEngine.resolveStyle(app, eff.styleId)
            if (used != eff.styleId) onNote("Voice not installed — spoken with ${VoiceSettings.PREFERRED_LABEL}")
            if (reading != null) OnseEngine.synthesizePcmFromKana(app, reading, used, eff)
            else OnseEngine.synthesizePcm(app, text, used, eff)
        }
        Lang.EN -> EnglishEngine.synthesizePcm(app, text, eff.enVoice, eff.enSpeedPct / 100f)
        Lang.CS -> shiroikuma.onse.czech.CzechEngine.synthesizePcm(app, text, eff.csVoice, eff.csSpeedPct / 100f)
    }

    /** Play [lang]'s clip from [fromMs]. */
    fun play(lang: Lang, fromMs: Long = 0) {
        _states.value.getValue(lang).clip?.let { play(lang, it, fromMs) }
    }

    private fun play(lang: Lang, clip: Clip, fromMs: Long = 0, historyId: String? = null) {
        VoicePlayer.stop() // stops this one too
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(clip.rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(clip.pcm.size.coerceAtLeast(4096))
            .build()
        t.write(clip.pcm, 0, clip.pcm.size)
        val start = (fromMs * clip.rate / 1000).toInt().coerceIn(0, (clip.frames - 1).coerceAtLeast(0))
        if (start > 0) t.playbackHeadPosition = start
        t.play()
        track = t
        _playback.value = Playback(lang, fromMs, historyId)
        playJob = scope.launch {
            while (isActive) {
                if (track !== t) break
                val pos = runCatching { t.playbackHeadPosition }.getOrNull() ?: break
                _playback.value = Playback(lang, pos * 1000L / clip.rate, historyId)
                if (pos >= clip.frames) break
                delay(33)
            }
            if (track === t) stopPlayback()
        }
    }

    fun stopPlayback() {
        val t = track
        track = null
        playJob?.cancel()
        playJob = null
        t?.let { runCatching { it.stop(); it.release() } }
        _playback.value = Playback()
    }

    // ---- recent phrases --------------------------------------------------------------------

    private fun historyDir() = File(app.filesDir, "production/history").apply { mkdirs() }
    private fun pcmFile(id: String) = File(historyDir(), "$id.pcm")

    /** A newly generated clip goes on top of the language's recent list (the same one moves up). */
    private fun remember(lang: Lang, clip: Clip, reading: String?, eff: VoiceSettings) {
        val list = _history.value.getValue(lang)
        val same = list.firstOrNull { it.key == clip.key }
        val entry = same?.copy(at = System.currentTimeMillis()) ?: HistoryEntry(
            id = "${System.currentTimeMillis()}-${(1000..9999).random()}",
            lang = lang,
            text = clip.text,
            reading = reading,
            values = Knob.of(lang).associateWith { it.get(eff) },
            key = clip.key,
            durationMs = clip.durationMs,
            at = System.currentTimeMillis(),
        )
        if (same == null) runCatching { pcmFile(entry.id).writeBytes(clip.pcm) }
        val next = (listOf(entry) + list.filter { it.id != entry.id })
        next.drop(HISTORY_MAX).forEach { pcmFile(it.id).delete() }
        setHistory(lang, next.take(HISTORY_MAX))
    }

    private fun setHistory(lang: Lang, list: List<HistoryEntry>) {
        _history.value = _history.value + (lang to list)
        val arr = org.json.JSONArray()
        list.forEach { e ->
            arr.put(
                org.json.JSONObject()
                    .put("id", e.id).put("text", e.text).put("reading", e.reading ?: "")
                    .put("key", e.key).put("durationMs", e.durationMs).put("at", e.at)
                    .put("values", org.json.JSONObject().also { o -> e.values.forEach { (k, v) -> o.put(k.name, v) } }),
            )
        }
        sp().edit().putString("history.${lang.name}", arr.toString()).apply()
    }

    private fun loadHistory(lang: Lang): List<HistoryEntry> = runCatching {
        val arr = org.json.JSONArray(sp().getString("history.${lang.name}", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            if (!pcmFile(id).isFile) return@mapNotNull null
            val vo = o.getJSONObject("values")
            val values = Knob.of(lang).filter { vo.has(it.name) }.associateWith { k -> if (k.isVoice && k != Knob.JA_VOICE) vo.getString(k.name) else vo.getInt(k.name) }
            HistoryEntry(id, lang, o.getString("text"), o.optString("reading").takeIf { it.isNotEmpty() }, values, o.getString("key"), o.getLong("durationMs"), o.getLong("at"))
        }
    }.getOrDefault(emptyList())

    private fun historyClip(e: HistoryEntry): Clip? =
        runCatching { Clip(pcmFile(e.id).readBytes(), OnseEngine.SAMPLE_RATE, e.key, e.text, 0, null) }.getOrNull()

    fun playHistory(e: HistoryEntry) {
        val clip = historyClip(e) ?: return
        play(e.lang, clip, historyId = e.id)
    }

    /** Back into the dashboard: the phrase, the reading, the exact values it was spoken with, its audio. */
    fun useHistory(e: HistoryEntry) {
        val saved = VoiceSettingsStore.settings.value
        val overrides = e.values.filter { (k, v) -> v != k.get(saved) }
        setText(e.lang, e.text)
        edit(e.lang) { it.copy(overrides = overrides, reading = e.reading, clip = historyClip(e), message = null, error = false) }
    }

    fun deleteHistory(e: HistoryEntry) {
        if (_playback.value.historyId == e.id) stopPlayback()
        pcmFile(e.id).delete()
        setHistory(e.lang, _history.value.getValue(e.lang).filter { it.id != e.id })
    }

    fun clearHistory(lang: Lang) {
        if (_playback.value.lang == lang && _playback.value.historyId != null) stopPlayback()
        _history.value.getValue(lang).forEach { pcmFile(it.id).delete() }
        setHistory(lang, emptyList())
    }

    /** "No.7（読み聞かせ） · 115 %" — the voice and speed a recent phrase was spoken with. */
    fun summary(e: HistoryEntry): String {
        val parts = Knob.of(e.lang).filter { it.isVoice || it == Knob.JA_SPEED || it == Knob.EN_SPEED || it == Knob.CS_SPEED }
            .mapNotNull { k -> e.values[k]?.let { k.format(app, it) } }
        return (parts + listOfNotNull(if (e.reading != null) "edited reading" else null)).joinToString(" · ")
    }

    // ---- save and share --------------------------------------------------------------------

    fun setOut(transform: (OutPrefs) -> OutPrefs) {
        val next = transform(_out.value)
        _out.value = next
        sp().edit().putString("dir", next.dir).putString("format", next.format.name).putInt("bitrate", next.bitrateKbps).apply()
    }

    /** OGG/Opus where the phone has an Opus encoder, otherwise WAV. */
    fun usableFormat(wanted: OutFormat): OutFormat =
        if (wanted == OutFormat.OGG && !OggOpusWriter.isAvailable()) OutFormat.WAV else wanted

    /** `2026-11-03_14-22-05_こんにちは白い熊の音声です` — the stamp, then the start of the phrase. */
    fun suggestedName(text: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val slug = text.trim()
            .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "")
            .replace(Regex("""\s+"""), "_")
            .trim('_', '.')
            .let { s -> s.codePoints().limit(32).toArray().let { String(it, 0, it.size) } }
        return if (slug.isEmpty()) stamp else "${stamp}_$slug"
    }

    fun suggestedName(lang: Lang): String = suggestedName(_states.value.getValue(lang).text)

    private fun encode(pcm: ByteArray, rate: Int, format: OutFormat, target: File) {
        when (format) {
            OutFormat.OGG -> OggOpusWriter.write(pcm, rate, _out.value.bitrateKbps, target)
            OutFormat.WAV -> OggOpusWriter.writeWav(EnglishEngine.wav(pcm, rate), target)
        }
    }

    private fun target(dir: String, name: String, format: OutFormat) =
        File(dir.trim().trimEnd('/'), name.trim().removeSuffix(".${format.ext}") + "." + format.ext)

    /**
     * Write the phrase to `<dir>/<name>.<ext>`, generating it first when needed — or, with [split]
     * on, sentence by sentence: one file each (`<name>_01` …) or one file with a pause between them.
     */
    fun save(lang: Lang, dir: String, name: String, wanted: OutFormat, split: Split = Split()) {
        if (jobs[lang]?.isActive == true) return
        jobs[lang] = scope.launch {
            if (split.on) { saveSplit(lang, dir, name, usableFormat(wanted), split); return@launch }
            val clip = clipOrNull(lang) ?: return@launch
            writeClip(lang, clip.pcm, clip.rate, target(dir, name, usableFormat(wanted)))
        }
    }

    /** Save a recent phrase as it was spoken. */
    fun saveHistory(e: HistoryEntry, dir: String, name: String, wanted: OutFormat) {
        if (jobs[e.lang]?.isActive == true) return
        val clip = historyClip(e) ?: return
        jobs[e.lang] = scope.launch { writeClip(e.lang, clip.pcm, clip.rate, target(dir, name, usableFormat(wanted))) }
    }

    private suspend fun writeClip(lang: Lang, pcm: ByteArray, rate: Int, target: File) {
        edit(lang) { it.copy(busy = "Saving…", message = null, error = false) }
        try {
            withContext(Dispatchers.IO) { encode(pcm, rate, target.extension.let { e -> OutFormat.entries.first { it.ext == e } }, target) }
            edit(lang) { it.copy(busy = null, message = "Saved: ${target.path}", error = false) }
        } catch (e: Exception) {
            edit(lang) { it.copy(busy = null, message = "Could not save: ${e.message ?: e.javaClass.simpleName}", error = true) }
        }
    }

    /**
     * The phrase cut into sentences: after 。！？!?… (with any closing quotes or brackets after
     * them) and at every line break; for English and Czech only where a space or the end follows.
     */
    fun sentences(lang: Lang, text: String): List<String> {
        val enders = if (lang == Lang.JA) "。！？!?…" else ".!?…"
        val closers = "」』）)\"'”’»"
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        fun flush() { cur.toString().trim().takeIf { it.isNotEmpty() }?.let(out::add); cur.clear() }
        val t = text.trim()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            i++
            if (c == '\n') { flush(); continue }
            cur.append(c)
            if (c in enders) {
                while (i < t.length && (t[i] in enders || t[i] in closers)) cur.append(t[i++])
                if (lang == Lang.JA || i >= t.length || t[i].isWhitespace()) flush()
            }
        }
        flush()
        return out
    }

    private suspend fun saveSplit(lang: Lang, dir: String, name: String, format: OutFormat, split: Split) {
        val s = _states.value.getValue(lang)
        val parts = sentences(lang, s.text)
        if (parts.isEmpty()) return
        val eff = effective(lang)
        val stem = name.trim().removeSuffix(".${format.ext}")
        val width = parts.size.toString().length.coerceAtLeast(2)
        val written = mutableListOf<File>()
        try {
            val pcms = ArrayList<ByteArray>()
            parts.forEachIndexed { i, sentence ->
                edit(lang) { it.copy(busy = "Saving ${i + 1} / ${parts.size}…", message = null, error = false) }
                val pcm = withContext(Dispatchers.Default) { pcmFor(lang, sentence, eff, null) }
                if (split.joined) pcms += pcm
                else withContext(Dispatchers.IO) {
                    val f = target(dir, "${stem}_${(i + 1).toString().padStart(width, '0')}", format)
                    encode(pcm, OnseEngine.SAMPLE_RATE, format, f)
                    written += f
                }
            }
            if (split.joined) {
                edit(lang) { it.copy(busy = "Saving…") }
                val gap = ByteArray(split.pauseMs * OnseEngine.SAMPLE_RATE / 1000 * 2)
                val joined = java.io.ByteArrayOutputStream().apply {
                    pcms.forEachIndexed { i, p -> if (i > 0) write(gap); write(p) }
                }.toByteArray()
                val f = target(dir, stem, format)
                withContext(Dispatchers.IO) { encode(joined, OnseEngine.SAMPLE_RATE, format, f) }
                edit(lang) { it.copy(busy = null, message = "Saved ${parts.size} sentences as one file: ${f.path}", error = false) }
            } else {
                edit(lang) { it.copy(busy = null, message = "Saved ${written.size} files in ${written.first().parent}: ${written.first().name} … ${written.last().name}", error = false) }
            }
        } catch (e: Exception) {
            edit(lang) { it.copy(busy = null, message = "Could not save (${written.size} of ${parts.size} written): ${e.message ?: e.javaClass.simpleName}", error = true) }
        }
    }

    /** Hand the phrase to another app (Android's share sheet), generating it first when needed. */
    fun share(context: Context, lang: Lang) {
        if (jobs[lang]?.isActive == true) return
        jobs[lang] = scope.launch {
            val clip = clipOrNull(lang) ?: return@launch
            shareClip(context, lang, clip, suggestedName(lang))
        }
    }

    fun shareHistory(context: Context, e: HistoryEntry) {
        if (jobs[e.lang]?.isActive == true) return
        val clip = historyClip(e) ?: return
        jobs[e.lang] = scope.launch { shareClip(context, e.lang, clip, suggestedName(e.text)) }
    }

    private suspend fun shareClip(context: Context, lang: Lang, clip: Clip, name: String) {
        val format = usableFormat(_out.value.format)
        edit(lang) { it.copy(busy = "Preparing to share…", message = null, error = false) }
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(app.cacheDir, "share")
                dir.listFiles()?.forEach { it.delete() } // only the clip being shared now is kept
                File(dir, "$name.${format.ext}").also { encode(clip.pcm, clip.rate, format, it) }
            }
            val uri = FileProvider.getUriForFile(app, app.packageName + SHARE_AUTHORITY_SUFFIX, file)
            val send = Intent(Intent.ACTION_SEND)
                .setType(format.mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(file.name, uri)
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            edit(lang) { it.copy(busy = null) }
        } catch (e: Exception) {
            edit(lang) { it.copy(busy = null, message = "Could not share: ${e.message ?: e.javaClass.simpleName}", error = true) }
        }
    }
}
