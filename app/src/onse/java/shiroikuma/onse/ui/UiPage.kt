package shiroikuma.onse.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import shiroikuma.onse.automation.AutomationAuth
import shiroikuma.onse.voice.VoiceCatalog
import shiroikuma.onse.voice.VoicePlayer
import shiroikuma.onse.voice.VoiceSettings
import shiroikuma.onse.voice.VoiceSettingsStore
import java.io.File

/**
 * 白い熊 音声 UI — every configurable item of the fork, on one page in the kxkb UI-page format:
 * Export / Import first, then Voice, Colours, Typography, Borders & shape, Main screen, Settings
 * page, Reset. Every change applies live and every group carries a preview of what it controls.
 */
@Composable
fun UiPage(appVersion: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p by UiStore.prefs.collectAsState()
    val voice by VoiceSettingsStore.settings.collectAsState()

    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var fontPicker by remember { mutableStateOf(false) }
    var fontsRefresh by remember { mutableIntStateOf(0) }
    var folderDialog by remember { mutableStateOf(false) }
    var eximPanel by remember { mutableStateOf(false) }
    var eximRefresh by remember { mutableIntStateOf(0) }
    var resetConfirm by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var automationEnabled by remember { mutableStateOf(AutomationAuth.enabled(context)) }
    var automationRequireToken by remember { mutableStateOf(AutomationAuth.requireToken(context)) }
    var automationToken by remember { mutableStateOf(AutomationAuth.token(context)) }

    // Queried on opening the page, on every return to it, and after any change.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { eximRefresh++ } }
    val status by produceState<Pair<String?, Backup.Latest?>>(null to null, eximRefresh) {
        value = withContext(Dispatchers.IO) { Backup.dir(context) to Backup.latest(context) }
    }

    val fontImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val stored = UiStore.importFont(uri)
            fontsRefresh++
            toast = if (stored != null) "Imported \"$stored\"" else "Couldn't import font (need .ttf or .otf)"
        }
    }

    Column(Modifier.fillMaxSize().background(Color(p.background))) {
        TopBar("白い熊 音声 UI", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {

            // ---- Export / Import — the first separated section ---------------------------------
            item { SectionHeader("Export / Import", p, first = true) }
            item {
                val dir = status.first
                RowScaffold(0, p, onClick = { folderDialog = true }) {
                    TitledText(
                        "Backup directory (tap to choose)",
                        dir ?: "Not set — tap to choose a directory",
                        Modifier.weight(1f),
                        summaryColor = if (dir == null) Color(p.errorColor) else MaterialTheme.colorScheme.primary,
                        summaryBold = true,
                    )
                }
            }
            item {
                val (dir, latest) = status
                val (msg, warn) = when {
                    dir == null -> "Set a backup directory first." to true
                    !Backup.hasAllFilesAccess() -> "All-files access is required to read and write the backup directory." to true
                    latest == null -> "No backup found in this directory yet." to true
                    else -> "Last backup: ${latest.whenText} (${Backup.humanSize(latest.size)})" to false
                }
                NoteText(msg, 0, p, if (warn) Color(p.errorColor) else MaterialTheme.colorScheme.primary)
            }
            item {
                RowScaffold(0, p, onClick = { eximPanel = true }) {
                    TitledText(
                        "Export / Import…",
                        "Back up or restore everything settable — this UI, the voice settings and the list of installed voices — as one ZIP.",
                        Modifier.weight(1f),
                    )
                }
            }

            // Automation lives INSIDE this section, below the export rows (family contract v2 §2).
            item {
                SwitchRow(
                    0, p, "Automation export",
                    "Let sister apps (白い熊 自由作業盤's 保存復元) trigger this app's export, and let 白い熊 応用管理 back its data up and put it back. On by default — turn it off to close this app to automation entirely.",
                    automationEnabled,
                ) { automationEnabled = it; AutomationAuth.setEnabled(context, it) }
            }
            item {
                SwitchRow(
                    0, p, "Use authorization token?",
                    "Off: any sister app may drive the automation. On: a caller must also present the token below. Either way the data door checks the calling app's package, uid and signing certificate.",
                    automationRequireToken,
                ) { automationRequireToken = it; AutomationAuth.setRequireToken(context, it) }
            }
            if (automationRequireToken) {
                item {
                    val clipboard = LocalClipboardManager.current
                    RowScaffold(0, p, onClick = {
                        clipboard.setText(AnnotatedString(automationToken))
                        toast = "Automation token copied"
                    }) {
                        TitledText("Automation token (tap to copy)", AutomationAuth.abbreviated(automationToken), Modifier.weight(1f), summaryBold = true)
                        Text(
                            "Regenerate",
                            Modifier.clickable {
                                automationToken = AutomationAuth.regenerate(context)
                                toast = "Automation token regenerated — update pasted copies"
                            }.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                    }
                }
            }

            // ---- Voice -------------------------------------------------------------------------
            item { SectionHeader("Voice", p) }
            item {
                val label = VoiceCatalog.get(context).choiceFor(voice.styleId)?.label ?: "—"
                PreviewCard(0, p) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = Color(p.text), fontWeight = FontWeight.Bold, fontFamily = p.family())
                            Text(
                                "speed ${pct(voice.speedPct)} · pitch ${signed(voice.pitchPct)} · intonation ${pct(voice.intonationPct)}",
                                color = Color(p.textSecondary), fontSize = p.labelSizeSp.sp, fontFamily = p.family(),
                            )
                        }
                        Pill("▶ Listen") { VoicePlayer.speak(context, VoiceCatalog.get(context).sampleText, voice.styleId) }
                    }
                }
            }
            item { NoteText("The voice itself is chosen on the main screen, where every VOICEVOX voice can be heard and downloaded.", 0, p) }
            item {
                SliderRow(0, p, "Speed", voice.speedPct, pct(voice.speedPct), 50..200, 5) { v ->
                    VoiceSettingsStore.update { it.copy(speedPct = v) }
                }
            }
            item {
                SliderRow(0, p, "Pitch", voice.pitchPct, signed(voice.pitchPct), -15..15) { v ->
                    VoiceSettingsStore.update { it.copy(pitchPct = v) }
                }
            }
            item {
                SliderRow(0, p, "Intonation", voice.intonationPct, pct(voice.intonationPct), 0..200, 5) { v ->
                    VoiceSettingsStore.update { it.copy(intonationPct = v) }
                }
            }
            item {
                SliderRow(0, p, "Volume", voice.volumePct, pct(voice.volumePct), 0..200, 5) { v ->
                    VoiceSettingsStore.update { it.copy(volumePct = v) }
                }
            }
            item { SubHeader("Pauses", p) }
            item {
                SliderRow(1, p, "Before speaking", voice.prePauseMs, ms(voice.prePauseMs), 0..1000, 10) { v ->
                    VoiceSettingsStore.update { it.copy(prePauseMs = v) }
                }
            }
            item {
                SliderRow(1, p, "After speaking", voice.postPauseMs, ms(voice.postPauseMs), 0..1000, 10) { v ->
                    VoiceSettingsStore.update { it.copy(postPauseMs = v) }
                }
            }
            item {
                SwitchRow(
                    0, p, "Follow the calling app's speech rate",
                    "Apps reading through Android's text-to-speech can speed up or slow down on top of the speed above.",
                    voice.followSystemRate,
                ) { v -> VoiceSettingsStore.update { it.copy(followSystemRate = v) } }
            }
            item {
                RowScaffold(0, p, onClick = {
                    VoiceSettingsStore.update { VoiceSettings(styleId = it.styleId) }
                    toast = "Voice knobs back to the 白い熊 defaults"
                }) {
                    TitledText("Restore the 白い熊 voice defaults", "Speed 115 %, pitch 0, intonation 100 %, volume 100 %, pauses 100 ms.", Modifier.weight(1f))
                }
            }

            // ---- Colours -----------------------------------------------------------------------
            item { SectionHeader("Colours", p) }
            item { PreviewCard(0, p) { ColourPreview(p) } }
            item { SubHeader("Base", p) }
            item { ColorRow(1, p, ColorTarget.Background) { colorTarget = it } }
            item { ColorRow(1, p, ColorTarget.Surface) { colorTarget = it } }
            item { ColorRow(1, p, ColorTarget.Accent) { colorTarget = it } }
            item { SubHeader("Text", p) }
            item { ColorRow(1, p, ColorTarget.Text) { colorTarget = it } }
            item { ColorRow(1, p, ColorTarget.TextSecondary) { colorTarget = it } }
            item { SubHeader("Lines & fills", p) }
            item { ColorRow(1, p, ColorTarget.Border) { colorTarget = it } }
            item { ColorRow(1, p, ColorTarget.Divider) { colorTarget = it } }
            item { ColorRow(1, p, ColorTarget.Selection) { colorTarget = it } }
            item { ColorRow(1, p, ColorTarget.Error) { colorTarget = it } }

            // ---- Typography --------------------------------------------------------------------
            item { SectionHeader("Typography", p) }
            item { PreviewCard(0, p) { TypographyPreview(p) } }
            item {
                RowScaffold(0, p, onClick = { fontPicker = true }) {
                    Text("Font", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        UiStore.displayNameFor(p.fontFileName),
                        fontFamily = UiStore.fontFamily(p.fontFileName) ?: FontFamily.Default,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                SliderRow(0, p, "Weight", p.fontWeight, if (p.fontWeight == 0) "Per style" else "${p.fontWeight}", 0..900, 100) { v ->
                    UiStore.update { it.copy(fontWeight = if (v in 1..99) 100 else v) }
                }
            }
            item {
                SliderRow(0, p, "Text scale", p.fontScalePct, "${p.fontScalePct} %", UiPrefs.FONT_SCALE_MIN..UiPrefs.FONT_SCALE_MAX, 5) { v ->
                    UiStore.update { it.copy(fontScalePct = v) }
                }
            }
            item { SubHeader("Sizes", p) }
            item {
                SliderRow(1, p, "Titles", p.titleSizeSp, "${p.titleSizeSp} sp", UiPrefs.TEXT_SIZE_MIN..UiPrefs.TEXT_SIZE_MAX) { v ->
                    UiStore.update { it.copy(titleSizeSp = v) }
                }
            }
            item {
                SliderRow(1, p, "Body text", p.bodySizeSp, "${p.bodySizeSp} sp", UiPrefs.TEXT_SIZE_MIN..UiPrefs.TEXT_SIZE_MAX) { v ->
                    UiStore.update { it.copy(bodySizeSp = v) }
                }
            }
            item {
                SliderRow(1, p, "Descriptions & labels", p.labelSizeSp, "${p.labelSizeSp} sp", UiPrefs.TEXT_SIZE_MIN..UiPrefs.TEXT_SIZE_MAX) { v ->
                    UiStore.update { it.copy(labelSizeSp = v) }
                }
            }

            // ---- Borders & shape ---------------------------------------------------------------
            item { SectionHeader("Borders & shape", p) }
            item { PreviewCard(0, p) { ShapePreview(p) } }
            item { SubHeader("Cards & boxes", p) }
            item {
                SliderRow(1, p, "Border width", p.borderWidthDp, dp(p.borderWidthDp), 0..UiPrefs.BORDER_WIDTH_MAX) { v ->
                    UiStore.update { it.copy(borderWidthDp = v) }
                }
            }
            item {
                SliderRow(1, p, "Corner roundness", p.cornerRadiusDp, dp(p.cornerRadiusDp), 0..UiPrefs.CORNER_RADIUS_MAX) { v ->
                    UiStore.update { it.copy(cornerRadiusDp = v) }
                }
            }
            item {
                SliderRow(1, p, "Divider thickness", p.dividerThicknessDp, dp(p.dividerThicknessDp), 0..UiPrefs.DIVIDER_MAX) { v ->
                    UiStore.update { it.copy(dividerThicknessDp = v) }
                }
            }
            item { SubHeader("Buttons", p) }
            item {
                SliderRow(1, p, "Button border", p.buttonBorderWidthDp, dp(p.buttonBorderWidthDp), 0..UiPrefs.BORDER_WIDTH_MAX) { v ->
                    UiStore.update { it.copy(buttonBorderWidthDp = v) }
                }
            }
            item {
                SliderRow(1, p, "Button roundness", p.buttonCornerPct, if (p.buttonCornerPct >= 50) "Pill" else "${p.buttonCornerPct} %", 0..50) { v ->
                    UiStore.update { it.copy(buttonCornerPct = v) }
                }
            }

            // ---- Main screen -------------------------------------------------------------------
            item { SectionHeader("Main screen", p) }
            item { PreviewCard(0, p) { MainScreenPreview(p) } }
            item {
                SliderRow(0, p, "Speak button height", p.speakButtonHeightDp, dp(p.speakButtonHeightDp), UiPrefs.BUTTON_HEIGHT_MIN..UiPrefs.BUTTON_HEIGHT_MAX, 2) { v ->
                    UiStore.update { it.copy(speakButtonHeightDp = v) }
                }
            }
            item {
                SliderRow(0, p, "Try-it text size", p.tryTextSizeSp, "${p.tryTextSizeSp} sp", UiPrefs.TEXT_SIZE_MIN..UiPrefs.TEXT_SIZE_MAX) { v ->
                    UiStore.update { it.copy(tryTextSizeSp = v) }
                }
            }
            item {
                SliderRow(0, p, "Voice list row padding", p.voiceRowPadDp, dp(p.voiceRowPadDp), 0..UiPrefs.PAD_MAX) { v ->
                    UiStore.update { it.copy(voiceRowPadDp = v) }
                }
            }
            item {
                SliderRow(0, p, "Card padding", p.cardPadDp, dp(p.cardPadDp), 0..UiPrefs.PAD_MAX) { v ->
                    UiStore.update { it.copy(cardPadDp = v) }
                }
            }

            // ---- Settings page -----------------------------------------------------------------
            item { SectionHeader("Settings page", p) }
            item { NoteText("These shape this very page — heading size and underline, how far each level indents, how tight the rows sit.", 0, p) }
            item {
                SliderRow(0, p, "Heading size", p.sectionTitleSizeSp, "${p.sectionTitleSizeSp} sp", UiPrefs.SECTION_TITLE_MIN..UiPrefs.SECTION_TITLE_MAX) { v ->
                    UiStore.update { it.copy(sectionTitleSizeSp = v) }
                }
            }
            item {
                SliderRow(0, p, "Heading underline", p.sectionUnderlineTenthDp, tenths(p.sectionUnderlineTenthDp), 0..UiPrefs.UNDERLINE_MAX_TENTHS, 5) { v ->
                    UiStore.update { it.copy(sectionUnderlineTenthDp = v) }
                }
            }
            item {
                SliderRow(0, p, "Heading indent", p.indentBaseDp, dp(p.indentBaseDp), 0..UiPrefs.INDENT_MAX) { v ->
                    UiStore.update { it.copy(indentBaseDp = v) }
                }
            }
            item {
                SliderRow(0, p, "Indent per level", p.indentStepDp, dp(p.indentStepDp), 0..UiPrefs.INDENT_MAX / 2) { v ->
                    UiStore.update { it.copy(indentStepDp = v) }
                }
            }
            item {
                SliderRow(0, p, "Row padding", p.settingsRowVPadDp, dp(p.settingsRowVPadDp), 0..UiPrefs.PAD_MAX) { v ->
                    UiStore.update { it.copy(settingsRowVPadDp = v) }
                }
            }
            item {
                SliderRow(0, p, "Gap between sections", p.sectionGapDp, dp(p.sectionGapDp), 0..UiPrefs.PAD_MAX * 2) { v ->
                    UiStore.update { it.copy(sectionGapDp = v) }
                }
            }

            // ---- Reset -------------------------------------------------------------------------
            item { SectionHeader("Reset", p) }
            item {
                RowScaffold(0, p, onClick = { resetConfirm = true }) {
                    TitledText("Restore the black-yellow defaults", "Every colour, font and size on this page back to the house look.", Modifier.weight(1f))
                }
            }
        }
    }

    // ---- dialogs ---------------------------------------------------------------------------------

    colorTarget?.let { t ->
        ColorPickerDialog(t.label, t.get(p), p.recentColors, onDismiss = { colorTarget = null }) { argb ->
            UiStore.update { t.set(it, argb) }
            UiStore.rememberColor(argb)
            colorTarget = null
        }
    }
    if (fontPicker) {
        val fonts = remember(fontsRefresh) { UiStore.availableFonts() }
        FontPickerDialog(
            current = p.fontFileName, fonts = fonts, onDismiss = { fontPicker = false },
            onPick = { f -> UiStore.update { it.copy(fontFileName = f) }; fontPicker = false },
            onAddFont = { fontImport.launch(arrayOf("*/*")) },
            onDelete = { f -> UiStore.deleteFont(f); fontsRefresh++ },
        )
    }
    if (folderDialog) {
        FolderDialog(Backup.dir(context), onDismiss = { folderDialog = false }) { path ->
            Backup.setDir(context, path)
            folderDialog = false
            eximRefresh++
        }
    }
    if (resetConfirm) {
        HouseDialog({ resetConfirm = false }) {
            Column(Modifier.padding(20.dp)) {
                DialogTitle("Restore defaults?")
                Text("Every colour, font and size on this page returns to the black-yellow default.", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Pill("Cancel") { resetConfirm = false }
                    Spacer(Modifier.width(8.dp))
                    Pill("Restore") { UiStore.resetToDefaults(); resetConfirm = false }
                }
            }
        }
    }
    if (eximPanel) {
        ExportImportPanel(
            appVersion = appVersion,
            onDismiss = { eximPanel = false; eximRefresh++ },
            onCloseChain = { eximPanel = false; onBack() },
            onDirChanged = { eximRefresh++ },
        )
    }
    toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(2200); toast = null }
        Box(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                msg,
                Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.background)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

