package app.tileshell.music.catalogue

import app.tileshell.video.catalogue.ImageHop
import java.util.Locale

/**
 * The Cover Art Archive's redirect chain, walked by hand (phase 20, r3 D13). TRUST-TOUCHING: `VideoHttp` follows no
 * redirect, and the archive answers `coverartarchive.org` 307 → `archive.org` 302 → `<node>.archive.org` 200 — so each
 * hop's `Location` is weighed by [mayFollow] before a request is made for it, at most [MAX_REDIRECTS] are followed,
 * and no header of the first request is carried to a later one.
 */
object CoverArt {
    const val MAX_REDIRECTS = 3

    private const val HOST = "archive.org"

    /**
     * Whether a redirect's `Location` is somewhere a cover-art fetch may go: an absolute `https` address whose host is
     * `archive.org` or a subdomain of it — the hosts the network security config keeps https-only. Anything else is
     * refused: plain http, a relative address, another host, a look-alike (`archive.org.evil.example`,
     * `evilarchive.org`), a user part, a port, a backslash or a control character.
     */
    fun mayFollow(location: String?): Boolean {
        val text = location ?: return false
        if (text.any { it <= ' ' || it == '\\' || it in '\u007f'..'\u009f' }) return false
        if (!text.startsWith("https://", ignoreCase = true)) return false
        val authority = text.substring("https://".length).takeWhile { it != '/' && it != '?' && it != '#' }.lowercase(Locale.ROOT)
        if (authority.isEmpty() || authority.any { it == '@' || it == ':' || it == '[' || it == '%' }) return false
        return authority == HOST || (authority.endsWith(".$HOST") && authority.split('.').none { it.isEmpty() })
    }

    /**
     * The image at [url], its redirects followed by hand. [hop] makes ONE request that follows nothing
     * (`VideoHttp.hop`). The first request carries [headers]; a followed one carries none. Null when the image cannot
     * be had: a failed request, a `Location` [mayFollow] refuses, or a fourth redirect.
     */
    fun fetch(url: String, headers: Map<String, String>, hop: (url: String, headers: Map<String, String>) -> ImageHop): ByteArray? {
        var at = url
        var carried = headers
        var followed = 0
        while (true) {
            when (val answer = hop(at, carried)) {
                is ImageHop.Body -> return answer.bytes
                ImageHop.Failed -> return null
                is ImageHop.Redirect -> {
                    if (followed == MAX_REDIRECTS || !mayFollow(answer.location)) return null
                    followed++
                    at = answer.location
                    carried = emptyMap()
                }
            }
        }
    }
}
