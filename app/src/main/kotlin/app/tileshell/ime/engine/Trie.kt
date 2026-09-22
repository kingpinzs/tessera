package app.tileshell.ime.engine

/**
 * The lexicon's index: a trie over lowercase spellings, stored as parallel primitive arrays.
 *
 * One structure answers all three questions the engine asks of 150k words — "is this a word" (walk),
 * "what starts with this" (walk, then the subtree) and "what is within a couple of typos of this"
 * (a depth-first walk carrying an edit-distance row, pruned wherever the row's minimum passes the
 * budget) — so there is no hash map, no sorted copy and no boxed Integer per word. Children hang off a
 * node as a singly linked list ([firstChild] / [nextSibling]); a node has at most 28 of them and most
 * deep nodes have one or two, so the linear scan is cheaper than any child table would be.
 *
 * Fourteen bytes per node; an English lexicon of 150k words is around 400k nodes.
 */
internal class Trie(initialCapacity: Int = 1 shl 16) {

    var firstChild = IntArray(initialCapacity) { NONE }
        private set
    var nextSibling = IntArray(initialCapacity) { NONE }
        private set
    var label = CharArray(initialCapacity)
        private set

    /** The word index at a terminal node, or [NONE] where the path is only a prefix. */
    var wordOf = IntArray(initialCapacity) { NONE }
        private set

    /** Node count; node 0 is the root. */
    var size = 1
        private set

    fun child(node: Int, c: Char): Int {
        var n = firstChild[node]
        while (n != NONE) {
            if (label[n] == c) return n
            n = nextSibling[n]
        }
        return NONE
    }

    /** The node at the end of [word] lowercased, or [NONE]. */
    fun find(word: CharSequence): Int {
        var node = 0
        for (i in word.indices) {
            node = child(node, Character.toLowerCase(word[i]))
            if (node == NONE) return NONE
        }
        return node
    }

    /** The node at the end of [word] lowercased, creating the path as needed. */
    fun insert(word: CharSequence): Int {
        var node = 0
        for (i in word.indices) {
            val c = Character.toLowerCase(word[i])
            var n = child(node, c)
            if (n == NONE) {
                n = newNode(c)
                // Prepend: order among siblings carries no meaning, and prepending is O(1).
                nextSibling[n] = firstChild[node]
                firstChild[node] = n
            }
            node = n
        }
        return node
    }

    fun setWord(node: Int, wordIndex: Int) {
        wordOf[node] = wordIndex
    }

    private fun newNode(c: Char): Int {
        if (size == label.size) grow()
        val n = size++
        label[n] = c
        firstChild[n] = NONE
        nextSibling[n] = NONE
        wordOf[n] = NONE
        return n
    }

    private fun grow() {
        val cap = label.size * 2
        firstChild = firstChild.copyOf(cap)
        nextSibling = nextSibling.copyOf(cap)
        label = label.copyOf(cap)
        wordOf = wordOf.copyOf(cap)
    }

    companion object {
        const val NONE = -1
    }
}