private fun pct(v: Int) = "$v %"
private fun signed(v: Int) = if (v > 0) "+${v / 100.0}" else "${v / 100.0}"
private fun ms(v: Int) = "$v ms"
private fun dp(v: Int) = if (v == 0) "Off (0 dp)" else "$v dp"
private fun tenths(v: Int) = if (v == 0) "Off (0 dp)" else "${v / 10.0} dp"

// ---- colour rows --------------------------------------------------------------------------------

private enum class ColorTarget(val label: String, val default: Int, val get: (UiPrefs) -> Int, val set: (UiPrefs, Int) -> UiPrefs) {
    Background("Background", UiPrefs.BLACK, { it.background }, { p, v -> p.copy(background = v) }),
    Surface("Surface / cards", UiPrefs.NEAR_BLACK, { it.surface }, { p, v -> p.copy(surface = v) }),
    Accent("Accent (buttons, headings)", UiPrefs.YELLOW, { it.accent }, { p, v -> p.copy(accent = v) }),
    Text("Primary text", UiPrefs.YELLOW, { it.text }, { p, v -> p.copy(text = v) }),
    TextSecondary("Secondary text", UiPrefs.YELLOW_DIM, { it.textSecondary }, { p, v -> p.copy(textSecondary = v) }),
    Border("Border", UiPrefs.YELLOW, { it.border }, { p, v -> p.copy(border = v) }),
    Divider("Divider", UiPrefs.YELLOW_FAINT, { it.divider }, { p, v -> p.copy(divider = v) }),
    Selection("Selection fill", UiPrefs.YELLOW_SELECT, { it.selection }, { p, v -> p.copy(selection = v) }),
    Error("Error / warning", UiPrefs.ERROR_RED, { it.errorColor }, { p, v -> p.copy(errorColor = v) }),
}

