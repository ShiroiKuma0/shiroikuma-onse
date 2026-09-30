package shiroikuma.onse.voice

import android.content.Context
import org.json.JSONObject

/**
 * Every VOICEVOX voice there is, from the bundled `assets/voices/catalog.json` — written on the PC
 * by `shiroikuma/voices/gen-voices.py` from the official voicevox_vvm release the app downloads
 * from, together with one short sample per style (`assets/voices/samples/<style id>.ogg`), so any
 * voice can be heard before its model is downloaded.
 */
data class VoiceStyle(
    val id: Int,
    val name: String,
    /** "streaming_talk" / "talk" read text; "sing" / "frame_decode" are the singing model's. */
    val type: String,
    val sample: String?,
) {
    val canRead: Boolean get() = type == "talk" || type == "streaming_talk"
}

data class VoiceCharacter(val name: String, val uuid: String, val styles: List<VoiceStyle>)

data class VoiceModel(
    val file: String,
    val url: String,
    val size: Long,
    val sha256: String,
    val bundled: Boolean,
    val singing: Boolean,
    val characters: List<VoiceCharacter>,
)

/** A readable style together with where it lives — what the selector and the engine work with. */
data class VoiceChoice(val model: VoiceModel, val character: VoiceCharacter, val style: VoiceStyle) {
    val label: String get() = "${character.name}（${style.name}）"
}

class VoiceCatalog private constructor(
    val release: String,
    val defaultStyle: Int,
    val sampleText: String,
    val models: List<VoiceModel>,
) {
    /** Every style that can read text, in catalogue order. */
    val readable: List<VoiceChoice> by lazy {
        models.flatMap { m -> m.characters.flatMap { c -> c.styles.filter { it.canRead }.map { VoiceChoice(m, c, it) } } }
    }

    fun choiceFor(styleId: Int): VoiceChoice? = readable.firstOrNull { it.style.id == styleId }

    fun modelFor(styleId: Int): VoiceModel? =
        models.firstOrNull { m -> m.characters.any { c -> c.styles.any { it.id == styleId } } }

    /** Characters in first-appearance order, each with every readable style across all models. */
    val characters: List<Pair<String, List<VoiceChoice>>> by lazy {
        readable.groupBy { it.character.uuid }.values.map { it.first().character.name to it }
    }

    companion object {
        @Volatile private var cached: VoiceCatalog? = null

        fun get(context: Context): VoiceCatalog = cached ?: synchronized(this) {
            cached ?: load(context).also { cached = it }
        }

        private fun load(context: Context): VoiceCatalog {
            val json = context.assets.open("voices/catalog.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val models = root.getJSONArray("models").let { arr ->
                (0 until arr.length()).map { i ->
                    val m = arr.getJSONObject(i)
                    VoiceModel(
                        file = m.getString("file"),
                        url = m.getString("url"),
                        size = m.getLong("size"),
                        sha256 = m.getString("sha256"),
                        bundled = m.optBoolean("bundled"),
                        singing = m.optBoolean("singing"),
                        characters = m.getJSONArray("characters").let { ca ->
                            (0 until ca.length()).map { j ->
                                val c = ca.getJSONObject(j)
                                VoiceCharacter(
                                    name = c.getString("name"),
                                    uuid = c.getString("uuid"),
                                    styles = c.getJSONArray("styles").let { sa ->
                                        (0 until sa.length()).map { k ->
                                            val s = sa.getJSONObject(k)
                                            VoiceStyle(
                                                id = s.getInt("id"),
                                                name = s.getString("name"),
                                                type = s.getString("type"),
                                                sample = if (s.isNull("sample")) null else s.getString("sample"),
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
            }
            return VoiceCatalog(
                release = root.getString("release"),
                defaultStyle = root.getInt("defaultStyle"),
                sampleText = root.getString("sampleText"),
                models = models,
            )
        }
    }
}
