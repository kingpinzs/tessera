package app.tileshell.music.radio

import app.tileshell.net.jsonLong
import app.tileshell.net.jsonString
import java.util.Locale

/**
 * One station of the radio-browser.info directory as the phone keeps it: the 11 slim fields (phase 20 Decisions "the
 * station directory"; r11 addendum §1) out of the 30-odd the directory sends. The cache, the favourites file and the
 * directory's own answers all use the directory's field names, so ONE reader ([fromJson]) makes every station there
 * is — and it is the reader that makes the text fit to show ([RadioText.shown]) and the numbers sane, so nothing
 * downstream holds a raw directory string. Pure (`StationTest`).
 *
 * [url] and [urlResolved] are kept as sent: what may be played is [StationUrl]'s to say, at the tap.
 */
data class Station(
    val uuid: String,
    val name: String,
    val url: String,
    /** The directory's own resolution of [url] (playlists and redirects followed); empty on some rows. */
    val urlResolved: String,
    /** The logo's address; fetched by the shell for favourites and the playing station only ([StationLogo]). */
    val favicon: String,
    /** The genres, comma-separated as the directory sends them. */
    val tags: String,
    /** ISO 3166-1 alpha-2, upper case; empty when the directory has none. */
    val countryCode: String,
    val codec: String,
    val bitrate: Int,
    /** The directory's `hls=1`: [urlResolved] is an HLS playlist. */
    val hls: Boolean,
    /** The directory's popularity count: what every list is ordered by. */
    val clickCount: Int,
) {
    /** The genres as [RadioText.key]s. */
    fun tagKeys(): List<String> = tags.split(',').map { RadioText.key(it) }.filter { it.isNotEmpty() }

    /** The row as the cache and the favourites file hold it, under the directory's own field names. */
    fun toJson(): Map<String, Any?> = linkedMapOf(
        "stationuuid" to uuid,
        "name" to name,
        "url" to url,
        "url_resolved" to urlResolved,
        "favicon" to favicon,
        "tags" to tags,
        "countrycode" to countryCode,
        "codec" to codec,
        "bitrate" to bitrate.toLong(),
        "hls" to (if (hls) 1L else 0L),
        "clickcount" to clickCount.toLong(),
    )

    companion object {
        /** The longest address kept; a longer one is dropped (the row then has no such address). */
        const val URL_MAX = 2048
        const val TAGS_MAX = 255
        const val CODEC_MAX = 24

        /**
         * A uuid as the shell uses one: it becomes a path segment of the click call, the tail of a media id and of a
         * test tag, so it is letters, digits, `-` and `_` only.
         */
        fun isUuid(text: String?): Boolean =
            text != null && text.length in 1..64 && text.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

        /** One row → a station, or null when it has no usable uuid or no name left to show. */
        fun fromJson(row: Map<String, Any?>?): Station? {
            if (row == null) return null
            val uuid = row.jsonString("stationuuid")?.takeIf { isUuid(it) } ?: return null
            val name = RadioText.shown(row.jsonString("name"), RadioText.NAME_MAX).ifEmpty { return null }
            fun address(key: String) = row.jsonString(key).orEmpty().takeIf { it.length <= URL_MAX }.orEmpty()
            val country = row.jsonString("countrycode").orEmpty().uppercase(Locale.ROOT)
            return Station(
                uuid = uuid,
                name = name,
                url = address("url"),
                urlResolved = address("url_resolved"),
                favicon = address("favicon"),
                tags = RadioText.shown(row.jsonString("tags"), TAGS_MAX),
                countryCode = if (country.length == 2 && country.all { it in 'A'..'Z' }) country else "",
                codec = RadioText.shown(row.jsonString("codec"), CODEC_MAX),
                bitrate = (row.jsonLong("bitrate") ?: 0L).coerceIn(0L, 100_000L).toInt(),
                hls = row.jsonLong("hls") == 1L || row["hls"] == true,
                clickCount = (row.jsonLong("clickcount") ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            )
        }
    }
}