@Composable
private fun ColorRow(level: Int, p: UiPrefs, t: ColorTarget, onPick: (ColorTarget) -> Unit) {
    val value = t.get(p)
    RowScaffold(level, p, onClick = { onPick(t) }) {
        TitledText(t.label, if (value == t.default) "Default — ${hex8(value)}" else hex8(value), Modifier.weight(1f))
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.15f))
                .background(Color(value)).border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
        )
    }
}

// ---- previews -----------------------------------------------------------------------------------

@Composable
private fun ColourPreview(p: UiPrefs) {
    Column(Modifier.background(Color(p.background)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Primary text — 白い熊 音声", color = Color(p.text), fontSize = p.bodySizeSp.sp, fontFamily = p.family())
        Text("Secondary text — a description", color = Color(p.textSecondary), fontSize = p.labelSizeSp.sp, fontFamily = p.family())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(Color(p.accent)))
            Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(p.cornerRadiusDp.dp)).background(Color(p.selection)))
            Text("Not set", color = Color(p.errorColor), fontWeight = FontWeight.Bold, fontSize = p.labelSizeSp.sp)
        }
        Box(Modifier.fillMaxWidth().height(p.dividerThicknessDp.coerceAtLeast(1).dp).background(Color(p.divider)))
    }
}

@Composable
private fun TypographyPreview(p: UiPrefs) {
    val family = p.family()
    val weight = p.fontWeight.takeIf { it in 100..900 }?.let(::FontWeight)
    val s = p.fontScalePct / 100f
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Title — 白い熊 音声", color = Color(p.text), fontSize = (p.titleSizeSp * s).sp, fontFamily = family, fontWeight = weight ?: FontWeight.Bold)
        Text("Body — こんにちは。これは声の見本です。", color = Color(p.text), fontSize = (p.bodySizeSp * s).sp, fontFamily = family, fontWeight = weight)
        Text("Label — No.7（読み聞かせ）", color = Color(p.textSecondary), fontSize = (p.labelSizeSp * s).sp, fontFamily = family, fontWeight = weight)
    }
}

