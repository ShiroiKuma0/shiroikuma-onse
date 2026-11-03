package shiroikuma.onse.production

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import shiroikuma.onse.ui.HouseCheckRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shiroikuma.onse.czech.CzechCatalog
import shiroikuma.onse.czech.CzechStore
import shiroikuma.onse.english.EnglishCatalog
import shiroikuma.onse.english.EnglishStore
import shiroikuma.onse.ui.DialogTitle
import shiroikuma.onse.ui.DirectoryDialog
import shiroikuma.onse.ui.HouseDialog
import shiroikuma.onse.ui.Pill
import shiroikuma.onse.ui.UiPrefs
import shiroikuma.onse.ui.family
import shiroikuma.onse.ui.houseFieldColors
import shiroikuma.onse.voice.VoiceCatalog
import shiroikuma.onse.voice.VoicePlayer
import shiroikuma.onse.voice.VoiceSettings
import shiroikuma.onse.voice.VoiceSettingsStore
import shiroikuma.onse.voice.VoiceStore
import kotlin.math.roundToInt

/**
 * The top of each language tab: a phrase, the settings it will be spoken with, Generate (then Play
 * / Stop), the waveform of the last clip with its playhead, Save / Share, and the recent phrases.
 *
 * The settings shown are the saved settings of the tab's section below, so they follow every change
 * made there. A tap on one changes it for this phrase only: such a chip is drawn filled, with its own
 * ↺; 「↺ Saved settings」 drops every change at once, 「★ Make saved」 writes them into the saved
 * settings. Japanese adds the Reading chip: the kana VOICEVOX reads, editable.
 */
