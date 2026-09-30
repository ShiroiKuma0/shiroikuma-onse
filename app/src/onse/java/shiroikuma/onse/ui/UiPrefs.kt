package shiroikuma.onse.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/**
 * 白い熊 音声 UI — every attribute of the look, in one persisted record.
 *
 * The defaults ARE the house look: black background, pure-yellow (#FFFF00) text, accent and
 * borders, so a fresh install is black-and-yellow with no user action — and every one of those
 * values is settable from the UI page. Colours are packed ARGB ints (the picker has a real A
 * channel). Sizes are plain Ints so every one is a slider; borders and thicknesses go down to 0.
 *
 * The settings-page geometry defaults are the kxkb UI page's: a 20 sp bold heading with a
 * 2.5 dp underline as wide as its text, headings at 36 dp, items at 54 / 72 / 90 dp (18 dp steps),
 * 5 dp row padding, a 1 px full-width rule between sections.
 */
data class UiPrefs(
    // ---- Colours ---------------------------------------------------------------------------
    val background: Int = BLACK,
    val surface: Int = NEAR_BLACK,
    val text: Int = YELLOW,
    val textSecondary: Int = YELLOW_DIM,
    val accent: Int = YELLOW,
    val border: Int = YELLOW,
    val divider: Int = YELLOW_FAINT,
    val selection: Int = YELLOW_SELECT,
    val errorColor: Int = ERROR_RED,

    // ---- Borders & shape -------------------------------------------------------------------
    val borderWidthDp: Int = 2,
    val cornerRadiusDp: Int = 12,
    val dividerThicknessDp: Int = 1,
    val buttonBorderWidthDp: Int = 2,
    val buttonCornerPct: Int = 50,

    // ---- Typography ------------------------------------------------------------------------
    val fontFileName: String = "",
    val fontWeight: Int = 0, // 0 = leave each text style's own weight; else 100..900
    val fontScalePct: Int = 100,
    val titleSizeSp: Int = 20,
    val bodySizeSp: Int = 16,
    val labelSizeSp: Int = 13,

    // ---- Main screen -----------------------------------------------------------------------
    val speakButtonHeightDp: Int = 64,
    val tryTextSizeSp: Int = 20,
    val voiceRowPadDp: Int = 4,
    val cardPadDp: Int = 10,

    // ---- Settings page ---------------------------------------------------------------------
    val sectionTitleSizeSp: Int = 20,
    /** Underline thickness in tenths of a dp — kxkb's is 2.5 dp. */
    val sectionUnderlineTenthDp: Int = 25,
    val indentBaseDp: Int = 36,
    val indentStepDp: Int = 18,
    val settingsRowVPadDp: Int = 5,
    val sectionGapDp: Int = 10,

    // ---- One-click colour swatches (most recently applied, newest first) --------------------
    val recentColors: List<Int> = DEFAULT_SWATCHES,
) {
    companion object {
        const val BLACK = 0xFF000000.toInt()
        const val NEAR_BLACK = 0xFF0A0A00.toInt() // kxkb_surface_dark
        const val YELLOW = 0xFFFFFF00.toInt() // pure yellow, NOT material amber
        const val YELLOW_DIM = 0xFFC8C800.toInt() // kxkb_yellow_dim — summaries
        const val YELLOW_FAINT = 0x66FFFF00 // dividers (~40 %)
        const val YELLOW_SELECT = 0x33FFFF00 // selection fill (~20 %)
        const val ERROR_RED = 0xFFFF5252.toInt() // the Kōjiki warn red

        val DEFAULT_SWATCHES = listOf(
            YELLOW, BLACK, NEAR_BLACK, YELLOW_DIM, YELLOW_FAINT, YELLOW_SELECT,
            ERROR_RED, 0xFFFFFFFF.toInt(), 0xFF7FB4FF.toInt(), 0xFF00E676.toInt(),
        )
        const val MAX_RECENT_COLORS = 12

        const val BORDER_WIDTH_MAX = 8
        const val CORNER_RADIUS_MAX = 40
        const val DIVIDER_MAX = 6
        const val FONT_SCALE_MIN = 70
        const val FONT_SCALE_MAX = 160
        const val TEXT_SIZE_MIN = 8
        const val TEXT_SIZE_MAX = 40
        const val PAD_MAX = 32
        const val BUTTON_HEIGHT_MIN = 36
        const val BUTTON_HEIGHT_MAX = 160
        const val INDENT_MAX = 72
        const val SECTION_TITLE_MIN = 12
        const val SECTION_TITLE_MAX = 34
        const val UNDERLINE_MAX_TENTHS = 60
    }
}

/** One option in the font picker. [fileName] is "", [UiStore.MONOSPACE], [UiStore.SERIF] or a file. */
data class FontOption(val displayName: String, val fileName: String)