@Composable
private fun ShapePreview(p: UiPrefs) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(p.cornerRadiusDp.dp)
        Box(
            Modifier.size(52.dp).clip(shape).background(Color(p.background))
                .then(if (p.borderWidthDp > 0) Modifier.border(p.borderWidthDp.dp, Color(p.border), shape) else Modifier),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Button") {}
            Box(Modifier.fillMaxWidth().height(p.dividerThicknessDp.coerceAtLeast(0).dp).background(Color(p.divider)))
        }
    }
}

@Composable
private fun MainScreenPreview(p: UiPrefs) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("今日はいい天気ですね。", color = Color(p.text), fontSize = p.tryTextSizeSp.sp, fontFamily = p.family())
        Box(
            Modifier.fillMaxWidth().height(p.speakButtonHeightDp.dp).clip(RoundedCornerShape(p.buttonCornerPct.coerceIn(0, 50)))
                .border(p.buttonBorderWidthDp.dp, Color(p.accent), RoundedCornerShape(p.buttonCornerPct.coerceIn(0, 50))),
            contentAlignment = Alignment.Center,
        ) { Text("▶ 話す", color = Color(p.accent), fontSize = (p.speakButtonHeightDp / 3).coerceIn(12, 48).sp, fontWeight = FontWeight.Bold) }
        repeat(2) { i ->
            Text(
                if (i == 0) "▶  No.7（読み聞かせ）  ✓" else "▶  四国めたん（ノーマル）  ⤓ 59 MB",
                Modifier.padding(vertical = p.voiceRowPadDp.dp),
                color = Color(if (i == 0) p.accent else p.text), fontSize = p.bodySizeSp.sp, fontFamily = p.family(),
            )
        }
    }
}

