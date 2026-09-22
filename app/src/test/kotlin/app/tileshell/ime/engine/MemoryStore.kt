package app.tileshell.ime.engine

/** A [WordStore] in memory, remembering how often it was written. */
class MemoryStore(var text: String? = null) : WordStore {
    var saves = 0
    override fun load(): String? = text
    override fun save(text: String) {
        this.text = text
        saves++
    }
}
