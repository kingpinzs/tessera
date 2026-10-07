package app.tileshell.files

/**
 * The extras FilesActivity reads (r3 D12): they only choose what is shown. Any app can start Files with any extras, so
 * they are read in ONE place that cannot throw ([request]) and reach a diagnostics line only through [lineSafe] (the
 * GATE review's L8 and L9; `FilesIntentsTest`).
 */
object FilesIntents {
    /** The page: `device`, `recent`, `bin` or `volume:<uuid>`. */
    const val EXTRA_PAGE = "page"

    /** [EXTRA_PAGE]'s values: This Device (the primary shared storage), Recent, the Recycle Bin, a mounted volume's root. */
    const val PAGE_DEVICE = "device"
    const val PAGE_RECENT = "recent"
    const val PAGE_BIN = "bin"
    const val PAGE_VOLUME_PREFIX = "volume:"

    /** [EXTRA_PAGE] for the volume with that `StorageVolume.getUuid()` (the dynamic "SD card" shortcut's page). */
    fun volumePage(uuid: String): String = PAGE_VOLUME_PREFIX + uuid

    /** A folder to open, canonical under a mounted volume. */
    const val EXTRA_PATH = "path"

    /** The row to show in that folder. */
    const val EXTRA_NAME = "name"

    /** Who asked (`recorder`), for `[files] open at <path> (from <caller>)`. */
    const val EXTRA_FROM = "from"

    /** The longest each extra may be, in characters; a longer one is ignored (a path fits `PATH_MAX`, a name one segment). */
    const val MAX_PAGE = 64
    const val MAX_PATH = 4096
    const val MAX_NAME = 255
    const val MAX_FROM = 64

    /** The longest piece of caller-supplied text [lineSafe] lets into a line by default, and the caller token's cap. */
    const val LINE_MAX = 120
    const val TOKEN_MAX = 32
    private const val TOKEN_MARKS = "._-:"

    /**
     * A launch's extras as the platform holds them (`intent.extras?.get(key)`): the value of any type, or null. Reading
     * one may THROW — another app's Parcelable cannot be unparcelled in this process.
     */
    fun interface Extras {
        fun raw(key: String): Any?
    }

    /**
     * The four extras, read defensively (L9): never throws. An extra that cannot be read, is not text, or is longer
     * than its cap is ignored — as if it had not been sent — with `open ignored: <why>` through [say]; the why names
     * the key and the size, never the caller's text.
     */
    fun request(extras: Extras?, say: (String) -> Unit): FilesRequest {
        fun text(key: String, max: Int): String? {
            val raw = try {
                extras?.raw(key)
            } catch (e: Throwable) {
                say("open ignored: the $key extra cannot be read (${e.javaClass.simpleName})")
                return null
            }
            return when {
                raw == null -> null
                raw !is String -> { say("open ignored: the $key extra is not text"); null }
                raw.length > max -> { say("open ignored: the $key extra is too long (${raw.length})"); null }
                else -> raw
            }
        }
        return FilesRequest(text(EXTRA_PAGE, MAX_PAGE), text(EXTRA_PATH, MAX_PATH), text(EXTRA_NAME, MAX_NAME), text(EXTRA_FROM, MAX_FROM))
    }

    /**
     * Caller-supplied text made fit for ONE diagnostics line (L8): every control, format and line-breaking character
     * is removed (a newline cannot start a forged `[files]` line) and the length is capped at [max]. With [token] only
     * ASCII letters, digits and `. _ - :` are kept — the form of a page (`volume:3842-1609`) and of a caller's name.
     */
    fun lineSafe(text: String?, max: Int = LINE_MAX, token: Boolean = false): String {
        val out = StringBuilder()
        for (c in text.orEmpty()) {
            if (out.length >= max) break
            val keep = if (token) {
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in TOKEN_MARKS
            } else {
                when (Character.getType(c).toByte()) {
                    Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.UNASSIGNED -> false
                    else -> true
                }
            }
            if (keep) out.append(c)
        }
        return out.toString()
    }

    /** `FilesActivity created page=<page>`: the page as a token, `none` when no page was sent. */
    fun createdLine(request: FilesRequest): String = "FilesActivity created page=${request.page?.let { lineSafe(it, MAX_PAGE, token = true) } ?: "none"}"

    /** `open at <path> (from <caller>)`: the folder that was opened and the caller's name as a token (`unknown` when it has none). */
    fun openAtLine(path: String, from: String): String =
        "open at ${lineSafe(path, 400)} (from ${lineSafe(from, TOKEN_MAX, token = true).ifEmpty { "unknown" }})"
}
