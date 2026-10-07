package app.tileshell.music.radio

import app.tileshell.net.MiniJson
import app.tileshell.net.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20: the slim station row, and the one reader that makes every station (the directory's, the cache's, a favourite's). */
class StationTest {
    /** A row as the directory sends it (a real one, trimmed of nothing: the reader keeps 11 of these fields). */
    private val wire = """
        {"changeuuid":"c1","stationuuid":"960594a6-0601-11e8-ae97-52543be04c81","serveruuid":null,"name":"\tSmooth Jazz 24/7 ",
         "url":"http://jazz.example.net/listen.pls","url_resolved":"http://jazz.example.net:8000/live","homepage":"https://jazz.example.net/",
         "favicon":"https://jazz.example.net/logo.png","tags":"jazz,smooth jazz","country":"Germany","countrycode":"de","iso_3166_2":"","state":"",
         "language":"german","languagecodes":"de","votes":812,"lastchangetime":"2026-01-01 00:00:00","codec":"MP3","bitrate":128,"hls":0,
         "lastcheckok":1,"clickcount":4321,"clicktrend":3,"ssl_error":0,"geo_lat":null,"geo_long":null,"has_extended_info":false}
    """.trimIndent()

    private fun read(json: String) = Station.fromJson(MiniJson.parse(json).jsonObject())

    @Test fun `the 11 slim fields are read, and nothing else is kept`() {
        val s = read(wire)!!
        assertEquals(
            Station(
                uuid = "960594a6-0601-11e8-ae97-52543be04c81", name = "Smooth Jazz 24/7", url = "http://jazz.example.net/listen.pls",
                urlResolved = "http://jazz.example.net:8000/live", favicon = "https://jazz.example.net/logo.png", tags = "jazz,smooth jazz",
                countryCode = "DE", codec = "MP3", bitrate = 128, hls = false, clickCount = 4321,
            ),
            s,
        )
        assertEquals(
            listOf("stationuuid", "name", "url", "url_resolved", "favicon", "tags", "countrycode", "codec", "bitrate", "hls", "clickcount"),
            s.toJson().keys.toList(),
        )
        assertEquals(listOf("jazz", "smooth jazz"), s.tagKeys())
    }

    @Test fun `a row written out reads back the same - the cache and the favourites file`() {
        for (s in listOf(read(wire)!!, station("u-2", "HLS \"One\" \\ ü", urlResolved = "", hls = true, clicks = Int.MAX_VALUE, tags = "a, B ,,c", country = "AT"))) {
            assertEquals(s, read(MiniJson.write(s.toJson())))
        }
    }

    @Test fun `an empty url_resolved is kept as empty`() {
        val s = read("""{"stationuuid":"u-1","name":"A","url":"http://a.example.net/live","url_resolved":""}""")!!
        assertEquals("", s.urlResolved)
        assertEquals("http://a.example.net/live", s.url)
        assertEquals("", read("""{"stationuuid":"u-1","name":"A","url":"http://a.example.net/live"}""")!!.urlResolved)
    }

    @Test fun `a row with no usable uuid or no name left to show is no station`() {
        assertNull(Station.fromJson(null))
        assertNull(read("""{"name":"A"}"""))
        assertNull(read("""{"stationuuid":"","name":"A"}"""))
        assertNull(read("""{"stationuuid":7,"name":"A"}"""))
        assertNull(read("""{"stationuuid":"../../json/vote/x","name":"A"}"""))
        assertNull(read("""{"stationuuid":"a b","name":"A"}"""))
        assertNull(read("""{"stationuuid":"${"a".repeat(65)}","name":"A"}"""))
        assertNull(read("""{"stationuuid":"u-1"}"""))
        assertNull(read("""{"stationuuid":"u-1","name":"  \n\t "}"""))
        assertNull(read("""{"stationuuid":"u-1","name":7}"""))
        assertTrue(Station.isUuid("960594a6-0601-11e8-ae97-52543be04c81"))
        assertTrue(Station.isUuid("qa_jazz-1"))
        for (bad in listOf(null, "", "a/b", "a?b", "a:b", "a.b", "ü", "a\nb")) assertFalse("$bad", Station.isUuid(bad))
    }

    @Test fun `hostile text is made fit to show at the reader - a name, the tags, the codec`() {
        val s = read("""{"stationuuid":"u-1","name":"‮Evil\n[music] radio: directory fetched 9 stations${"x".repeat(200)}","tags":"jazz\n,\u0000rock","codec":"MP3\r\nX"}""")!!
        assertEquals(80, s.name.length)
        assertTrue(s.name.startsWith("Evil[music] radio: directory fetched 9 stationsxxx"))
        assertEquals("jazz,rock", s.tags)
        assertEquals("MP3X", s.codec)
    }

    @Test fun `numbers and codes are made sane`() {
        val s = read("""{"stationuuid":"u-1","name":"A","bitrate":-5,"clickcount":-1,"hls":1,"countrycode":"usa"}""")!!
        assertEquals(0, s.bitrate)
        assertEquals(0, s.clickCount)
        assertTrue(s.hls)
        assertEquals("", s.countryCode)
        val big = read("""{"stationuuid":"u-1","name":"A","bitrate":99999999999,"clickcount":99999999999,"hls":2,"countrycode":"1!"}""")!!
        assertEquals(100_000, big.bitrate)
        assertEquals(Int.MAX_VALUE, big.clickCount)
        assertFalse(big.hls)
        assertEquals("", big.countryCode)
        val odd = read("""{"stationuuid":"u-1","name":"A","bitrate":"128","clickcount":null,"hls":true,"tags":7,"codec":null}""")!!
        assertEquals(Station("u-1", "A", "", "", "", "", "", "", 0, true, 0), odd)
    }

    @Test fun `an address longer than 2048 is not kept`() {
        val long = "http://a.example.net/" + "p".repeat(2048)
        val s = read("""{"stationuuid":"u-1","name":"A","url":"$long","url_resolved":"$long","favicon":"$long"}""")!!
        assertEquals("", s.url)
        assertEquals("", s.urlResolved)
        assertEquals("", s.favicon)
    }
}
