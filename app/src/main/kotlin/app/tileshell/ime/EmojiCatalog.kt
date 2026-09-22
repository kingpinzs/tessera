package app.tileshell.ime

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.tileshell.diag.Diagnostics

/** One emoji: what it inserts (plain Unicode, Decisions) and the Fluent Flat artwork that draws it. */
data class Emoji(val text: String, val group: String, val subgroup: String, val name: String, val file: String)

/**
 * The emoji panel's data: `assets/keyboard/emoji/index.tsv` (written by tools/fetch-keyboard.sh from
 * Microsoft's MIT-licensed Fluent Emoji and Unicode's emoji-test.txt, in Unicode's own order) and one
 * 128-px PNG per emoji beside it. The artwork is the branding module's (Decisions); what goes into the
 * app's field is always the Unicode text, so the receiving app draws it with its own font.
 *
 * Unicode's groups map onto R6 §2.6.1's categories (approximation, H6): the Windows touch keyboard of
 * the time put animals with the smileys and plants with the food, and objects with the celebrations.
 */
class EmojiCatalog(private val assets: AssetManager) {

    val byCategory: Map<EmojiCategory, List<Emoji>> by lazy { load() }

    private val bitmaps = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun bitmap(e: Emoji): Bitmap? {
        bitmaps.get(e.file)?.let { return it }
        val bmp = runCatching { assets.open("$DIR/${e.file}").use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return null
        bitmaps.put(e.file, bmp)
        return bmp
    }

    private fun load(): Map<EmojiCategory, List<Emoji>> {
        val all = runCatching {
            assets.open("$DIR/index.tsv").bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val f = line.split('\t')
                    if (f.size < 6) null else Emoji(text = f[1], group = f[2], subgroup = f[3], name = f[4], file = f[5])
                }.toList()
            }
        }.onFailure { Diagnostics.add("ime", "emoji index unreadable: $it") }.getOrDefault(emptyList())
        Diagnostics.add("ime", "emoji catalog: ${all.size} emoji")
        return all.groupBy { categoryOf(it) }.filterKeys { it != null }.mapKeys { it.key!! }
    }

    companion object {
        const val DIR = "keyboard/emoji"

        fun categoryOf(e: Emoji): EmojiCategory? = when (e.group) {
            "Smileys & Emotion" -> EmojiCategory.SMILEYS
            "People & Body" -> EmojiCategory.PEOPLE
            "Animals & Nature" -> if (e.subgroup.startsWith("plant")) EmojiCategory.FOOD else EmojiCategory.SMILEYS
            "Food & Drink" -> EmojiCategory.FOOD
            "Travel & Places" -> EmojiCategory.TRAVEL
            "Activities", "Objects" -> EmojiCategory.CELEBRATION
            "Symbols", "Flags" -> EmojiCategory.SYMBOLS
            else -> null // "Component" (skin tones, hair) is not something anyone inserts on its own
        }

        /**
         * R6 §2.6.1's "text emoticons (;-))" category. The W10M list itself was never captured, so this is
         * the common set (approximation, H6).
         */
        val TEXT_EMOTICONS = listOf(
            ":)", ":-)", ";)", ";-)", ":D", ":-D", ":(", ":-(", ":P", ":-P", ":O", ":-O", ":'(", ":|", ":/",
            ":*", "<3", "</3", "XD", "B)", "8)", "^_^", "^.^", "-_-", "o_O", "O_o", ">_<", "T_T", ":3", "(y)",
            "(n)", ":$", ">:(", "0:)", "\\o/", "¯\\_(ツ)_/¯",
        )
    }
}
