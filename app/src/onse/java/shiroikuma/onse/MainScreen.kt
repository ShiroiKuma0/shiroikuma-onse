package shiroikuma.onse

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import shiroikuma.onse.ui.Backup
import shiroikuma.onse.ui.NoteText
import shiroikuma.onse.ui.Pill
import shiroikuma.onse.ui.PreviewCard
import shiroikuma.onse.ui.RowScaffold
import shiroikuma.onse.ui.SectionHeader
import shiroikuma.onse.ui.SliderRow
import shiroikuma.onse.ui.SwitchRow
import shiroikuma.onse.ui.SubHeader
import shiroikuma.onse.ui.TitledText
import shiroikuma.onse.ui.TopBar
import shiroikuma.onse.ui.UiPrefs
import shiroikuma.onse.ui.UiStore
import shiroikuma.onse.ui.family
import shiroikuma.onse.ui.houseFieldColors
import shiroikuma.onse.voice.VoiceCatalog
import shiroikuma.onse.voice.VoiceChoice
import shiroikuma.onse.voice.VoiceModel
import shiroikuma.onse.voice.VoicePlayer
import shiroikuma.onse.voice.VoiceSettings
import shiroikuma.onse.voice.VoiceSettingsStore
import shiroikuma.onse.voice.VoiceStore

const val GITHUB_URL = "https://github.com/ShiroiKuma0/shiroikuma-onse"

/**
 * The launcher screen: engine status, a jump to Android's TTS settings, a try-it box, then one
 * section per language — Japanese (VOICEVOX), English (Kokoro), Czech (Piper) — each with its
 * current voice and that voice's settings on top and every other voice folded below (sample ▶,
 * download ⤓, select), and the credits. The Settings cog — tap or long-press — opens 白い熊 音声 UI.
 */