/**
 * The persisted store behind [UiPrefs], in the `shiroikuma_ui_theme` SharedPreferences file — the
 * family name the sister forks use, and the file the export's `ui` category carries.
 */
object UiStore {

    const val PREFS_FILE = "shiroikuma_ui_theme"
    const val MONOSPACE = "@monospace"
    const val SERIF = "@serif"

    private lateinit var appContext: Context
    private val _prefs = MutableStateFlow(UiPrefs())
    val prefs: StateFlow<UiPrefs> = _prefs.asStateFlow()
    private val fontFamilyCache = mutableMapOf<String, FontFamily?>()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _prefs.value = load()
    }

    private fun store() = appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun update(transform: (UiPrefs) -> UiPrefs) {
        val next = transform(_prefs.value)
        _prefs.value = next
        save(next)
    }

    fun resetToDefaults() = update { UiPrefs(recentColors = it.recentColors) }

    fun rememberColor(argb: Int) = update { current ->
        current.copy(
            recentColors = (listOf(argb) + current.recentColors.filter { it != argb })
                .take(UiPrefs.MAX_RECENT_COLORS),
        )
    }

    /** Re-read from disk — used after an import replaced the prefs file underneath us. */
    fun reload() {
        if (::appContext.isInitialized) {
            fontFamilyCache.clear()
            _prefs.value = load()
        }
    }

    // ---- persistence: one key per field, named after the field ------------------------------

    private val intFields: List<Pair<String, Pair<(UiPrefs) -> Int, (UiPrefs, Int) -> UiPrefs>>> = listOf(
        "background" to ({ p: UiPrefs -> p.background } to { p: UiPrefs, v: Int -> p.copy(background = v) }),
        "surface" to ({ p: UiPrefs -> p.surface } to { p: UiPrefs, v: Int -> p.copy(surface = v) }),
        "text" to ({ p: UiPrefs -> p.text } to { p: UiPrefs, v: Int -> p.copy(text = v) }),
        "textSecondary" to ({ p: UiPrefs -> p.textSecondary } to { p: UiPrefs, v: Int -> p.copy(textSecondary = v) }),
        "accent" to ({ p: UiPrefs -> p.accent } to { p: UiPrefs, v: Int -> p.copy(accent = v) }),
        "border" to ({ p: UiPrefs -> p.border } to { p: UiPrefs, v: Int -> p.copy(border = v) }),
        "divider" to ({ p: UiPrefs -> p.divider } to { p: UiPrefs, v: Int -> p.copy(divider = v) }),
        "selection" to ({ p: UiPrefs -> p.selection } to { p: UiPrefs, v: Int -> p.copy(selection = v) }),
        "errorColor" to ({ p: UiPrefs -> p.errorColor } to { p: UiPrefs, v: Int -> p.copy(errorColor = v) }),
        "borderWidthDp" to ({ p: UiPrefs -> p.borderWidthDp } to { p: UiPrefs, v: Int -> p.copy(borderWidthDp = v) }),
        "cornerRadiusDp" to ({ p: UiPrefs -> p.cornerRadiusDp } to { p: UiPrefs, v: Int -> p.copy(cornerRadiusDp = v) }),
        "dividerThicknessDp" to ({ p: UiPrefs -> p.dividerThicknessDp } to { p: UiPrefs, v: Int -> p.copy(dividerThicknessDp = v) }),
        "buttonBorderWidthDp" to ({ p: UiPrefs -> p.buttonBorderWidthDp } to { p: UiPrefs, v: Int -> p.copy(buttonBorderWidthDp = v) }),
        "buttonCornerPct" to ({ p: UiPrefs -> p.buttonCornerPct } to { p: UiPrefs, v: Int -> p.copy(buttonCornerPct = v) }),
        "fontWeight" to ({ p: UiPrefs -> p.fontWeight } to { p: UiPrefs, v: Int -> p.copy(fontWeight = v) }),
        "fontScalePct" to ({ p: UiPrefs -> p.fontScalePct } to { p: UiPrefs, v: Int -> p.copy(fontScalePct = v) }),
        "titleSizeSp" to ({ p: UiPrefs -> p.titleSizeSp } to { p: UiPrefs, v: Int -> p.copy(titleSizeSp = v) }),
        "bodySizeSp" to ({ p: UiPrefs -> p.bodySizeSp } to { p: UiPrefs, v: Int -> p.copy(bodySizeSp = v) }),
        "labelSizeSp" to ({ p: UiPrefs -> p.labelSizeSp } to { p: UiPrefs, v: Int -> p.copy(labelSizeSp = v) }),
        "speakButtonHeightDp" to ({ p: UiPrefs -> p.speakButtonHeightDp } to { p: UiPrefs, v: Int -> p.copy(speakButtonHeightDp = v) }),
        "tryTextSizeSp" to ({ p: UiPrefs -> p.tryTextSizeSp } to { p: UiPrefs, v: Int -> p.copy(tryTextSizeSp = v) }),
        "voiceRowPadDp" to ({ p: UiPrefs -> p.voiceRowPadDp } to { p: UiPrefs, v: Int -> p.copy(voiceRowPadDp = v) }),
        "cardPadDp" to ({ p: UiPrefs -> p.cardPadDp } to { p: UiPrefs, v: Int -> p.copy(cardPadDp = v) }),
        "sectionTitleSizeSp" to ({ p: UiPrefs -> p.sectionTitleSizeSp } to { p: UiPrefs, v: Int -> p.copy(sectionTitleSizeSp = v) }),
        "sectionUnderlineTenthDp" to ({ p: UiPrefs -> p.sectionUnderlineTenthDp } to { p: UiPrefs, v: Int -> p.copy(sectionUnderlineTenthDp = v) }),
        "indentBaseDp" to ({ p: UiPrefs -> p.indentBaseDp } to { p: UiPrefs, v: Int -> p.copy(indentBaseDp = v) }),
        "indentStepDp" to ({ p: UiPrefs -> p.indentStepDp } to { p: UiPrefs, v: Int -> p.copy(indentStepDp = v) }),
        "settingsRowVPadDp" to ({ p: UiPrefs -> p.settingsRowVPadDp } to { p: UiPrefs, v: Int -> p.copy(settingsRowVPadDp = v) }),
        "sectionGapDp" to ({ p: UiPrefs -> p.sectionGapDp } to { p: UiPrefs, v: Int -> p.copy(sectionGapDp = v) }),
    )

    private fun load(): UiPrefs {
        val sp = store()
        var p = UiPrefs()
        for ((key, access) in intFields) {
            if (sp.contains(key)) p = access.second(p, sp.getInt(key, access.first(p)))
        }
        p = p.copy(fontFileName = sp.getString("fontFileName", p.fontFileName) ?: "")
        decodeColors(sp.getString("recentColors", null))?.let { p = p.copy(recentColors = it) }
        return p
    }

    private fun save(v: UiPrefs) {
        store().edit().apply {
            for ((key, access) in intFields) putInt(key, access.first(v))
            putString("fontFileName", v.fontFileName)
            putString("recentColors", JSONArray().apply { v.recentColors.forEach { put(it) } }.toString())
        }.apply()
    }

    private fun decodeColors(raw: String?): List<Int>? = raw?.let {
        runCatching { JSONArray(it).let { a -> (0 until a.length()).map(a::getInt) } }.getOrNull()
    }

    // ---- external fonts ----------------------------------------------------------------------

    fun fontsDir(): File = File(appContext.filesDir, "fonts").apply { mkdirs() }

    fun availableFonts(): List<FontOption> {
        val options = mutableListOf(
            FontOption("App default", ""),
            FontOption("Serif", SERIF),
            FontOption("Monospace", MONOSPACE),
        )
        fontsDir().listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in FONT_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { options.add(FontOption(it.nameWithoutExtension, it.name)) }
        return options
    }

    fun displayNameFor(fileName: String): String = when (fileName) {
        "" -> "App default"
        MONOSPACE -> "Monospace"
        SERIF -> "Serif"
        else -> File(fileName).nameWithoutExtension
    }

    /** null = use the caller's own default family. */
    fun fontFamily(fileName: String): FontFamily? = when (fileName) {
        "" -> null
        MONOSPACE -> FontFamily.Monospace
        SERIF -> FontFamily.Serif
        else -> fontFamilyCache.getOrPut(fileName) {
            runCatching {
                val file = File(fontsDir(), fileName)
                if (file.isFile) FontFamily(Font(file)) else null
            }.getOrNull()
        }
    }

    /** Copy a user-picked .ttf/.otf into the private fonts directory. Returns the stored name. */
    fun importFont(uri: Uri): String? = runCatching {
        val rawName = queryDisplayName(uri) ?: "font-${System.currentTimeMillis()}.ttf"
        if (rawName.substringAfterLast('.', "").lowercase() !in FONT_EXTENSIONS) return null
        val dest = File(fontsDir(), sanitize(rawName))
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: return null
        fontFamilyCache.remove(dest.name)
        dest.name
    }.getOrNull()

    fun deleteFont(fileName: String) {
        if (fileName.isEmpty() || fileName.startsWith("@")) return
        runCatching { File(fontsDir(), fileName).delete() }
        fontFamilyCache.remove(fileName)
        update { if (it.fontFileName == fileName) it.copy(fontFileName = "") else it }
    }

    private fun queryDisplayName(uri: Uri): String? =
        appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }

    private fun sanitize(name: String): String =
        name.substringAfterLast('/').replace(Regex("[/\\\\:*?\"<>|]"), "_")

    private val FONT_EXTENSIONS = setOf("ttf", "otf")
}
