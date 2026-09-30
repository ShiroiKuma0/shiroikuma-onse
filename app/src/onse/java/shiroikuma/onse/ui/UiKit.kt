package shiroikuma.onse.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlin.math.roundToInt

/*
 * The house page kit — the kxkb UI page's geometry in Compose:
 *   section heading  at indentBase                 (36 dp)  20 sp bold, 2.5 dp text-wide underline,
 *                                                            a 1 px full-width rule above it
 *   sub-heading      at indentBase + step          (54 dp)  17 sp bold, 1.5 dp underline
 *   level-1 item     at indentBase + 2 · step      (72 dp)
 *   level-2 item     at indentBase + 3 · step      (90 dp)
 * Rows are tight (5 dp); the only real breathing space is the gap before a top-level section.
 */

fun rowIndent(level: Int, p: UiPrefs): Dp = (p.indentBaseDp + (level + 1) * p.indentStepDp).dp
fun subIndent(p: UiPrefs): Dp = (p.indentBaseDp + p.indentStepDp).dp

@Composable
fun SectionHeader(title: String, p: UiPrefs, first: Boolean = false) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(top = if (first) 2.dp else p.sectionGapDp.dp)) {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(p.border)))
        Column(
            Modifier
                .padding(start = p.indentBaseDp.dp, top = if (first) 12.dp else 8.dp, end = 16.dp, bottom = 2.dp)
                .width(IntrinsicSize.Max),
        ) {
            Text(
                title,
                fontSize = p.sectionTitleSizeSp.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                fontFamily = p.family(),
                maxLines = 1,
                softWrap = false,
            )
            if (p.sectionUnderlineTenthDp > 0) {
                Box(
                    Modifier.padding(top = 2.dp).fillMaxWidth()
                        .height((p.sectionUnderlineTenthDp / 10f).dp).background(accent),
                )
            }
        }
    }
}

@Composable
fun SubHeader(title: String, p: UiPrefs) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.padding(start = subIndent(p), top = 10.dp, end = 16.dp, bottom = 2.dp).width(IntrinsicSize.Max),
    ) {
        Text(
            title,
            fontSize = (p.sectionTitleSizeSp - 3).coerceAtLeast(10).sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            fontFamily = p.family(),
            maxLines = 1,
            softWrap = false,
        )
        if (p.sectionUnderlineTenthDp > 0) {
            Box(
                Modifier.padding(top = 2.dp).fillMaxWidth()
                    .height((p.sectionUnderlineTenthDp * 0.6f / 10f).coerceAtLeast(0.5f).dp).background(accent),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScaffold(
    level: Int,
    p: UiPrefs,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val base = Modifier.fillMaxWidth()
    val clickable = when {
        onClick != null || onLongClick != null -> base.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
        else -> base
    }
    Row(
        clickable.padding(start = rowIndent(level, p), end = 16.dp, top = p.settingsRowVPadDp.dp, bottom = p.settingsRowVPadDp.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun TitledText(title: String, summary: String?, modifier: Modifier = Modifier, summaryColor: Color? = null, summaryBold: Boolean = false) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (summaryBold) FontWeight.Bold else null,
                color = summaryColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun NoteText(text: String, level: Int, p: UiPrefs, color: Color? = null) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = rowIndent(level, p), end = 16.dp, top = 1.dp, bottom = 3.dp),
    )
}

@Composable
fun SliderRow(
    level: Int,
    p: UiPrefs,
    label: String,
    value: Int,
    valueText: String,
    range: IntRange,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = rowIndent(level, p), end = 16.dp, top = 2.dp, bottom = 0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                valueText,
                Modifier.width(IntrinsicSize.Max).padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
        }
        val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { v -> onChange(((v - range.first) / step).roundToInt() * step + range.first) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (steps in 1..200) steps else 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().height(26.dp),
        )
    }
}

@Composable
fun SwitchRow(level: Int, p: UiPrefs, label: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowScaffold(level, p, onClick = { onChange(!checked) }) {
        TitledText(label, description, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.primary,
                uncheckedTrackColor = MaterialTheme.colorScheme.background,
                uncheckedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/** A bordered box holding a live preview of whatever the section controls. */
@Composable
fun PreviewCard(level: Int, p: UiPrefs, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(p.cornerRadiusDp.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = rowIndent(level, p), end = 16.dp, top = 3.dp, bottom = 3.dp)
            .clip(shape)
            .background(Color(p.surface))
            .then(if (p.borderWidthDp > 0) Modifier.border(p.borderWidthDp.dp, Color(p.border), shape) else Modifier)
            .padding(p.cardPadDp.dp),
    ) { content() }
}

/** ArcaneChat-style pill: black fill, accent stroke, accent text, fully rounded by default. */
@Composable
fun Pill(label: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = UiStore.prefs.value
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(p.buttonCornerPct.coerceIn(0, 50)),
        border = BorderStroke(
            (p.buttonBorderWidthDp.toFloat() * 0.75f).dp,
            MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
    ) { Text(label, maxLines = 1) }
}

/** Black box, yellow 2 dp border, 16 dp corners — every dialog of the app. */
@Composable
fun HouseDialog(
    onDismiss: () -> Unit,
    dismissable: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = dismissable, dismissOnClickOutside = dismissable),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) { content() }
    }
}