@Composable
fun ProductionDashboard(lang: Lang, p: UiPrefs) {
    Column(Modifier.fillMaxWidth()) { DashboardCard(lang, p) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardCard(lang: Lang, p: UiPrefs) {
    val context = LocalContext.current
    val saved by VoiceSettingsStore.settings.collectAsState()
    val states by Production.states.collectAsState()
    val playback by Production.playback.collectAsState()
    val out by Production.out.collectAsState()
    val enState by EnglishStore.state.collectAsState()
    val csStates by CzechStore.states.collectAsState()
    val st = states.getValue(lang)
    val eff = st.overrides.entries.fold(saved) { s, (k, v) -> k.set(s, v) }
    val reading = st.reading.takeIf { lang == Lang.JA }
    val current = st.clip != null && st.clip.key == Production.keyOf(lang, st.text, eff, reading)
    val playingHere = playback.lang == lang && playback.historyId == null
    val changedKnobs = Knob.of(lang).filter { Production.isOverridden(it, saved, st) }
    val anyChange = changedKnobs.isNotEmpty() || reading != null

    val notReady: String? = when (lang) {
        Lang.JA -> null
        Lang.EN -> if (enState !is EnglishStore.State.Installed) "The Kokoro English model is not downloaded — download it in “Saved settings” below." else null
        Lang.CS -> if (csStates[eff.csVoice] !is CzechStore.State.Installed) "Czech voice ${eff.csVoice} is not downloaded — download it below, or pick an installed voice." else null
    }
    val canProduce = notReady == null && st.text.isNotBlank() && st.busy == null

    var knobDialog by remember { mutableStateOf<Knob?>(null) }
    var readingDialog by remember { mutableStateOf(false) }
    var makeSavedDialog by remember { mutableStateOf(false) }
    var saveDialog by rememberSaveable { mutableStateOf(false) }

    val accent = Color(p.accent)
    val card = RoundedCornerShape(p.cornerRadiusDp.dp)

    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)
            .clip(card).background(Color(p.surface))
            .then(if (p.borderWidthDp > 0) Modifier.border(p.borderWidthDp.dp, Color(p.border), card) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- header --------------------------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).border(2.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
                Text(lang.glyph, color = accent, fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text("Voice production", color = accent, fontSize = (p.titleSizeSp - 1).sp, fontWeight = FontWeight.Bold, fontFamily = p.family())
                Text("${lang.tab} · ${lang.engine}", color = Color(p.textSecondary), fontSize = p.labelSizeSp.sp)
            }
        }

        // ---- phrase --------------------------------------------------------------------------
        OutlinedTextField(
            value = st.text,
            onValueChange = { Production.setText(lang, it) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = p.tryTextSizeSp.sp, color = Color(p.text), fontFamily = p.family()),
            colors = houseFieldColors(),
            minLines = 2,
            maxLines = 8,
            label = { Text("Phrase") },
            trailingIcon = if (st.text.isEmpty()) null else {
                {
                    Text(
                        "✕",
                        Modifier.clip(CircleShape).clickable { Production.setText(lang, "") }.padding(8.dp),
                        color = Color(p.textSecondary), fontSize = 18.sp,
                    )
                }
            },
        )

        // ---- settings ------------------------------------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Spoken with", color = Color(p.textSecondary), fontSize = p.labelSizeSp.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Knob.of(lang).forEach { knob ->
                    KnobChip(
                        p = p,
                        label = knob.label,
                        value = knob.format(context, knob.get(eff)),
                        overridden = Production.isOverridden(knob, saved, st),
                        onClick = { knobDialog = knob },
                        onReset = { Production.clearOverride(knob) },
                    )
                }
                if (lang == Lang.JA) {
                    KnobChip(
                        p = p,
                        label = "Reading",
                        value = reading?.let { if (it.length > 18) it.take(17) + "…" else it } ?: "from the text",
                        overridden = reading != null,
                        onClick = { readingDialog = true },
                        onReset = { Production.setReading(null, null) },
                    )
                }
            }
            Text(
                if (anyChange) "Filled = changed for this phrase only. The rest follow the saved settings below."
                else "These are the saved settings below. Tap one to change it for this phrase only.",
                color = Color(p.textSecondary), fontSize = (p.labelSizeSp - 1).sp,
            )
            if (anyChange) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("↺ Saved settings") { Production.resetToSaved(lang) }
                    if (changedKnobs.isNotEmpty()) Pill("★ Make saved") { makeSavedDialog = true }
                }
            }
        }

        notReady?.let { Text(it, color = Color(p.errorColor), fontSize = p.labelSizeSp.sp, fontWeight = FontWeight.Bold) }

        // ---- generate / play: the block's main pill — 3 dp frame, bold, never filled ----------
        val enabled = when {
            st.busy != null -> false
            current -> true
            else -> canProduce
        }
        val ink = if (enabled || st.busy != null) accent else accent.copy(alpha = 0.38f)
        Box(
            Modifier.fillMaxWidth().height(p.speakButtonHeightDp.dp).clip(RoundedCornerShape(50))
                .border(3.dp, ink, RoundedCornerShape(50))
                .clickable(enabled = enabled) {
                    when {
                        current && playingHere -> Production.stopPlayback()
                        current -> Production.play(lang)
                        else -> Production.generate(lang)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    st.busy != null -> st.busy
                    current && playingHere -> "■ Stop"
                    current -> "▶ Play"
                    else -> "▶ Generate"
                },
                color = ink,
                fontSize = (p.speakButtonHeightDp / 3).coerceIn(12, 48).sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (st.busy != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = accent, trackColor = accent.copy(alpha = 0.2f))
        }

        // ---- the clip ------------------------------------------------------------------------
        st.clip?.let { clip ->
            val pos = if (playingHere) playback.positionMs else 0L
            Waveform(clip, pos, playingHere, current, accent) { fraction -> Production.play(lang, (clip.durationMs * fraction).toLong()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${clock(pos)} / ${clock(clip.durationMs)}",
                    Modifier.weight(1f),
                    color = accent, fontSize = p.labelSizeSp.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        !current -> "Changed since — Generate to update"
                        clip.synthMs > 0 -> "made in ${clip.synthMs} ms"
                        else -> "from Recent"
                    },
                    color = Color(p.textSecondary), fontSize = (p.labelSizeSp - 1).sp,
                )
            }
            clip.note?.takeIf { current }?.let { Text(it, color = Color(p.errorColor), fontSize = (p.labelSizeSp - 1).sp) }
        }

        // ---- save / share --------------------------------------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("⤓  Save", canProduce, Modifier.weight(1f).height(48.dp)) { saveDialog = true }
            Pill("⇪  Share", canProduce, Modifier.weight(1f).height(48.dp)) { Production.share(context, lang) }
        }
        Text(
            "Save and Share generate first when the phrase or its settings changed · ${Production.usableFormat(out.format).label}" +
                if (Production.usableFormat(out.format) == OutFormat.OGG) " ${out.bitrateKbps} kbps" else "",
            color = Color(p.textSecondary), fontSize = (p.labelSizeSp - 1).sp,
        )
        st.message?.let {
            Text(it, color = if (st.error) Color(p.errorColor) else accent, fontSize = p.labelSizeSp.sp, fontWeight = FontWeight.Bold)
        }
    }

    RecentPhrases(lang, p)

    knobDialog?.let { knob ->
        if (knob.isVoice) VoiceDialog(knob, saved, eff, p) { knobDialog = null }
        else KnobDialog(knob, saved, eff, p) { knobDialog = null }
    }
    if (readingDialog) ReadingDialog(st, p) { readingDialog = false }
    if (makeSavedDialog) MakeSavedDialog(lang, changedKnobs, saved, eff, p) { makeSavedDialog = false }
    if (saveDialog) SaveDialog(lang, null, out, p) { saveDialog = false }
}