@Composable
fun MainScreen(appVersion: String, onOpenUi: () -> Unit) {
    val context = LocalContext.current
    val p by UiStore.prefs.collectAsState()
    val voice by VoiceSettingsStore.settings.collectAsState()
    val states by VoiceStore.states.collectAsState()
    val player by VoicePlayer.status.collectAsState()
    val catalog = remember { VoiceCatalog.get(context) }
    val english = remember { shiroikuma.onse.english.EnglishCatalog.get(context) }
    val enState by shiroikuma.onse.english.EnglishStore.state.collectAsState()
    val czech = remember { shiroikuma.onse.czech.CzechCatalog.get(context) }
    val csStates by shiroikuma.onse.czech.CzechStore.states.collectAsState()
    // Folds: every voice list starts closed; the current voice and its settings are always shown.
    var jaOpen by rememberSaveable { mutableStateOf(false) }
    var singOpen by rememberSaveable { mutableStateOf(false) }
    var enOpen by rememberSaveable { mutableStateOf(false) }
    var csOpen by rememberSaveable { mutableStateOf(false) }
    val openChars = remember { mutableStateListOf<String>() }
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            VoiceStore.refresh(context); shiroikuma.onse.english.EnglishStore.refresh(context); shiroikuma.onse.czech.CzechStore.refresh(context); tick++
        }
    }
    var text by rememberSaveable { mutableStateOf("こんにちは。白い熊の音声です。今日はいい天気ですね。") }
    val current = catalog.choiceFor(voice.styleId)

    Column(Modifier.fillMaxSize().background(Color(p.background))) {
        TopBar("白い熊 音声", onBack = null) { SettingsCog(onOpenUi) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {

            // ---- Engine status ----------------------------------------------------------------
            item { SectionHeader("Engine", p, first = true) }
            item {
                val isDefault = remember(tick) { isDefaultEngine(context) }
                val installed = catalog.models.filter { VoiceStore.isInstalled(context, it) }
                StatusLine("System text-to-speech", if (isDefault) "白い熊 音声 is the selected engine ✓" else "Another engine is selected", !isDefault, p)
                StatusLine(
                    "VOICEVOX",
                    "CORE 0.17.0 · voice models ${catalog.release} · ${installed.size} of ${catalog.models.size} installed (${Backup.humanSize(installed.sumOf { it.size })})",
                    false, p,
                )
                StatusLine("Dictionary", "OpenJTalk (bundled)", false, p)
                val enOk = enState is shiroikuma.onse.english.EnglishStore.State.Installed
                StatusLine(
                    "English (Kokoro)",
                    if (enOk) "Installed — voice ${voice.enVoice}" else "Model not downloaded — English does not work (see English voices below)",
                    !enOk, p,
                )
                val csOk = csStates[voice.csVoice] is shiroikuma.onse.czech.CzechStore.State.Installed
                StatusLine(
                    "Czech (Piper)",
                    if (csOk) "Installed — voice ${voice.csVoice}" else "Voice ${voice.csVoice} not downloaded — Czech does not work (see Czech voices below)",
                    !csOk, p,
                )
            }
            item {
                Row(Modifier.padding(start = shiroikuma.onse.ui.rowIndent(0, p), top = 4.dp, bottom = 4.dp)) {
                    Pill("Open text-to-speech settings") { openTtsSettings(context) }
                }
            }

            // ---- Try it ------------------------------------------------------------------------
            item { SectionHeader("Try it", p) }
            item {
                Column(Modifier.padding(start = shiroikuma.onse.ui.rowIndent(0, p), end = 16.dp, top = 4.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = p.tryTextSizeSp.sp, color = Color(p.text), fontFamily = p.family()),
                        colors = houseFieldColors(),
                        label = { Text("読み上げるテキスト — Japanese, English (Kokoro) or Czech (Piper)") },
                    )
                    val shape = RoundedCornerShape(p.buttonCornerPct.coerceIn(0, 50))
                    Box(
                        Modifier.padding(top = 8.dp).fillMaxWidth().height(p.speakButtonHeightDp.dp).clip(shape)
                            .border(p.buttonBorderWidthDp.dp, Color(p.accent), shape)
                            .clickable { if (player.busy) VoicePlayer.stop() else speakTry(context, text, voice.styleId) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (player.busy) "■ 停止" else "▶ 話す",
                            color = Color(p.accent),
                            fontSize = (p.speakButtonHeightDp / 3).coerceIn(12, 48).sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    player.message?.let { Text(it, color = Color(p.textSecondary), fontSize = p.labelSizeSp.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            }

            // ---- Japanese (VOICEVOX) ------------------------------------------------------------
            // The current voice and its settings first; every other voice folded away — by
            // character, singing included — so the three languages fit without endless scrolling.
            item { SectionHeader("Japanese voices — VOICEVOX", p) }
            item {
                RowScaffold(0, p) {
                    TitledText(
                        "Current: ${current?.label ?: "—"}" + if (voice.styleId == VoiceSettings.PREFERRED_STYLE) "  ★" else "",
                        if (voice.styleId == VoiceSettings.PREFERRED_STYLE) "The voice of 白い熊's subtitles, audiobooks and 言語島."
                        else "Preferred: ${VoiceSettings.PREFERRED_LABEL}.",
                        Modifier.weight(1f),
                    )
                    Pill("▶") { VoicePlayer.speak(context, catalog.sampleText, voice.styleId) }
                    if (voice.styleId != VoiceSettings.PREFERRED_STYLE) {
                        Pill("Default") { VoiceSettingsStore.update { it.copy(styleId = VoiceSettings.PREFERRED_STYLE) } }
                    }
                }
            }
            item { SliderRow(0, p, "Speed", voice.speedPct, pct(voice.speedPct), 50..200, 5) { v -> VoiceSettingsStore.update { it.copy(speedPct = v) } } }
            item { SliderRow(0, p, "Pitch", voice.pitchPct, signed(voice.pitchPct), -15..15) { v -> VoiceSettingsStore.update { it.copy(pitchPct = v) } } }
            item { SliderRow(0, p, "Intonation", voice.intonationPct, pct(voice.intonationPct), 0..200, 5) { v -> VoiceSettingsStore.update { it.copy(intonationPct = v) } } }
            item { SliderRow(0, p, "Volume", voice.volumePct, pct(voice.volumePct), 0..200, 5) { v -> VoiceSettingsStore.update { it.copy(volumePct = v) } } }
            item { SliderRow(0, p, "Pause before speaking", voice.prePauseMs, ms(voice.prePauseMs), 0..1000, 10) { v -> VoiceSettingsStore.update { it.copy(prePauseMs = v) } } }
            item { SliderRow(0, p, "Pause after speaking", voice.postPauseMs, ms(voice.postPauseMs), 0..1000, 10) { v -> VoiceSettingsStore.update { it.copy(postPauseMs = v) } } }
            item {
                SwitchRow(
                    0, p, "Follow the calling app's speech rate",
                    "Apps reading through Android's text-to-speech can speed up or slow down on top of the speed above.",
                    voice.followSystemRate,
                ) { v -> VoiceSettingsStore.update { it.copy(followSystemRate = v) } }
            }
            item {
                RowScaffold(0, p) {
                    TitledText("Restore the 白い熊 voice defaults", "Speed 115 %, pitch 0, intonation 100 %, volume 100 %, pauses 100 ms.", Modifier.weight(1f))
                    Pill("Restore") {
                        VoiceSettingsStore.update {
                            VoiceSettings(styleId = it.styleId, enVoice = it.enVoice, enSpeedPct = it.enSpeedPct, csVoice = it.csVoice, csSpeedPct = it.csSpeedPct)
                        }
                    }
                }
            }
            item {
                val installedModels = catalog.models.count { VoiceStore.isInstalled(context, it) }
                FoldRow(0, p, "All Japanese voices", "${catalog.characters.size} characters · ${catalog.readable.size} voices · $installedModels of ${catalog.models.size} models installed", jaOpen) { jaOpen = !jaOpen }
            }
            if (jaOpen) {
                item {
                    NoteText(
                        "▶ plays a sample (bundled — no download needed). ⤓ downloads the voice model from VOICEVOX's official release; one model can hold several voices. Tap a voice to use it.",
                        1, p,
                    )
                }
                catalog.characters.forEach { (name, choices) ->
                    item(key = "c-$name") {
                        val selectedHere = choices.any { it.style.id == voice.styleId }
                        val installedHere = choices.count { it.model.bundled || states[it.model.file] is VoiceStore.State.Installed }
                        FoldRow(
                            1, p, name + if (selectedHere) "  ✓" else "",
                            "${choices.size} ${if (choices.size == 1) "voice" else "voices"}" + if (installedHere > 0) " · $installedHere installed" else "",
                            name in openChars,
                        ) { if (name in openChars) openChars.remove(name) else openChars.add(name) }
                    }
                    if (name in openChars) {
                        choices.forEach { choice ->
                            item(key = "s-${choice.style.id}") {
                                VoiceRow(choice, selected = choice.style.id == voice.styleId, state = states[choice.model.file], p = p, playing = player.playingSample == choice.style.id)
                            }
                        }
                    }
                }
                val singing = catalog.models.filter { it.singing }
                if (singing.isNotEmpty()) {
                    item(key = "c-singing") {
                        FoldRow(1, p, "Singing voices", "Render music scores, not text — they cannot read aloud.", singOpen) { singOpen = !singOpen }
                    }
                    if (singOpen) {
                        item { NoteText("Their samples sing ド・レ・ミ・レ・ド on ら.", 2, p) }
                        singing.forEach { model ->
                            item(key = "m-${model.file}") { ModelRow(model, states[model.file], p, level = 2) }
                            model.characters.forEach { ch ->
                                ch.styles.filter { it.sample != null }.forEach { st ->
                                    item(key = "sing-${st.id}") {
                                        RowScaffold(3, p, onClick = { VoicePlayer.playSample(context, st.id, st.sample!!) }) {
                                            Text(if (player.playingSample == st.id) "♪" else "▶", color = Color(p.accent), fontSize = 18.sp)
                                            Text("${ch.name}（${st.name}）", Modifier.weight(1f), color = Color(p.text), fontSize = p.bodySizeSp.sp, fontFamily = p.family())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- English (Kokoro) ---------------------------------------------------------------
            // Nothing English works until the model is downloaded — say so in red, model row first.
            item { SectionHeader("English voices — Kokoro", p) }
            item {
                RowScaffold(0, p) {
                    val enInstalled = enState is shiroikuma.onse.english.EnglishStore.State.Installed
                    val note = when (val s = enState) {
                        is shiroikuma.onse.english.EnglishStore.State.Installed -> "Installed (${english.model}) — English works."
                        is shiroikuma.onse.english.EnglishStore.State.Downloading -> "Downloading ${s.done * 100 / s.total.coerceAtLeast(1)} % — English will work once it is installed."
                        is shiroikuma.onse.english.EnglishStore.State.Unpacking -> "Unpacking… — English will work once it is installed."
                        is shiroikuma.onse.english.EnglishStore.State.Failed -> "Download failed: ${s.message}. English does NOT work until the model is installed."
                        else -> "NOT DOWNLOADED — English does not work at all until you download it (one model holds every voice)."
                    }
                    TitledText(
                        "Kokoro English model (required)",
                        note,
                        Modifier.weight(1f),
                        summaryColor = if (enInstalled) Color(p.accent) else Color(p.errorColor),
                        summaryBold = !enInstalled,
                    )
                    when (enState) {
                        is shiroikuma.onse.english.EnglishStore.State.Installed -> Pill("Delete") { shiroikuma.onse.english.EnglishStore.delete(context) }
                        is shiroikuma.onse.english.EnglishStore.State.Downloading -> Pill("Stop") { shiroikuma.onse.english.EnglishStore.cancel() }
                        is shiroikuma.onse.english.EnglishStore.State.Unpacking -> Unit
                        else -> Pill("⤓ ${Backup.humanSize(english.size)}") { shiroikuma.onse.english.EnglishStore.download(context) }
                    }
                }
            }
            item {
                val enInstalled = enState is shiroikuma.onse.english.EnglishStore.State.Installed
                val preferred = voice.enVoice == VoiceSettings.PREFERRED_EN_VOICE
                RowScaffold(0, p) {
                    TitledText(
                        "Current: ${english.voice(voice.enVoice)?.label ?: voice.enVoice}" + if (preferred) "  ★" else "",
                        when {
                            !enInstalled -> "Not usable yet — download the Kokoro English model above first."
                            preferred -> "The English voice of the 言語島 cards."
                            else -> "Preferred: ${VoiceSettings.PREFERRED_EN_VOICE}."
                        },
                        Modifier.weight(1f),
                        summaryColor = if (enInstalled) null else Color(p.errorColor),
                        summaryBold = !enInstalled,
                    )
                    Pill("▶", enabled = enInstalled) { VoicePlayer.speakEnglish(context, "I worked seven years in Russia, and I still read the news in Russian.") }
                    if (!preferred) Pill("Default", enabled = enInstalled) { VoiceSettingsStore.update { it.copy(enVoice = VoiceSettings.PREFERRED_EN_VOICE) } }
                }
            }
            item { SliderRow(0, p, "Speed", voice.enSpeedPct, pct(voice.enSpeedPct), 50..200, 5) { v -> VoiceSettingsStore.update { it.copy(enSpeedPct = v) } } }
            item { FoldRow(0, p, "All English voices", "${english.voices.size} voices · US and UK, female and male", enOpen) { enOpen = !enOpen } }
            if (enOpen) {
                item {
                    NoteText(
                        "▶ plays a bundled sample of each voice — it works without the model; speaking your own text and rendering for 言語島 / 暗記 need the model. Tap a voice to use it.",
                        1, p,
                    )
                }
                listOf("US" to true, "US" to false, "UK" to true, "UK" to false).forEach { (accent, female) ->
                    item(key = "en-h-$accent-$female") { SubHeader("$accent ${if (female) "female" else "male"}", p) }
                    english.voices.filter { it.accent == accent && it.female == female }.forEach { v ->
                        item(key = "en-${v.name}") {
                            SherpaVoiceRow(
                                p, v.name + if (v.name == VoiceSettings.PREFERRED_EN_VOICE) "  ★" else "", null,
                                playing = player.playingSample == 100_000 + v.sid,
                                selected = v.name == voice.enVoice,
                                installed = enState is shiroikuma.onse.english.EnglishStore.State.Installed,
                                onPlay = { VoicePlayer.playSample(context, 100_000 + v.sid, v.sample) },
                                onSelect = { VoiceSettingsStore.update { it.copy(enVoice = v.name) } },
                            ) {}
                        }
                    }
                }
            }

            // ---- Czech (Piper) ------------------------------------------------------------------
            // One model per voice. The current voice's model state is shown in its row, in red
            // while it is missing, with its download right there.
            item { SectionHeader("Czech voices — Piper", p) }
            item {
                val cur = czech.voice(voice.csVoice)
                val st = cur?.let { csStates[it.name] }
                val ok = st is shiroikuma.onse.czech.CzechStore.State.Installed
                val preferred = voice.csVoice == VoiceSettings.PREFERRED_CS_VOICE
                RowScaffold(0, p) {
                    TitledText(
                        "Current: ${cur?.label ?: voice.csVoice}" + if (preferred) "  ★" else "",
                        if (ok) (if (preferred) "The Czech voice of the 言語島 cards." else "Preferred: ${VoiceSettings.PREFERRED_CS_VOICE}.")
                        else czechStateNote(st),
                        Modifier.weight(1f),
                        summaryColor = if (ok) null else Color(p.errorColor),
                        summaryBold = !ok,
                    )
                    Pill("▶", enabled = ok) { VoicePlayer.speakCzech(context, "Sedm let jsem pracoval v Rusku a zprávy dodnes čtu rusky.") }
                    if (cur != null && !ok) CzechModelPill(cur, st)
                    if (!preferred) Pill("Default") { VoiceSettingsStore.update { it.copy(csVoice = VoiceSettings.PREFERRED_CS_VOICE) } }
                }
            }
            item { SliderRow(0, p, "Speed", voice.csSpeedPct, pct(voice.csSpeedPct), 50..200, 5) { v -> VoiceSettingsStore.update { it.copy(csSpeedPct = v) } } }
            item {
                val installed = czech.voices.count { csStates[it.name] is shiroikuma.onse.czech.CzechStore.State.Installed }
                FoldRow(0, p, "All Czech voices", "${czech.voices.size} voices · $installed installed · each its own ≈67 MB download", csOpen) { csOpen = !csOpen }
            }
            if (csOpen) {
                item { NoteText("▶ plays a bundled sample — it works without the model. Tap an installed voice to use it.", 1, p) }
                czech.voices.forEachIndexed { i, v ->
                    item(key = "cs-${v.name}") {
                        val st = csStates[v.name]
                        SherpaVoiceRow(
                            p, v.label + if (v.name == VoiceSettings.PREFERRED_CS_VOICE) "  ★" else "",
                            if (st is shiroikuma.onse.czech.CzechStore.State.Installed) "${v.model} · installed · ${v.license}" else "${czechStateNote(st)} · ${v.license}",
                            playing = player.playingSample == 200_000 + i,
                            selected = v.name == voice.csVoice,
                            installed = st is shiroikuma.onse.czech.CzechStore.State.Installed,
                            onPlay = { VoicePlayer.playSample(context, 200_000 + i, v.sample) },
                            onSelect = { VoiceSettingsStore.update { it.copy(csVoice = v.name) } },
                        ) { CzechModelPill(v, st) }
                    }
                }
            }

            // ---- About ---------------------------------------------------------------------------
            item { SectionHeader("About", p) }
            item {
                PreviewCard(0, p) {
                    val credited = catalog.models.filter { VoiceStore.isInstalled(context, it) && !it.singing }
                        .flatMap { m -> m.characters.map { it.name } }.distinct()
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("白い熊 音声 $appVersion", color = Color(p.text), fontWeight = FontWeight.Bold, fontFamily = p.family())
                        Text(GITHUB_URL, color = Color(p.accent), fontSize = p.labelSizeSp.sp)
                        Text("Voices: " + credited.joinToString(" · ") { "VOICEVOX:$it" }, color = Color(p.text), fontSize = p.labelSizeSp.sp)
                        Text(
                            "Each voice has its own terms of use (see each character's page on voicevox.hiroshiba.jp); audio made with it carries its VOICEVOX credit.",
                            color = Color(p.textSecondary), fontSize = p.labelSizeSp.sp,
                        )
                        Text(
                            "Built on VOICEVOX TTS Engine for Android (MIT) · VOICEVOX CORE (MIT) · VOICEVOX ONNX Runtime (MIT) · OpenJTalk (© 2009 Nara Institute of Science and Technology) · Gson (Apache 2.0). English: Kokoro-82M (Apache 2.0) · Czech: Piper cs_CZ jirka (dataset CC0) and kasandra (dataset CC BY 4.0, Ondřej Šimek) · both via sherpa-onnx (Apache 2.0, with ONNX Runtime, MIT) · Apache Commons Compress (Apache 2.0).",
                            color = Color(p.textSecondary), fontSize = p.labelSizeSp.sp,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsCog(onOpenUi: () -> Unit) {
    // A tap and a long-press both open 白い熊 音声 UI (白い熊, 2026-09-30); a Box, since an
    // IconButton cannot take a long-press.
    Box(
        Modifier.padding(horizontal = 4.dp).size(44.dp).clip(CircleShape)
            .combinedClickable(onClick = onOpenUi, onLongClick = onOpenUi),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Settings, contentDescription = "白い熊 音声 UI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun StatusLine(title: String, value: String, warn: Boolean, p: UiPrefs) {
    RowScaffold(0, p) {
        TitledText(title, value, Modifier.weight(1f), summaryColor = if (warn) Color(p.errorColor) else Color(p.accent))
    }
}

@Composable
private fun VoiceRow(choice: VoiceChoice, selected: Boolean, state: VoiceStore.State?, p: UiPrefs, playing: Boolean, level: Int = 2) {
    val context = LocalContext.current
    val installed = choice.model.bundled || state is VoiceStore.State.Installed
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Color(p.selection) else Color.Transparent)
            .clickable(enabled = installed) { VoiceSettingsStore.update { it.copy(styleId = choice.style.id) } }
            .padding(start = shiroikuma.onse.ui.rowIndent(level, p), end = 12.dp, top = p.voiceRowPadDp.dp, bottom = p.voiceRowPadDp.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (playing) "♪" else "▶",
            Modifier.clip(CircleShape).clickable(enabled = choice.style.sample != null) {
                choice.style.sample?.let { VoicePlayer.playSample(context, choice.style.id, it) }
            }.padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color(p.accent), fontSize = 20.sp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                choice.style.name + if (choice.style.id == VoiceSettings.PREFERRED_STYLE) "  ★" else "",
                color = Color(if (installed) p.text else p.textSecondary),
                fontSize = p.bodySizeSp.sp, fontFamily = p.family(),
                fontWeight = if (selected) FontWeight.Bold else null,
            )
            Text(modelNote(choice.model, state), color = Color(p.textSecondary), fontSize = (p.labelSizeSp - 1).sp)
        }
        when {
            selected -> Text("✓", color = Color(p.accent), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            installed -> Unit
            state is VoiceStore.State.Downloading -> Pill("Stop") { VoiceStore.cancel(choice.model) }
            else -> Pill("⤓ ${Backup.humanSize(choice.model.size)}") { VoiceStore.download(context, choice.model) }
        }
    }
}

@Composable
private fun ModelRow(model: VoiceModel, state: VoiceStore.State?, p: UiPrefs, level: Int = 0) {
    val context = LocalContext.current
    RowScaffold(level, p) {
        TitledText(model.file, modelNote(model, state), Modifier.weight(1f))
        when (state) {
            is VoiceStore.State.Installed -> Pill("Delete") { VoiceStore.delete(context, model) }
            is VoiceStore.State.Downloading -> Pill("Stop") { VoiceStore.cancel(model) }
            else -> Pill("⤓ ${Backup.humanSize(model.size)}") { VoiceStore.download(context, model) }
        }
    }
}

private fun modelNote(model: VoiceModel, state: VoiceStore.State?): String = when {
    model.bundled -> "${model.file} · built in"
    state is VoiceStore.State.Installed -> "${model.file} · installed"
    state is VoiceStore.State.Downloading -> "${model.file} · downloading ${state.done * 100 / state.total.coerceAtLeast(1)} %"
    state is VoiceStore.State.Failed -> "${model.file} · failed: ${state.message}"
    else -> "${model.file} · not downloaded"
}

/**
 * The try-it box reads Japanese with VOICEVOX; text without any kana/kanji with Piper Czech when it
 * has a Czech letter (háček / kroužek / čárka), otherwise with Kokoro English.
 */
private fun speakTry(context: Context, text: String, styleId: Int) {
    val japanese = text.any { Character.UnicodeScript.of(it.code) in setOf(Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HAN) }
    val czech = text.any { it.lowercaseChar() in "áčďéěíňóřšťúůýž" }
    when {
        japanese -> VoicePlayer.speak(context, text, styleId)
        czech -> VoicePlayer.speakCzech(context, text)
        else -> VoicePlayer.speakEnglish(context, text)
    }
}

/** A fold: ▸ closed / ▾ open, a bold title and a one-line summary; the whole row toggles. */
@Composable
private fun FoldRow(level: Int, p: UiPrefs, title: String, summary: String, open: Boolean, onToggle: () -> Unit) {
    RowScaffold(level, p, onClick = onToggle) {
        Text(if (open) "▾" else "▸", color = Color(p.accent), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        TitledText(title, summary, Modifier.weight(1f))
    }
}

/** One English or Czech voice in its list: ▶ sample, name (+ note), ✓ when selected, then [trailing]. */
@Composable
private fun SherpaVoiceRow(
    p: UiPrefs,
    name: String,
    note: String?,
    playing: Boolean,
    selected: Boolean,
    installed: Boolean,
    onPlay: () -> Unit,
    onSelect: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Color(p.selection) else Color.Transparent)
            .clickable(enabled = installed, onClick = onSelect)
            .padding(start = shiroikuma.onse.ui.rowIndent(1, p), end = 12.dp, top = p.voiceRowPadDp.dp, bottom = p.voiceRowPadDp.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (playing) "♪" else "▶",
            Modifier.clip(CircleShape).clickable(onClick = onPlay).padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color(p.accent), fontSize = 20.sp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = Color(if (installed) p.text else p.textSecondary),
                fontSize = p.bodySizeSp.sp, fontFamily = p.family(),
                fontWeight = if (selected) FontWeight.Bold else null,
            )
            note?.let { Text(it, color = Color(p.textSecondary), fontSize = (p.labelSizeSp - 1).sp) }
        }
        if (selected) Text("✓", color = Color(p.accent), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        trailing()
    }
}

/** Download / Stop / Delete for one Czech voice's model. */
@Composable
private fun CzechModelPill(v: shiroikuma.onse.czech.CzechVoice, state: shiroikuma.onse.czech.CzechStore.State?) {
    val context = LocalContext.current
    when (state) {
        is shiroikuma.onse.czech.CzechStore.State.Installed -> Pill("Delete") { shiroikuma.onse.czech.CzechStore.delete(context, v) }
        is shiroikuma.onse.czech.CzechStore.State.Downloading -> Pill("Stop") { shiroikuma.onse.czech.CzechStore.cancel(v) }
        is shiroikuma.onse.czech.CzechStore.State.Unpacking -> Unit
        else -> Pill("⤓ ${Backup.humanSize(v.size)}") { shiroikuma.onse.czech.CzechStore.download(context, v) }
    }
}

private fun czechStateNote(state: shiroikuma.onse.czech.CzechStore.State?): String = when (state) {
    is shiroikuma.onse.czech.CzechStore.State.Installed -> "Installed."
    is shiroikuma.onse.czech.CzechStore.State.Downloading -> "Downloading ${state.done * 100 / state.total.coerceAtLeast(1)} % — works once it is installed."
    is shiroikuma.onse.czech.CzechStore.State.Unpacking -> "Unpacking… — works once it is installed."
    is shiroikuma.onse.czech.CzechStore.State.Failed -> "Download failed: ${state.message}. Does NOT work until the model is installed."
    else -> "NOT DOWNLOADED — does not work until its model is downloaded."
}

private fun pct(v: Int) = "$v %"
private fun signed(v: Int) = if (v > 0) "+${v / 100.0}" else "${v / 100.0}"
private fun ms(v: Int) = "$v ms"

private fun isDefaultEngine(context: Context): Boolean =
    Settings.Secure.getString(context.contentResolver, "tts_default_synth") == context.packageName

private fun openTtsSettings(context: Context) {
    runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
