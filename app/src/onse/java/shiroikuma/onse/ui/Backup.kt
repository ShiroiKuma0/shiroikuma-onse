package shiroikuma.onse.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import shiroikuma.onse.voice.VoiceCatalog
import shiroikuma.onse.voice.VoiceSettingsStore
import shiroikuma.onse.voice.VoiceStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Export/import of everything settable in 白い熊 音声 — one ZIP of plain JSON, one entry per
 * category (the Kōjiki / kxkb family format), written to a real directory the user sets (All
 * Files Access, as in kxkb — the sister-app automation contract hands over a `path`).
 *
 * File names follow the family rule `shiroikuma-onse_<yyyy-MM-dd_HH-mm-ss>.zip`: every sister app
 * backs up into the same directory, so the latest-backup scan and the import list filter by that
 * prefix. The archive is written as `<name>.zip.part` and renamed only when complete; a failure
 * deletes the partial file. Import MERGES category by category and skips what is absent, so an
 * old export stays importable. Voice models themselves are never exported (they are re-downloaded):
 * the `voices` category records which ones were installed, and import fetches the missing ones.
 */
object Backup {

    const val FORMAT = "onse-export"
    const val VERSION = 1
    const val EXPORT_PREFIX = "shiroikuma-onse_"
    private const val MANIFEST = "manifest.json"
    private const val FONTS_DIR = "fonts/"
    private const val EXIM_PREFS = "onse_eximport" // device-local, never exported
    private const val KEY_DIR = "dir"
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

    enum class Cat(val id: String, val label: String, val parent: String? = null, val defaultOn: Boolean = true) {
        UI("ui", "白い熊 音声 UI (colours · fonts · sizes · layout)"),
        UI_FONTS("ui.fonts", "Imported font files", parent = "ui"),
        VOICE("voice", "Voice settings (selected voice · speed · pitch · intonation · pauses)"),
        VOICES("voices", "Installed voices (the list — models are re-downloaded)");

        companion object {
            fun byId(id: String) = entries.firstOrNull { it.id == id }
            val topLevel get() = entries.filter { it.parent == null }
            fun childrenOf(c: Cat) = entries.filter { it.parent == c.id }
            val defaultSelection get() = entries.filter { it.defaultOn }.toSet()
        }
    }

    class Cancelled : Exception("cancelled")

    // ---- directory ---------------------------------------------------------------------------

    private fun eximPrefs(context: Context) = context.getSharedPreferences(EXIM_PREFS, Context.MODE_PRIVATE)

    fun dir(context: Context): String? = eximPrefs(context).getString(KEY_DIR, null)?.takeIf { it.isNotBlank() }