// ---- recent phrases ----------------------------------------------------------------------------

/** The language's recent phrases under the dashboard, folded: ▶ plays, a tap on the text brings it back. */
@Composable
private fun RecentPhrases(lang: Lang, p: UiPrefs) {
    val context = LocalContext.current
    val history by Production.history.collectAsState()
    val playback by Production.playback.collectAsState()
    val out by Production.out.collectAsState()
    val list = history.getValue(lang)
    var open by rememberSaveable(lang) { mutableStateOf(false) }
    var saving by remember { mutableStateOf<HistoryEntry?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }
    if (list.isEmpty()) return
    val accent = Color(p.accent)
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (open) "▾" else "▸", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Recent phrases", Modifier.weight(1f), color = accent, fontSize = p.bodySizeSp.sp, fontWeight = FontWeight.Bold, fontFamily = p.family())
            Text("${list.size}", color = Color(p.textSecondary), fontSize = p.labelSizeSp.sp)
            if (open) Pill("Clear") { clearConfirm = true }
        }
        if (open) {
            Text(
                "Tap a phrase to bring it back into the dashboard with exactly the settings it was spoken with.",
                Modifier.padding(start = 4.dp, bottom = 4.dp), color = Color(p.textSecondary), fontSize = (p.labelSizeSp - 1).sp,
            )
            list.forEach { e ->
                val playing = playback.historyId == e.id
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RoundPill(if (playing) "■" else "▶", p) { if (playing) Production.stopPlayback() else Production.playHistory(e) }
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { Production.useHistory(e) }.padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text(e.text, color = Color(p.text), fontSize = p.bodySizeSp.sp, fontFamily = p.family(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${clock(e.durationMs)} · ${Production.summary(e)} · ${ago(e.at)}",
                            color = Color(p.textSecondary), fontSize = (p.labelSizeSp - 1).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RoundPill("⤓", p) { saving = e }
                    RoundPill("⇪", p) { Production.shareHistory(context, e) }
                    RoundPill("✕", p) { Production.deleteHistory(e) }
                }
            }
        }
    }
    saving?.let { e -> SaveDialog(lang, e, out, p) { saving = null } }
    if (clearConfirm) {
        HouseDialog({ clearConfirm = false }) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogTitle("Clear recent phrases?")
                Text("All ${list.size} recent ${lang.tab} phrases and their audio are removed. Files you saved stay.", color = Color(p.text), fontSize = 15.sp)
                Row(Modifier.fillMaxWidth()) {
                    Pill("Cancel") { clearConfirm = false }
                    Spacer(Modifier.weight(1f))
                    Pill("Clear", main = true) { Production.clearHistory(lang); clearConfirm = false }
                }
            }
        }
    }
}

