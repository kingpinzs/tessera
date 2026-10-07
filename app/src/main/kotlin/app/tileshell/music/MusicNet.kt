package app.tileshell.music

/**
 * What the Music app's own requests say about themselves (phase 20; r11 addendum §1, §2): radio-browser asks for "a
 * speaking http agent string", and MusicBrainz throttles a blank one or a library's default (`Java/…`, `Dalvik/…`)
 * as anonymous. Pure (`MusicNetTest`); the form is phase 17's (`StreamingHandoff`'s Wikidata header).
 */
object MusicNet {
    const val APP = "Tessera"

    /** The version when the build has none to give. */
    const val NO_VERSION = "0"

    /**
     * `Tessera/<version> (personal launcher; Music)`. The version keeps only what a product token may hold (letters,
     * digits, `.`, `_`, `-`), so nothing in it can end the header or start another.
     */
    fun userAgent(version: String?): String {
        val v = version.orEmpty().filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "._-" }.take(32)
        return "$APP/${v.ifEmpty { NO_VERSION }} (personal launcher; Music)"
    }

    /** The one header every directory and catalogue request carries. No credential is ever among them. */
    fun headers(version: String?): Map<String, String> = mapOf("User-Agent" to userAgent(version))
}