// ---- top bar --------------------------------------------------------------------------------------

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    val p by UiStore.prefs.collectAsState()
    Row(
        Modifier.fillMaxWidth().background(Color(p.background)).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Text(
                "←",
                Modifier.clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            )
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(
            title,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (p.titleSizeSp + 2).sp,
            fontFamily = p.family(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
    Box(Modifier.fillMaxWidth().height(p.dividerThicknessDp.dp).background(Color(p.border)))
}

// ---- Export / Import panel (Kōjiki sheet format, ArcaneChat button line) ---------------------------

private sealed interface EximInfo {
    data class ExportDone(val message: String) : EximInfo
    data class ImportDone(val lines: List<String>) : EximInfo
    data class Failure(val message: String) : EximInfo
}

@Composable
private fun ExportImportPanel(appVersion: String, onDismiss: () -> Unit, onCloseChain: () -> Unit, onDirChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p by UiStore.prefs.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<EximInfo?>(null) }
    var folderDialog by remember { mutableStateOf(false) }
    var importList by remember { mutableStateOf<List<File>?>(null) }
    val selected = remember { mutableStateMapOf<Backup.Cat, Boolean>().apply { Backup.Cat.entries.forEach { put(it, it.defaultOn) } } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ } }
    val status by produceState<Pair<String?, Backup.Latest?>>(null to null, refresh) {
        value = withContext(Dispatchers.IO) { Backup.dir(context) to Backup.latest(context) }
    }
    val hasAccess = remember(refresh) { Backup.hasAllFilesAccess() }
    fun cats() = selected.filterValues { it }.keys

    fun ready(): Boolean {
        if (!Backup.hasAllFilesAccess()) { requestAllFilesAccess(context); return false }
        if (Backup.dir(context) == null) { folderDialog = true; return false }
        return true
    }

    HouseDialog(onDismiss = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 20.dp)) {
            Text("Export / Import", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp))
            Text(
                "Pick the parts to back up, choose a folder once, then Export or Import. Backups are plain .zip files you can copy anywhere.",
                fontSize = 13.sp, color = Color(p.textSecondary).copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 10.dp),
            )
            if (!hasAccess) {
                Text("All-files access is required to read and write the backup folder.", fontSize = 13.sp, color = Color(p.errorColor))
                Pill("Grant all-files access", modifier = Modifier.padding(top = 6.dp)) { requestAllFilesAccess(context) }
                Spacer(Modifier.height(6.dp))
            }
            val (dir, latest) = status
            Column(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    .clickable(enabled = !busy) { folderDialog = true }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("Backup folder", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(dir ?: "Not set — tap to choose", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (dir == null) Color(p.errorColor) else MaterialTheme.colorScheme.primary)
            }
            val (msg, warn) = when {
                dir == null -> "Set a backup folder first." to true
                latest == null -> "No backup found in this folder yet." to true
                else -> "Last backup: ${latest.whenText}" to false
            }
            Text(msg, fontSize = 14.sp, color = if (warn) Color(p.errorColor) else Color(p.textSecondary).copy(alpha = 0.8f), modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
            val all = Backup.Cat.entries.all { selected[it] == true }
            HouseCheckRow("Select all", all, bold = true) { c -> Backup.Cat.entries.forEach { selected[it] = c } }
            Backup.Cat.topLevel.forEach { cat ->
                HouseCheckRow(cat.label, selected[cat] == true) { c ->
                    selected[cat] = c
                    Backup.Cat.childrenOf(cat).forEach { selected[it] = c }
                }
                Backup.Cat.childrenOf(cat).forEach { child ->
                    HouseCheckRow(child.label, selected[child] == true, indent = 1) { selected[child] = it }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Cancel", enabled = !busy, onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                Pill("Import", enabled = !busy) {
                    if (cats().isEmpty()) { info = EximInfo.Failure("No categories selected."); return@Pill }
                    if (!ready()) return@Pill
                    val list = Backup.backups(Backup.dir(context))
                    if (list.isEmpty()) info = EximInfo.Failure("No backups in this folder to import.") else importList = list
                }
                Spacer(Modifier.width(8.dp))
                Pill("Export", enabled = !busy) {
                    if (cats().isEmpty()) { info = EximInfo.Failure("No categories selected."); return@Pill }
                    if (!ready()) return@Pill
                    busy = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { Backup.exportToDirectory(context, cats(), appVersion) } }
                            .onSuccess { r ->
                                refresh++; onDirChanged()
                                info = EximInfo.ExportDone("Exported ${r.categories} categories (${Backup.humanSize(r.file.length())}).\n\n${r.file.name}")
                            }
                            .onFailure { info = EximInfo.Failure("Export failed: ${it.message ?: "unknown error"}") }
                        busy = false
                    }
                }
            }
        }
    }

    if (folderDialog) {
        FolderDialog(Backup.dir(context), onDismiss = { folderDialog = false }) { path ->
            Backup.setDir(context, path); folderDialog = false; refresh++; onDirChanged()
        }
    }
    importList?.let { list ->
        HouseDialog({ importList = null }) {
            Column(Modifier.padding(20.dp)) {
                DialogTitle("Choose a backup to import")
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    list.forEach { f ->
                        Text(
                            f.name,
                            Modifier.fillMaxWidth().clickable {
                                importList = null
                                busy = true
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { Backup.import(context, f, cats()) } }
                                        .onSuccess { info = EximInfo.ImportDone(it.lines) }
                                        .onFailure { info = EximInfo.Failure("Import failed: ${it.message ?: "unknown error"}") }
                                    busy = false
                                }
                            }.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary, fontSize = 15.sp,
                        )
                    }
                }
                Pill("Cancel") { importList = null }
            }
        }
    }
    when (val cur = info) {
        is EximInfo.ExportDone -> InfoDialog("✓ Export — 100% success", cur.message) {
            // OK closes the info dialog, the panel beneath it AND the UI page.
            Pill("OK") { info = null; onCloseChain() }
        }
        is EximInfo.ImportDone -> InfoDialog("✓ Import — 100% success", "Restored:\n\n${cur.lines.joinToString("\n")}\n\nRestart to apply everything.") {
            Pill("Later") { info = null; onCloseChain() }
            Spacer(Modifier.width(10.dp))
            Pill("Restart now") { restartApp(context) }
        }
        // A failure leaves the panel open underneath — only the info dialog closes.
        is EximInfo.Failure -> InfoDialog("Export / Import", cur.message, warn = true) { Pill("OK") { info = null } }
        null -> Unit
    }
}

@Composable
private fun InfoDialog(title: String, body: String, warn: Boolean = false, buttons: @Composable () -> Unit) {
    HouseDialog(onDismiss = {}, dismissable = false) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 16.dp)) {
            DialogTitle(title, warn)
            Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { buttons() }
        }
    }
}

fun requestAllFilesAccess(context: Context) {
    val pkg = Uri.parse("package:${context.packageName}")
    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)) }
        .recoverCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
}

/** Kōjiki's restart: relaunch the launcher activity as a fresh task, then exit this process. */
private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
