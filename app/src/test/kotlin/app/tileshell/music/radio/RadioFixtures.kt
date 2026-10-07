package app.tileshell.music.radio

/** A station for the radio rules' tests: only what a test names differs from the plain row. */
fun station(
    uuid: String,
    name: String = "Station $uuid",
    url: String = "http://stream.example.net/$uuid",
    urlResolved: String = url,
    favicon: String = "",
    tags: String = "",
    country: String = "",
    codec: String = "MP3",
    bitrate: Int = 128,
    hls: Boolean = false,
    clicks: Int = 0,
) = Station(uuid, name, url, urlResolved, favicon, tags, country, codec, bitrate, hls, clicks)
