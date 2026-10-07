package app.tileshell.music.radio

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.tileshell.music.MusicLive

/**
 * The ONLY place a station's `MediaItem` is built (phase 20 build task 3; r3 D1 / D2 / D9 / D10 / D11; a trust
 * rule). `MusicService` is exported and keeps an item's URI only for the shell's own controller (`MusicItemRule`), so
 * what the shell itself hands its player is decided here and nowhere else:
 *
 *  - the media id is `station:<uuid>` — never a decimal, so no library lookup matches it;
 *  - the URI is [StationUrl.playable]'s pick, and only after [StationUrl.accept] accepts it (`http` / `https`, no
 *    private host); a refused station gets no item at all, and the caller writes [Plan.Refused.line] and shows
 *    [CANT_PLAY];
 *  - an `hls=1` row is given the m3u8 MIME type;
 *  - `albumTitle` and `artist` are the station's name through [RadioText.shown]. `title` is NEVER set: Media3 puts
 *    the stream's own StreamTitle there, and an item's own metadata would win over it ([LiveMetadata]);
 *  - `artworkUri` is NEVER set: the session would load it itself, from any scheme, uncapped. A logo arrives as bytes
 *    the shell fetched and bounded ([StationLogos]);
 *  - no `durationMs`: a station has no length, and "live" is the id's mark ([MusicLive.isLive]).
 *
 * [plan] is the whole decision and is pure (`StationItemTest`); [build] only pours it into Media3's builders.
 */
object StationItem {
    /** What a row says when its station was refused. */
    const val CANT_PLAY = "can't play this station"

    sealed interface Plan {
        data class Play(val mediaId: String, val url: String, val mimeType: String?, val name: String) : Plan

        /** No item is built; [line] is the `[music]` line that says why ([StreamLine]). */
        data class Refused(val line: String) : Plan
    }

    sealed interface Built {
        data class Item(val item: MediaItem) : Built
        data class Refused(val line: String) : Built
    }

    fun plan(station: Station, qaHost: String?): Plan {
        val stream = when (val p = StationUrl.playable(station.urlResolved, station.url, station.hls)) {
            StationUrl.Playable.Playlist -> return Plan.Refused(StreamLine.UNSUPPORTED_PLAYLIST)
            StationUrl.Playable.None -> return Plan.Refused(StreamLine.unsupportedScheme(null))
            is StationUrl.Playable.Stream -> p
        }
        return when (val a = StationUrl.accept(stream.url, qaHost)) {
            is StationUrl.Accept.UnsupportedScheme -> Plan.Refused(StreamLine.unsupportedScheme(a.scheme))
            StationUrl.Accept.UnsupportedHost -> Plan.Refused(StreamLine.UNSUPPORTED_HOST)
            StationUrl.Accept.Ok -> Plan.Play(
                mediaId = MusicLive.stationId(station.uuid),
                url = stream.url,
                mimeType = stream.mimeType,
                name = RadioText.shown(station.name, RadioText.NAME_MAX),
            )
        }
    }

    /**
     * The station's item, or the refusal. [logo] is [StationLogos]' bounded bytes when the caller has them (a
     * favourite, the playing station), else null and the page draws the placeholder.
     */
    fun build(station: Station, qaHost: String?, logo: ByteArray? = null): Built {
        val play = when (val p = plan(station, qaHost)) {
            is Plan.Refused -> return Built.Refused(p.line)
            is Plan.Play -> p
        }
        val meta = MediaMetadata.Builder()
            .setAlbumTitle(play.name)
            .setArtist(play.name)
            .setIsBrowsable(false)
            .setIsPlayable(true)
        logo?.let { meta.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        val item = MediaItem.Builder()
            .setMediaId(play.mediaId)
            .setUri(play.url)
            .setMediaMetadata(meta.build())
        play.mimeType?.let { item.setMimeType(it) }
        return Built.Item(item.build())
    }

    /** A queue's items: every station that may play, in order, and the lines of those that may not. */
    fun buildAll(stations: List<Station>, qaHost: String?): Pair<List<MediaItem>, List<String>> {
        val built = stations.map { build(it, qaHost) }
        return built.filterIsInstance<Built.Item>().map { it.item } to built.filterIsInstance<Built.Refused>().map { it.line }
    }
}