@Composable
fun DialogTitle(text: String, warn: Boolean = false) {
    Text(
        text,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun houseFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
fun HouseCheckRow(label: String, checked: Boolean, bold: Boolean = false, indent: Int = 0, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(start = (indent * 22).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.background,
            ),
        )
        Text(label, fontSize = 15.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
    }
}

fun hex8(argb: Int): String = "#%08X".format(argb)

// ---- colour picker ----------------------------------------------------------------------------

/**
 * RGBA picker: one-click boxes on top, prefilled with previously applied colours, then a live
 * preview with the hex value, then the four channel sliders.
 */
@Composable
fun ColorPickerDialog(title: String, initial: Int, swatches: List<Int>, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var a by remember { mutableIntStateOf((initial ushr 24) and 0xFF) }
    var r by remember { mutableIntStateOf((initial ushr 16) and 0xFF) }
    var g by remember { mutableIntStateOf((initial ushr 8) and 0xFF) }
    var b by remember { mutableIntStateOf(initial and 0xFF) }
    val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun load(c: Int) {
        a = (c ushr 24) and 0xFF; r = (c ushr 16) and 0xFF; g = (c ushr 8) and 0xFF; b = c and 0xFF
    }
    HouseDialog(onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogTitle(title)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                swatches.forEach { s ->
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.15f)).background(Color(s))
                            .border(if (s == argb) 3.dp else 1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .clickable { load(s) },
                    )
                }
            }
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.15f)).background(Color(argb))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(hex8(argb), fontSize = 16.sp, color = if (luminance < 128 || a < 128) Color.White else Color.Black)
            }
            Channel("A", a) { a = it }
            Channel("R", r) { r = it }
            Channel("G", g) { g = it }
            Channel("B", b) { b = it }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                Pill("Cancel", onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                Pill("Apply") { onConfirm(argb) }
            }
        }
    }
}

@Composable
private fun Channel(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(22.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.weight(1f).height(26.dp),
        )
        Text("$value", Modifier.width(36.dp), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
    }
}

// ---- font picker ------------------------------------------------------------------------------

@Composable
fun FontPickerDialog(
    current: String,
    fonts: List<FontOption>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onAddFont: () -> Unit,
    onDelete: (String) -> Unit,
) {
    HouseDialog(onDismiss) {
        Column(Modifier.padding(20.dp)) {
            DialogTitle("Font")
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                fonts.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(option.fileName) }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (option.fileName == current) "✓  " else "") + option.displayName + "　あいう漢字",
                            Modifier.weight(1f),
                            // Every option renders in its OWN glyphs.
                            fontFamily = UiStore.fontFamily(option.fileName) ?: FontFamily.Default,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!option.fileName.startsWith("@") && option.fileName.isNotEmpty()) {
                            Text(
                                "Delete",
                                Modifier.clickable { onDelete(option.fileName) }.padding(start = 12.dp),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Import font…", onClick = onAddFont)
                Spacer(Modifier.weight(1f))
                Pill("Close", onClick = onDismiss)
            }
        }
    }
}

// ---- folder chooser (kxkb: a typed path, or Browse in an in-app folder list) -------------------

@Composable
fun FolderDialog(initial: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var path by remember { mutableStateOf(initial ?: "/storage/emulated/0/") }
    var browsing by remember { mutableStateOf(false) }
    HouseDialog(onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogTitle("Backup folder")
            Text("Enter a full path, or Browse to pick a folder.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            OutlinedTextField(value = path, onValueChange = { path = it }, singleLine = true, colors = houseFieldColors(), modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pill("Cancel", onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                Pill("Browse") { browsing = true }
                Spacer(Modifier.width(8.dp))
                Pill("Save", enabled = path.isNotBlank()) { onSave(path) }
            }
        }
    }
    if (browsing) {
        FolderBrowser(start = File(path.ifBlank { "/storage/emulated/0" }), onDismiss = { browsing = false }) {
            path = it.absolutePath
            browsing = false
        }
    }
}

@Composable
private fun FolderBrowser(start: File, onDismiss: () -> Unit, onUse: (File) -> Unit) {
    var dir by remember { mutableStateOf(generateSequence(start) { it.parentFile }.firstOrNull { it.isDirectory } ?: File("/storage/emulated/0")) }
    HouseDialog(onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text(dir.absolutePath, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                @Composable
                fun entry(text: String, onClick: () -> Unit) = Text(
                    text,
                    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                )
                entry("✓ Use this folder") { onUse(dir) }
                dir.parentFile?.let { parent -> entry(".. (${parent.name.ifEmpty { "/" }})") { dir = parent } }
                dir.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }
                    .forEach { sub -> entry("📁 ${sub.name}") { dir = sub } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) { Pill("Cancel", onClick = onDismiss) }
        }
    }
}
