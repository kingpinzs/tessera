package app.tileshell.music.radio

/**
 * What a station shows while it plays (phase 20 Decisions "live metadata", r3 D9; r11 addendum §4). The station item
 * sets `albumTitle` and `artist` to the station's name and never `title`; Media3 puts a progressive stream's ICY
 * StreamTitle in `title`, and an HLS station sends none. So the session's player wrapper asks this for every
 * `station:` item: the title is the song now on air when the stream names one, else the station's name — and the
 * album line is always the station. Pure (`LiveMetadataTest`).
 */
object LiveMetadata {
    data class Shown(val title: String, val albumTitle: String)

    fun merge(stationName: String?, icyTitle: String?): Shown {
        val station = RadioText.shown(stationName, RadioText.NAME_MAX)
        // A StreamTitle is whatever the stream's operator typed: it is shown only through RadioText.
        val song = RadioText.shown(icyTitle, RadioText.TITLE_MAX)
        return Shown(title = song.ifEmpty { station }, albumTitle = station)
    }
}