/** A small round regular pill holding one glyph — the recent list's actions. */
@Composable
private fun RoundPill(glyph: String, p: UiPrefs, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).border(1.25.dp, Color(p.accent), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Color(p.accent), fontSize = 15.sp) }
}

private fun ago(at: Long): String {
    val m = (System.currentTimeMillis() - at) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        else -> java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(at))
    }
}

/** One setting: a small label over its value. Filled with the accent when changed for this phrase. */
@Composable
private fun KnobChip(p: UiPrefs, label: String, value: String, overridden: Boolean, onClick: () -> Unit, onReset: () -> Unit) {
    val accent = Color(p.accent)
    val onAccent = Color(p.background)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.clip(shape)
            .background(if (overridden) accent else Color.Transparent)
            .border(1.5.dp, if (overridden) accent else accent.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = if (overridden) 4.dp else 10.dp, top = 4.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, color = if (overridden) onAccent.copy(alpha = 0.75f) else Color(p.textSecondary), fontSize = 11.sp)
            Text(
                value,
                color = if (overridden) onAccent else Color(p.text),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = p.family(),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (overridden) {
            Text(
                "↺",
                Modifier.padding(start = 4.dp).clip(CircleShape).clickable(onClick = onReset).padding(horizontal = 6.dp, vertical = 2.dp),
                color = onAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** The clip's envelope as rounded bars; played bars full, the rest faint; a tap plays from there. */
@Composable
private fun Waveform(clip: Clip, positionMs: Long, playing: Boolean, current: Boolean, accent: Color, onSeek: (Float) -> Unit) {
    val progress = if (clip.durationMs > 0) (positionMs.toFloat() / clip.durationMs).coerceIn(0f, 1f) else 0f
    Canvas(
        Modifier.fillMaxWidth().height(58.dp).pointerInput(clip) {
            detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) }
        },
    ) {
        val n = clip.envelope.size
        val slot = size.width / n
        val bar = (slot * 0.62f).coerceAtLeast(1f)
        val mid = size.height / 2
        val dim = accent.copy(alpha = if (current) 0.32f else 0.18f)
        for (i in 0 until n) {
            val h = (clip.envelope[i] * (size.height - 4f)).coerceAtLeast(3f)
            val x = i * slot + (slot - bar) / 2
            val played = playing && (i + 0.5f) / n <= progress
            drawRoundRect(
                color = if (played || (!playing && current)) accent.copy(alpha = if (played) 1f else 0.75f) else dim,
                topLeft = Offset(x, mid - h / 2),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 2, bar / 2),
            )
        }
        if (playing) {
            val x = progress * size.width
            drawLine(accent, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

private fun clock(ms: Long): String = "%d:%02d.%d".format(ms / 60000, ms / 1000 % 60, ms / 100 % 10)

// ---- dialogs ---------------------------------------------------------------------------------

/**
 * A number setting: its value, a slider (live), the saved value as a one-tap way back. Done keeps
 * what the slider says; Cancel — and back, and a tap outside — puts back the value it opened with.
 */
@Composable
private fun KnobDialog(knob: Knob, saved: VoiceSettings, eff: VoiceSettings, p: UiPrefs, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val value = knob.get(eff) as Int
    val savedValue = knob.get(saved) as Int
    val opened = remember { Production.states.value.getValue(knob.lang).overrides[knob] }
    val cancel = {
        if (opened == null) Production.clearOverride(knob) else Production.setOverride(knob, opened)
        onDismiss()
    }
    HouseDialog(cancel) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogTitle(knob.label)
            Text(knob.format(context, value), color = Color(p.accent), fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text("Saved setting: ${knob.format(context, savedValue)}", color = Color(p.textSecondary), fontSize = 13.sp)
            val r = knob.range
            Slider(
                value = value.toFloat().coerceIn(r.first.toFloat(), r.last.toFloat()),
                onValueChange = { v -> Production.setOverride(knob, ((v - r.first) / knob.step).roundToInt() * knob.step + r.first) },
                valueRange = r.first.toFloat()..r.last.toFloat(),
                steps = ((r.last - r.first) / knob.step - 1).coerceIn(0, 200),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pill("↺ Saved", enabled = value != savedValue) { Production.clearOverride(knob) }
                Spacer(Modifier.weight(1f))
                Pill("Cancel", onClick = cancel)
                Spacer(Modifier.width(8.dp))
                Pill("Done", main = true, onClick = onDismiss)
            }
        }
    }
}

/** Pick the voice for this phrase: ▶ hears a sample, a tap on the name chooses it. */
@Composable
private fun VoiceDialog(knob: Knob, saved: VoiceSettings, eff: VoiceSettings, p: UiPrefs, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player by VoicePlayer.status.collectAsState()
    val savedValue = knob.get(saved)
    val chosen = knob.get(eff)

    data class Entry(val header: String?, val value: Any?, val name: String, val sampleId: Int?, val sample: String?, val usable: Boolean, val note: String?)

    val entries = remember(knob) {
        when (knob) {
            Knob.JA_VOICE -> VoiceCatalog.get(context).characters.flatMap { (name, choices) ->
                val inst = choices.filter { it.model.bundled || VoiceStore.isInstalled(context, it.model) }
                if (inst.isEmpty()) emptyList()
                else listOf(Entry(name, null, name, null, null, false, null)) +
                    inst.map { c -> Entry(null, c.style.id, c.style.name + if (c.style.id == VoiceSettings.PREFERRED_STYLE) "  ★" else "", c.style.id, c.style.sample, true, null) }
            }
            Knob.EN_VOICE -> {
                val ok = EnglishStore.isInstalled(context)
                listOf("US" to true, "US" to false, "UK" to true, "UK" to false).flatMap { (accent, female) ->
                    listOf(Entry("$accent ${if (female) "female" else "male"}", null, "", null, null, false, null)) +
                        EnglishCatalog.get(context).voices.filter { it.accent == accent && it.female == female }.map { v ->
                            Entry(null, v.name, v.name + if (v.name == VoiceSettings.PREFERRED_EN_VOICE) "  ★" else "", 100_000 + v.sid, v.sample, ok, if (ok) null else "model not downloaded")
                        }
                }
            }
            else -> CzechCatalog.get(context).voices.mapIndexed { i, v ->
                val ok = CzechStore.isInstalled(context, v)
                Entry(null, v.name, v.label + if (v.name == VoiceSettings.PREFERRED_CS_VOICE) "  ★" else "", 200_000 + i, v.sample, ok, if (ok) null else "not downloaded")
            }
        }
    }

    HouseDialog(onDismiss) {
        Column(Modifier.padding(vertical = 18.dp)) {
            Box(Modifier.padding(horizontal = 20.dp)) { DialogTitle("Voice for this phrase") }
            Text(
                "Saved voice: ${knob.format(context, savedValue)}" + if (knob == Knob.JA_VOICE) " · only installed voices are listed" else "",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = Color(p.textSecondary), fontSize = 13.sp,
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                entries.forEach { e ->
                    item {
                        if (e.header != null) {
                            Text(e.header, Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp), color = Color(p.accent), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            val selected = e.value == chosen
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (selected) Color(p.selection) else Color.Transparent)
                                    .clickable(enabled = e.usable) { Production.setOverride(knob, e.value!!); onDismiss() }
                                    .padding(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    if (player.playingSample == e.sampleId && e.sampleId != null) "♪" else "▶",
                                    Modifier.clip(CircleShape).clickable(enabled = e.sample != null) {
                                        VoicePlayer.playSample(context, e.sampleId!!, e.sample!!)
                                    }.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = Color(p.accent).copy(alpha = if (e.sample != null) 1f else 0.3f), fontSize = 18.sp,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        e.name,
                                        color = Color(if (e.usable) p.text else p.textSecondary),
                                        fontSize = 16.sp, fontFamily = p.family(),
                                        fontWeight = if (selected) FontWeight.Bold else null,
                                    )
                                    val tags = listOfNotNull(if (e.value == savedValue) "saved setting" else null, e.note)
                                    if (tags.isNotEmpty()) Text(tags.joinToString(" · "), color = Color(p.textSecondary), fontSize = 12.sp)
                                }
                                if (selected) Text("✓", color = Color(p.accent), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("↺ Saved", enabled = chosen != savedValue) { Production.clearOverride(knob); onDismiss() }
                Spacer(Modifier.weight(1f))
                Pill("Cancel", onClick = onDismiss)
            }
        }
    }
}

/**
 * The Japanese reading: the kana VOICEVOX makes of the phrase, editable — to fix a misread kanji or
 * a wrong accent. Spoken instead of the text until the phrase changes or 「↺ From the text」.
 */
@Composable
private fun ReadingDialog(st: LangState, p: UiPrefs, onDismiss: () -> Unit) {
    var auto by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var field by remember { mutableStateOf(st.reading ?: "") }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.Default) { Production.autoReading() } }
            .onSuccess { auto = it; if (field.isEmpty()) field = it }
            .onFailure { failed = it.message ?: it.javaClass.simpleName }
    }
    HouseDialog(onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogTitle("Reading")
            Text(st.text.trim(), color = Color(p.text), fontSize = 15.sp, fontFamily = p.family(), maxLines = 4, overflow = TextOverflow.Ellipsis)
            when {
                failed != null -> Text("Could not read the phrase: $failed", color = Color(p.errorColor), fontSize = 13.sp)
                auto == null -> LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Color(p.accent), trackColor = Color(p.accent).copy(alpha = 0.2f))
            }
            OutlinedTextField(
                value = field, onValueChange = { field = it }, colors = houseFieldColors(), minLines = 2, maxLines = 6,
                textStyle = TextStyle(fontSize = 18.sp, color = Color(p.text)),
                label = { Text("Kana as VOICEVOX reads it") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Katakana. ' after a mora = the accent (the pitch falls after it) · / = new accent phrase · 、 = pause · _ before a mora = devoiced · ？ at the end = rising. " +
                    "Example: コンニチワ'/シロイ'クマ",
                color = Color(p.textSecondary), fontSize = 12.sp,
            )
            if (field.trim() != (auto ?: "").trim() && auto != null) {
                Text("Edited — this reading is spoken instead of the text.", color = Color(p.accent), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("↺ From the text", enabled = auto != null) { field = auto ?: ""; Production.setReading(null, auto) }
                Spacer(Modifier.weight(1f))
                Pill("Use", enabled = field.isNotBlank() && auto != null, main = true) { Production.setReading(field, auto); onDismiss() }
            }
            Pill("Cancel", onClick = onDismiss)
        }
    }
}

/** Confirm writing the dashboard's changes into the saved settings — the app's voice everywhere. */
@Composable
private fun MakeSavedDialog(lang: Lang, knobs: List<Knob>, saved: VoiceSettings, eff: VoiceSettings, p: UiPrefs, onDismiss: () -> Unit) {
    val context = LocalContext.current
    HouseDialog(onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogTitle("Make these the saved settings?")
            knobs.forEach { k ->
                Text("${k.label}: ${k.format(context, k.get(saved))} → ${k.format(context, k.get(eff))}", color = Color(p.text), fontSize = 15.sp, fontFamily = p.family())
            }
            Text(
                "The saved ${lang.tab} settings are the app's own voice: Android's text-to-speech and the sister apps speak with them too.",
                color = Color(p.textSecondary), fontSize = 13.sp,
            )
            Row(Modifier.fillMaxWidth()) {
                Pill("Cancel", onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                Pill("★ Make saved", main = true) { Production.makeSaved(lang); onDismiss() }
            }
        }
    }
}

/**
 * Name, format and directory, then Save — the directory and format are remembered for next time.
 * For the dashboard's phrase ([entry] null) an option splits it into sentences first; off, the
 * phrase is saved exactly as it stands. A recent phrase ([entry]) is saved as it was spoken.
 */
@Composable
private fun SaveDialog(lang: Lang, entry: HistoryEntry?, out: OutPrefs, p: UiPrefs, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(if (entry != null) Production.suggestedName(entry.text) else Production.suggestedName(lang)) }
    var format by remember { mutableStateOf(out.format) }
    var dir by remember { mutableStateOf(out.dir) }
    var picking by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(Split()) }
    val opus = remember { Production.usableFormat(OutFormat.OGG) == OutFormat.OGG }
    val sentences = remember(entry) { if (entry == null) Production.sentences(lang, Production.states.value.getValue(lang).text) else emptyList() }
    val hasReading = entry == null && lang == Lang.JA && Production.states.value.getValue(lang).reading != null
    HouseDialog(onDismiss) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogTitle(if (entry != null) "Save the recent phrase" else "Save the phrase")
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true, colors = houseFieldColors(),
                label = { Text("File name") },
                suffix = { Text(if (split.on && !split.joined) "_01.${format.ext} …" else ".${format.ext}") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutFormat.entries.forEach { f ->
                    Segment(f.label + if (f == OutFormat.OGG && f == format) " · ${out.bitrateKbps} kbps" else "", f == format, f != OutFormat.OGG || opus, p) { format = f }
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Color(p.accent).copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .clickable { picking = true }.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Directory — tap to change", color = Color(p.accent), fontSize = 12.sp)
                Text(dir, color = Color(p.text), fontSize = 14.sp)
            }
            if (entry == null && sentences.size > 1) {
                HouseCheckRow("Split into sentences (${sentences.size})", split.on) { split = split.copy(on = it) }
                if (split.on) {
                    Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Segment("A file each", !split.joined, true, p) { split = split.copy(joined = false) }
                        Segment("One joined file", split.joined, true, p) { split = split.copy(joined = true) }
                    }
                    if (split.joined) {
                        Text("Pause between sentences: ${split.pauseMs} ms", Modifier.padding(start = 12.dp), color = Color(p.text), fontSize = 14.sp)
                        Slider(
                            value = split.pauseMs.toFloat(), onValueChange = { split = split.copy(pauseMs = (it / 50).roundToInt() * 50) },
                            valueRange = 0f..2000f, steps = 39, modifier = Modifier.padding(start = 12.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                            ),
                        )
                    }
                    Text(
                        sentences.take(3).joinToString("\n") { "· $it" } + if (sentences.size > 3) "\n· …" else "",
                        Modifier.padding(start = 12.dp), color = Color(p.textSecondary), fontSize = 12.sp, fontFamily = p.family(),
                    )
                    if (hasReading) Text("The edited reading is for the whole phrase — split, each sentence is read from its text.", Modifier.padding(start = 12.dp), color = Color(p.errorColor), fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pill("Cancel", onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                Pill("⤓ Save", enabled = name.isNotBlank() && dir.isNotBlank(), main = true) {
                    Production.setOut { it.copy(dir = dir.trim().trimEnd('/'), format = format) }
                    if (entry != null) Production.saveHistory(entry, dir, name, format)
                    else Production.save(lang, dir, name, format, split)
                    onDismiss()
                }
            }
        }
    }
    if (picking) {
        DirectoryDialog("Save directory", dir, onDismiss = { picking = false }) { dir = it.trim().trimEnd('/'); picking = false }
    }
}

/** One segment of a selector (not an action): filled when chosen. */
@Composable
private fun Segment(label: String, on: Boolean, usable: Boolean, p: UiPrefs, onClick: () -> Unit) {
    val ink = Color(p.accent).copy(alpha = if (usable) 1f else 0.38f)
    Text(
        label,
        Modifier.clip(RoundedCornerShape(50))
            .background(if (on) Color(p.accent) else Color.Transparent)
            .border(1.25.dp, ink, RoundedCornerShape(50))
            .clickable(enabled = usable, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        color = if (on) Color(p.background) else ink,
        fontWeight = if (on) FontWeight.Bold else null, fontSize = 14.sp,
    )
}
