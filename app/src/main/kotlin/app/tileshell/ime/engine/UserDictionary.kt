package app.tileshell.ime.engine

/**
 * Where the learned words live. The IME backs this with an app-private file; tests with a string.
 * [load] returns null when nothing has been saved yet.
 */
interface WordStore {
    fun load(): String?
    fun save(text: String)
}

/**
 * Word learning (phase 05 Decisions stand-in (6)): "a word not in the dictionary is offered as a
 * suggestion once it has been committed twice outside password fields; '– word' in the strip (R6
 * §2.2.7) removes it". "+ word" (R6 §2.2.7, D1 l.1558–1560) adds it at once.
 *
 * Two counts, not one: a word is either learned or still pending its second commit. Removing a learned
 * word drops it from both, so it starts again from zero and needs two fresh commits to come back —
 * "– word" is the typist saying this is not a word, and one more slip must not resurrect it.
 *
 * Commits in a password field are never counted (they never reach the pending count either, so a
 * password typed twice is not half-learned). The [lexicon] check keeps dictionary words out: they are
 * already offered, and learning them would only bloat the file.
 *
 * Persisted through [WordStore] after every change, in a versioned line format the owner can read:
 *
 *     tileshell-user-dictionary 1
 *     learned<TAB>Becase
 *     pending<TAB>Bekasi<TAB>1
 */
class UserDictionary(private val store: WordStore, private val isKnown: (String) -> Boolean) {

    /** lowercase → the spelling as committed, in the order learned. */
    private val learned = LinkedHashMap<String, String>()

    /** lowercase → (spelling, commits so far), for words seen but not yet learned. */
    private val pending = LinkedHashMap<String, Pending>()

    private class Pending(var spelling: String, var commits: Int)

    init {
        parse(store.load())
    }

    /** The learned words, as committed, in the order learned. */
    val words: Collection<String> get() = learned.values

    fun contains(word: CharSequence): Boolean = learned.containsKey(key(word))

    /** The spelling as learned ("Becase" for "becase"), or null. */
    fun canonical(word: CharSequence): String? = learned[key(word)]

    /** How many commits a not-yet-learned word has; 0 for anything else. */
    fun pendingCount(word: CharSequence): Int = pending[key(word)]?.commits ?: 0

    /**
     * A word was committed (space, punctuation, or a tapped suggestion) in a field of kind [field].
     * Returns true when this commit is the one that learned it.
     */
    fun commit(word: String, field: FieldKind): Boolean {
        if (!field.learns || !isLearnable(word) || isKnown(word)) return false
        val k = key(word)
        if (learned.containsKey(k)) return false
        val p = pending.getOrPut(k) { Pending(word, 0) }
        p.spelling = word
        p.commits++
        if (p.commits < COMMITS_TO_LEARN) {
            persist()
            return false
        }
        pending.remove(k)
        learned[k] = word
        persist()
        return true
    }

    /** "+ word": learned at once, whatever field it came from and however often it was seen. */
    fun add(word: String) {
        if (!isLearnable(word)) return
        val k = key(word)
        pending.remove(k)
        learned[k] = word
        persist()
    }

    /** "– word": forgotten, and its commit count with it. */
    fun remove(word: CharSequence) {
        val k = key(word)
        val changed = (learned.remove(k) != null) or (pending.remove(k) != null)
        if (changed) persist()
    }

    private fun persist() = store.save(serialise())

    private fun serialise(): String {
        val sb = StringBuilder("$HEADER $FORMAT_VERSION\n")
        for (w in learned.values) sb.append("learned\t").append(w).append('\n')
        for (p in pending.values) sb.append("pending\t").append(p.spelling).append('\t').append(p.commits).append('\n')
        return sb.toString()
    }

    private fun parse(text: String?) {
        if (text.isNullOrEmpty()) return
        val lines = text.lineSequence().iterator()
        val header = lines.next().split(' ')
        // A file from a newer format is not guessed at: better to start empty than to misread it.
        if (header.size != 2 || header[0] != HEADER || header[1].toIntOrNull() != FORMAT_VERSION) return
        for (line in lines) {
            val parts = line.split('\t')
            when {
                parts.size == 2 && parts[0] == "learned" && isLearnable(parts[1]) ->
                    learned[key(parts[1])] = parts[1]
                parts.size == 3 && parts[0] == "pending" && isLearnable(parts[1]) ->
                    parts[2].toIntOrNull()?.takeIf { it > 0 }?.let { pending[key(parts[1])] = Pending(parts[1], it) }
            }
        }
    }

    companion object {
        const val COMMITS_TO_LEARN = 2
        const val FORMAT_VERSION = 1
        private const val HEADER = "tileshell-user-dictionary"

        private fun key(word: CharSequence): String = word.toString().lowercase()

        /**
         * A learnable word is spelled from the dictionary's own alphabet — letters, apostrophe, hyphen —
         * with at least one letter. "2026", "http://x" and ":-)" are committed tokens, not words; the
         * strip would never sensibly offer them and the ruling is about WORDS.
         */
        fun isLearnable(word: CharSequence): Boolean {
            if (word.isEmpty()) return false
            var letters = 0
            for (c in word) {
                when {
                    Character.isLetter(c) -> letters++
                    c == '\'' || c == '-' || c == '’' -> Unit
                    else -> return false
                }
            }
            return letters > 0
        }
    }
}
