package shiroikuma.onse.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The live Material scheme and typography built from [UiPrefs], so every knob on the 白い熊 音声 UI
 * page takes effect the moment it moves.
 *
 * Every `*Container` role is a FLAT surface colour (alpha yellow over black reads as olive) and
 * [ColorScheme.surfaceTint] is transparent, so tonal elevation never pulls a surface toward yellow.
 */
fun onseColorScheme(p: UiPrefs): ColorScheme {
    val background = Color(p.background)
    val surface = Color(p.surface)
    val text = Color(p.text)
    val accent = Color(p.accent)
    val onAccent = accent.contrastingOnColor()
    return darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = surface, onPrimaryContainer = text,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = surface, onSecondaryContainer = text,
        tertiary = accent, onTertiary = onAccent,
        tertiaryContainer = surface, onTertiaryContainer = text,
        error = Color(p.errorColor), onError = background,
        errorContainer = surface, onErrorContainer = Color(p.errorColor),
        background = background, onBackground = text,
        surface = background, onSurface = text,
        surfaceVariant = surface, onSurfaceVariant = Color(p.textSecondary),
        outline = Color(p.border), outlineVariant = Color(p.divider),
        scrim = Color(0xFF000000),
        inverseSurface = text, inverseOnSurface = background, inversePrimary = background,
        surfaceTint = Color.Transparent,
        surfaceDim = background, surfaceBright = surface,
        surfaceContainerLowest = background, surfaceContainerLow = background,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface,
    )
}

/** The page's font, weight and scale over Material's typography; title/body/label sizes set. */
fun onseTypography(p: UiPrefs): Typography {
    val base = Typography()
    val family = UiStore.fontFamily(p.fontFileName)
    val weight = p.fontWeight.takeIf { it in 100..900 }?.let(::FontWeight)
    val scale = p.fontScalePct / 100f
    fun style(s: TextStyle, sizeSp: Int? = null) = s.copy(
        fontFamily = family ?: s.fontFamily,
        fontWeight = weight ?: s.fontWeight,
        fontSize = ((sizeSp?.toFloat() ?: s.fontSize.value) * scale).sp,
        lineHeight = (((sizeSp?.toFloat() ?: s.fontSize.value) * 1.25f) * scale).sp,
    )
    return Typography(
        displayLarge = style(base.displayLarge), displayMedium = style(base.displayMedium),
        displaySmall = style(base.displaySmall), headlineLarge = style(base.headlineLarge),
        headlineMedium = style(base.headlineMedium), headlineSmall = style(base.headlineSmall),
        titleLarge = style(base.titleLarge, p.titleSizeSp), titleMedium = style(base.titleMedium, p.titleSizeSp - 3),
        titleSmall = style(base.titleSmall, p.titleSizeSp - 5),
        bodyLarge = style(base.bodyLarge, p.bodySizeSp), bodyMedium = style(base.bodyMedium, p.bodySizeSp - 2),
        bodySmall = style(base.bodySmall, p.labelSizeSp),
        labelLarge = style(base.labelLarge, p.labelSizeSp + 1), labelMedium = style(base.labelMedium, p.labelSizeSp),
        labelSmall = style(base.labelSmall, p.labelSizeSp - 1),
    )
}

/** Black or white, whichever reads better on [this]. */
fun Color.contrastingOnColor(): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return if (luminance > 0.6f) Color(0xFF000000) else Color(0xFFFFFFFF)
}

fun UiPrefs.family(): FontFamily? = UiStore.fontFamily(fontFileName)

/** Wraps [content] in the live 白い熊 音声 look. */
@Composable
fun OnseTheme(content: @Composable () -> Unit) {
    val p by UiStore.prefs.collectAsState()
    MaterialTheme(colorScheme = onseColorScheme(p), typography = onseTypography(p), content = content)
}
