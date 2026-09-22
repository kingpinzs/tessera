package app.tileshell.ime

import android.content.Context
import app.tileshell.diag.Diagnostics
import app.tileshell.ime.engine.Alternates
import app.tileshell.ime.engine.KeyPoint
import app.tileshell.ime.engine.LetterLayout
import app.tileshell.ime.engine.Lexicon
import app.tileshell.ime.engine.SpaceRules
import app.tileshell.ime.engine.Suggester
import app.tileshell.ime.engine.SwipeDecoder
import app.tileshell.ime.engine.UserDictionary
import app.tileshell.ime.engine.WordStore
import java.io.File
import app.tileshell.ime.engine.FieldKind as EngineField

/**
 * [TextBrain] over the phase 05 text engine. Built once per keyboard process, off the main thread: the
 * dictionary is `assets/keyboard/en_US.tsv` (tools/fetch-keyboard.sh, licence in docs/plan/qa/phase-05/
 * BUILD-START.md), and the learned words live in the `:ime` process's own files.
 *
 * The engine's key model is the LETTERS layout in [KeyGrid]'s phys coordinates, so a Word Flow path
 * handed over in phys lines up with it whatever the screen, the dock or the raise.
 */
class EngineBrain private constructor(
    private val lexicon: Lexicon,
    private val user: UserDictionary,
    layout: LetterLayout,
) : TextBrain {

    private val suggester = Suggester(lexicon, layout, user)
    private val decoder = SwipeDecoder(lexicon, layout, user)

    override fun offer(word: String, previous: String?, field: FieldInfo): Offer {
        val s = suggester.suggest(word, kindOf(field), previous)
        return Offer(s.items, s.autoCorrect)
    }

    override fun decodeSwipe(path: List<Pair<Float, Float>>): List<String> =
        decoder.decode(path.map { KeyPoint(it.first, it.second) })

    override fun alternates(ch: String, upper: Boolean): List<String> =
        if (ch.length == 1) Alternates.of(ch[0], upper).map { it.toString() } else listOf(ch)

    override fun doubleSpacePeriod(textBefore: String, sinceLastSpaceMs: Long, field: FieldInfo): Boolean =
        SpaceRules.space(textBefore, sinceLastSpaceMs, kindOf(field)) == SpaceRules.PERIOD

    override fun committed(word: String, field: FieldInfo) {
        if (user.commit(word, kindOf(field))) Diagnostics.add("ime", "learned a word after its second commit")
    }

    override fun isKnown(word: String): Boolean = lexicon.contains(word) || user.contains(word)
    override fun isLearned(word: String): Boolean = user.contains(word)
    override fun add(word: String) = user.add(word)
    override fun remove(word: String) = user.remove(word)

    override fun properCase(word: String): String {
        val rank = lexicon.rankOf(word)
        if (rank < 0) return user.canonical(word) ?: word
        // "us" stays "us" when the source also spells it lower case, rather than becoming "US".
        return if (lexicon.isLowercaseInSource(rank)) word else lexicon.wordAt(rank)
    }

    companion object {
        const val DICTIONARY = "keyboard/en_US.tsv"

        fun load(context: Context): EngineBrain {
            val lexicon = context.assets.open(DICTIONARY).reader(Charsets.UTF_8).use { Lexicon.load(it) }
            val centres = Layouts.build(Layer.LETTERS, FieldInfo.DEFAULT).letterCentres()
                .mapValues { (_, c) -> KeyPoint(c.first, c.second) }
            val layout = LetterLayout(centres, KeyGrid.PITCH, KeyGrid.ROW_PITCH)
            val file = File(context.filesDir, "learned_words.txt")
            val store = object : WordStore {
                override fun load(): String? = runCatching { if (file.exists()) file.readText() else null }.getOrNull()
                override fun save(text: String) {
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    runCatching {
                        tmp.writeText(text)
                        check(tmp.renameTo(file)) { "rename failed" }
                    }.onFailure { Diagnostics.add("ime", "learned words write failed: $it") }
                }
            }
            val user = UserDictionary(store) { lexicon.contains(it) }
            Diagnostics.add("ime", "dictionary: ${lexicon.size} words, ${user.words.size} learned")
            return EngineBrain(lexicon, user, layout)
        }

        /** The engine's field columns (suggests / autocorrects / learns / double-space) from the field. */
        fun kindOf(field: FieldInfo): EngineField = when {
            field.isPassword -> EngineField.PASSWORD
            field.noSuggestions || field.kind == FieldKind.PHONE || field.kind == FieldKind.NUMBER -> EngineField.NO_SUGGESTIONS
            field.kind == FieldKind.URL -> EngineField.URL
            field.kind == FieldKind.EMAIL -> EngineField.EMAIL
            else -> EngineField.TEXT
        }
    }
}
