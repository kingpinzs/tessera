package app.tileshell.music

/**
 * One file played outside the library (phase 18, "below Q-18-2"): what the session calls it. Pure; the tags are read
 * by [MusicService], the rule for what is shown when they are missing is here (`MusicFileTest`).
 *
 * Nothing about the file is written anywhere: it has no MediaStore row and gets none, so it has no library id.
 */
object MusicFile {
    /** The media id's prefix. Never a number, so no library row, playlist or album lookup can match it. */
    const val ID_PREFIX = "file:"

    /**
     * The media id of the [n]th file played in this process. It does NOT carry the path: a media id is told to every
     * controller of the session, and the file may sit in a folder its owner hid.
     */
    fun mediaId(n: Long): String = "$ID_PREFIX$n"

    fun isFile(mediaId: String?): Boolean = mediaId != null && mediaId.startsWith(ID_PREFIX)

    /** The tag's title, else the file's name without its extension (a dot-file keeps its whole name). */
    fun title(tagTitle: String?, fileName: String): String {
        val tag = text(tagTitle)
        if (tag.isNotEmpty()) return tag
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    /** A tag as shown: trimmed, cut at a NUL, and blank when absent — a file's missing artist is not "Unknown artist". */
    fun text(tag: String?): String = tag.orEmpty().substringBefore('\u0000').trim()

    /** `[music] play file <path> (not in library)`, without its tag. */
    fun line(path: String): String = "play file $path (not in library)"

    /** The power-of-two sample size that brings a [width] x [height] picture's longer side to [max] or under. */
    fun sampleSize(width: Int, height: Int, max: Int): Int {
        var n = 1
        while (max > 0 && (maxOf(width, height) / n) > max) n *= 2
        return n
    }
}
