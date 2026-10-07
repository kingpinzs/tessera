package app.tileshell.music.radio

import app.tileshell.music.MusicFile

/**
 * A station's logo (phase 20, r3 D2; a trust rule). A logo's address is any host's, so the item NEVER carries it as
 * `artworkUri` — Media3's session would load that itself, from any scheme its data source opens, with no cap. The
 * shell fetches it ([StationLogos]): `http` / `https` only, by [StationUrl.accept]'s whole rule; at most [MAX_BYTES];
 * decoded at bounds no larger than [MAX_PX] a side, as `MusicService.boundedArt` does a tag's picture; and only for
 * favourites and the playing station, so browsing never fans out to thousands of hosts. Pure (`StationLogoTest`).
 */
object StationLogo {
    const val MAX_BYTES = 512 * 1024
    const val MAX_PX = 512

    /** A picture declaring a side longer than this is not decoded at all: a small file can declare any size. */
    const val MAX_SOURCE_PX = 8192

    /** The address the logo is fetched from, or null: no logo, or an address [StationUrl.accept] refuses. */
    fun url(favicon: String?, qaHost: String?): String? =
        favicon?.trim()?.takeIf { it.isNotEmpty() && StationUrl.accept(it, qaHost) == StationUrl.Accept.Ok }

    /** Whether the fetched bytes may be decoded: something, and no more than [MAX_BYTES]. */
    fun accept(bytes: ByteArray?): Boolean = bytes != null && bytes.isNotEmpty() && bytes.size <= MAX_BYTES

    /**
     * The power-of-two sample size that brings a [width] x [height] picture's longer side to [MAX_PX] or under; 0 —
     * do not decode — when the bounds are not a picture's or are past [MAX_SOURCE_PX].
     */
    fun sampleSize(width: Int, height: Int): Int =
        if (width <= 0 || height <= 0 || width > MAX_SOURCE_PX || height > MAX_SOURCE_PX) 0 else MusicFile.sampleSize(width, height, MAX_PX)
}