    fun setDir(context: Context, path: String) {
        eximPrefs(context).edit().putString(KEY_DIR, path.trim().trimEnd('/')).commit()
    }

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun exportFileName(now: Long = System.currentTimeMillis()): String =
        EXPORT_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date(now)) + ".zip"

    fun isBackupFileName(n: String) = n.startsWith(EXPORT_PREFIX) && n.endsWith(".zip")

    /** This app's backups in [dirPath], newest first. */
    fun backups(dirPath: String?): List<File> =
        dirPath?.let { File(it).listFiles() }.orEmpty()
            .filter { it.isFile && isBackupFileName(it.name) }
            .sortedByDescending { it.lastModified() }

    data class Latest(val file: File, val whenText: String, val size: Long)

    fun latest(context: Context): Latest? = backups(dir(context)).firstOrNull()?.let {
        Latest(it, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(it.lastModified())), it.length())
    }

    fun humanSize(bytes: Long): String = when {
        bytes >= 1_048_576L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format(Locale.ROOT, "%.1f kB", bytes / 1024.0)
        else -> "$bytes B"
    }

    // ---- export ------------------------------------------------------------------------------

    data class ExportResult(val file: File, val categories: Int)

    /**
     * Write one archive of [cats] into [dirPath] (or the configured directory). [isCancelled] is
     * polled between entries; [onProgress] reports `(done, total, category label)`.
     */
    fun exportToDirectory(
        context: Context,
        cats: Set<Cat>,
        appVersion: String,
        dirPath: String? = dir(context),
        isCancelled: () -> Boolean = { false },
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
    ): ExportResult {
        val dir = File(dirPath ?: error("no-directory"))
        if (!dir.isDirectory && !dir.mkdirs()) error("cannot create $dir")
        val name = exportFileName()
        val part = File(dir, "$name.part")
        val target = File(dir, name)
        try {
            ZipOutputStream(FileOutputStream(part)).use { zip ->
                val ordered = Cat.entries.filter { it in cats }
                ordered.forEachIndexed { i, cat ->
                    if (isCancelled()) throw Cancelled()
                    onProgress(i, ordered.size, cat.label)
                    writeCategory(context, cat, zip)
                }
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(
                    JSONObject()
                        .put("format", FORMAT).put("version", VERSION)
                        .put("app", context.packageName).put("appVersion", appVersion)
                        .put("createdAt", System.currentTimeMillis())
                        .put("categories", JSONArray(ordered.map { it.id }))
                        .toString(1).toByteArray(),
                )
                zip.closeEntry()
                onProgress(ordered.size, ordered.size, "done")
            }
            if (!part.renameTo(target)) error("could not finish $name")
            return ExportResult(target, cats.size)
        } finally {
            part.delete()
        }
    }

    private fun writeCategory(context: Context, cat: Cat, zip: ZipOutputStream) {
        fun entry(name: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        }
        when (cat) {
            Cat.UI -> entry("ui.json", prefsToJson(context.getSharedPreferences(UiStore.PREFS_FILE, 0)).toString(1).toByteArray())
            Cat.UI_FONTS -> UiStore.fontsDir().listFiles().orEmpty().filter { it.isFile }.forEach { f ->
                zip.putNextEntry(ZipEntry(FONTS_DIR + f.name))
                FileInputStream(f).use { it.copyTo(zip) }
                zip.closeEntry()
            }
            Cat.VOICE -> entry(
                "voice.json",
                prefsToJson(context.getSharedPreferences(VoiceSettingsStore.PREFS_FILE, 0)).toString(1).toByteArray(),
            )
            Cat.VOICES -> {
                val installed = VoiceCatalog.get(context).models
                    .filter { !it.bundled && VoiceStore.isInstalled(context, it) }.map { it.file }
                entry("voices.json", JSONObject().put("installed", JSONArray(installed)).toString(1).toByteArray())
            }
        }
    }

    // ---- import ------------------------------------------------------------------------------

    data class ImportResult(val lines: List<String>)

    fun import(context: Context, archive: File, cats: Set<Cat>): ImportResult {
        val lines = mutableListOf<String>()
        var fonts = 0
        ZipInputStream(FileInputStream(archive)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val name = e.name
                if (!e.isDirectory && !name.contains("..")) {
                    val bytes = readCapped(zip)
                    when {
                        name == "ui.json" && Cat.UI in cats -> {
                            jsonToPrefs(JSONObject(String(bytes)), context.getSharedPreferences(UiStore.PREFS_FILE, 0))
                            lines += "白い熊 音声 UI"
                        }
                        name.startsWith(FONTS_DIR) && Cat.UI_FONTS in cats -> {
                            val f = File(UiStore.fontsDir(), name.removePrefix(FONTS_DIR).substringAfterLast('/'))
                            f.writeBytes(bytes); fonts++
                        }
                        name == "voice.json" && Cat.VOICE in cats -> {
                            jsonToPrefs(JSONObject(String(bytes)), context.getSharedPreferences(VoiceSettingsStore.PREFS_FILE, 0))
                            lines += "Voice settings"
                        }
                        name == "voices.json" && Cat.VOICES in cats -> {
                            val arr = JSONObject(String(bytes)).optJSONArray("installed") ?: JSONArray()
                            val wanted = (0 until arr.length()).map(arr::getString).toSet()
                            val catalog = VoiceCatalog.get(context)
                            val missing = catalog.models.filter { it.file in wanted && !VoiceStore.isInstalled(context, it) }
                            missing.forEach { VoiceStore.download(context, it) }
                            lines += "Installed voices: ${wanted.size} listed, ${missing.size} downloading"
                        }
                    }
                }
                e = zip.nextEntry
            }
        }
        if (fonts > 0) lines += "Imported fonts: $fonts"
        UiStore.reload()
        VoiceSettingsStore.reload()
        if (lines.isEmpty()) error("nothing in this archive matched the selected categories")
        return ImportResult(lines)
    }

    private fun readCapped(zip: ZipInputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = zip.read(buf); if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) error("archive entry too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // ---- SharedPreferences ⇄ JSON (typed) ----------------------------------------------------

    private fun prefsToJson(p: SharedPreferences): JSONObject {
        val o = JSONObject()
        for ((k, v) in p.all) {
            val t = when (v) {
                is Int -> "i"; is Long -> "l"; is Float -> "f"; is Boolean -> "b"; is String -> "s"
                is Set<*> -> "S"; else -> continue
            }
            o.put(k, JSONObject().put("t", t).put("v", if (v is Set<*>) JSONArray(v.toList()) else v))
        }
        return o
    }

    private fun jsonToPrefs(o: JSONObject, p: SharedPreferences) {
        val ed = p.edit()
        for (k in o.keys()) {
            val e = o.optJSONObject(k) ?: continue
            when (e.optString("t")) {
                "i" -> ed.putInt(k, e.getInt("v"))
                "l" -> ed.putLong(k, e.getLong("v"))
                "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                "b" -> ed.putBoolean(k, e.getBoolean("v"))
                "s" -> ed.putString(k, e.getString("v"))
                "S" -> ed.putStringSet(k, e.getJSONArray("v").let { a -> (0 until a.length()).map(a::getString).toSet() })
            }
        }
        ed.commit()
    }
}
