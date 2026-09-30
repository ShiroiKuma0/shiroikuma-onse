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
 * The launcher screen: engine status, the preferred-voice line with its reselect button, a
 * try-it box, a jump to Android's TTS settings, every VOICEVOX voice (sample ▶, download ⤓,
 * select), and the credits. The Settings cog — tap or long-press — opens 白い熊 音声 UI.
 */
@Composable
fun MainScreen(appVersion: String, onOpenUi: () -> Unit) {
    val context = LocalContext.current
    val p by UiStore.prefs.collectAsState()
    val voice by VoiceSettingsStore.settings.collectAsState()
    val states by VoiceStore.states.collectAsState()
    val player by VoicePlayer.status.collectAsState()
    val catalog = remember { VoiceCatalog.get(context) }
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { VoiceStore.refresh(context); tick++ }
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
                StatusLine("Current voice", current?.label ?: "—", false, p)
                StatusLine(
                    "VOICEVOX",
                    "CORE 0.17.0 · voice models ${catalog.release} · ${installed.size} of ${catalog.models.size} installed (${Backup.humanSize(installed.sumOf { it.size })})",
                    false, p,
                )
                StatusLine("Dictionary", "OpenJTalk (bundled)", false, p)
            }
            item {
                Row(Modifier.padding(start = shiroikuma.onse.ui.rowIndent(0, p), top = 4.dp, bottom = 4.dp)) {
                    Pill("Open text-to-speech settings") { openTtsSettings(context) }
                }
            }

            // ---- Preferred voice ---------------------------------------------------------------
            item { SectionHeader("Preferred voice", p) }
            item {
                RowScaffold(0, p) {
                    TitledText(
                        "Preferred: ${VoiceSettings.PREFERRED_LABEL}",
                        if (voice.styleId == VoiceSettings.PREFERRED_STYLE) "In use — the voice of 白い熊's subtitles and audiobooks."
                        else "Now using ${current?.label ?: "another voice"}.",
                        Modifier.weight(1f),
                    )
                    Pill("Reselect default", enabled = voice.styleId != VoiceSettings.PREFERRED_STYLE) {
                        VoiceSettingsStore.update { it.copy(styleId = VoiceSettings.PREFERRED_STYLE) }
                    }
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
                        label = { Text("読み上げるテキスト") },
                    )
                    val shape = RoundedCornerShape(p.buttonCornerPct.coerceIn(0, 50))
                    Box(
                        Modifier.padding(top = 8.dp).fillMaxWidth().height(p.speakButtonHeightDp.dp).clip(shape)
                            .border(p.buttonBorderWidthDp.dp, Color(p.accent), shape)
                            .clickable { if (player.busy) VoicePlayer.stop() else VoicePlayer.speak(context, text, voice.styleId) },
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

            // ---- Voices ------------------------------------------------------------------------
            item { SectionHeader("Voices", p) }
            item {
                NoteText(
                    "▶ plays a sample (bundled — no download needed). ⤓ downloads the voice model from VOICEVOX's official release; one model can hold several voices. Tap a voice to use it.",
                    0, p,
                )
            }
            catalog.characters.forEach { (name, choices) ->
                item(key = "c-$name") { SubHeader(name, p) }
                choices.forEach { choice ->
                    item(key = "s-${choice.style.id}") {
                        VoiceRow(choice, selected = choice.style.id == voice.styleId, state = states[choice.model.file], p = p, playing = player.playingSample == choice.style.id)
                    }
                }
            }
            val singing = catalog.models.filter { it.singing }
            if (singing.isNotEmpty()) {
                item { SectionHeader("Singing voices", p) }
                item { NoteText("The singing model renders music scores, not text, so it cannot read aloud. Its samples sing ド・レ・ミ・レ・ド on ら.", 0, p) }
                singing.forEach { model ->
                    item(key = "m-${model.file}") { ModelRow(model, states[model.file], p) }
                    model.characters.forEach { ch ->
                        ch.styles.filter { it.sample != null }.forEach { st ->
                            item(key = "sing-${st.id}") {
                                RowScaffold(1, p, onClick = { VoicePlayer.playSample(context, st.id, st.sample!!) }) {
                                    Text(if (player.playingSample == st.id) "♪" else "▶", color = Color(p.accent), fontSize = 18.sp)
                                    Text("${ch.name}（${st.name}）", Modifier.weight(1f), color = Color(p.text), fontSize = p.bodySizeSp.sp, fontFamily = p.family())
                                }
                            }
                        }
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
                            "Built on VOICEVOX TTS Engine for Android (MIT) · VOICEVOX CORE (MIT) · VOICEVOX ONNX Runtime (MIT) · OpenJTalk (© 2009 Nara Institute of Science and Technology) · Gson (Apache 2.0).",
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
private fun VoiceRow(choice: VoiceChoice, selected: Boolean, state: VoiceStore.State?, p: UiPrefs, playing: Boolean) {
    val context = LocalContext.current
    val installed = choice.model.bundled || state is VoiceStore.State.Installed
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Color(p.selection) else Color.Transparent)
            .clickable(enabled = installed) { VoiceSettingsStore.update { it.copy(styleId = choice.style.id) } }
            .padding(start = shiroikuma.onse.ui.rowIndent(1, p), end = 12.dp, top = p.voiceRowPadDp.dp, bottom = p.voiceRowPadDp.dp),
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
private fun ModelRow(model: VoiceModel, state: VoiceStore.State?, p: UiPrefs) {
    val context = LocalContext.current
    RowScaffold(0, p) {
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

private fun isDefaultEngine(context: Context): Boolean =
    Settings.Secure.getString(context.contentResolver, "tts_default_synth") == context.packageName

private fun openTtsSettings(context: Context) {
    runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
