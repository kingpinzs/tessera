package app.tileshell.music.radio

import app.tileshell.music.radio.RadioDirectory.CacheRow
import app.tileshell.music.radio.RadioDirectory.Parsed
import app.tileshell.music.radio.RadioDirectory.What
import app.tileshell.net.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (build task 5; Decisions "the station directory", Q-20-1; r3 D5): what is asked, read, kept and searched. */
class RadioDirectoryTest {
    // ---- request

    @Test fun `the page URL for an offset - by click count, most first, broken hidden, 2000 at a time`() {
        assertEquals("json/stations?order=clickcount&reverse=true&hidebroken=true&limit=2000&offset=0", RadioDirectory.request(What.Page(0)))
        assertEquals("json/stations?order=clickcount&reverse=true&hidebroken=true&limit=2000&offset=2000", RadioDirectory.request(What.Page(2000)))
        assertEquals("json/stations?order=clickcount&reverse=true&hidebroken=true&limit=2000&offset=78000", RadioDirectory.request(What.Page(78_000)))
        assertEquals(RadioDirectory.request(What.Page(0)), RadioDirectory.request(What.Page(-4)))
        assertEquals(2_000, RadioDirectory.PAGE)
        // The doc's own form, on a mirror.
        assertEquals(
            "https://de1.api.radio-browser.info/json/stations?order=clickcount&reverse=true&hidebroken=true&limit=2000&offset=4000",
            RadioMirrors.base("de1.api.radio-browser.info") + RadioDirectory.request(What.Page(4000)),
        )
    }

    @Test fun `paging stops on a short page`() {
        assertEquals(2000, RadioDirectory.nextOffset(0, 2000))
        assertEquals(4000, RadioDirectory.nextOffset(2000, 2000))
        assertNull(RadioDirectory.nextOffset(0, 1999))
        assertNull(RadioDirectory.nextOffset(52_000, 817))
        assertNull(RadioDirectory.nextOffset(0, 0))
        assertNull(RadioDirectory.nextOffset(0, 4))
    }

    @Test fun `paging stops at 40 pages, however full they are`() {
        assertEquals(40, RadioDirectory.MAX_PAGES)
        var offset: Int? = 0
        val asked = ArrayList<Int>()
        while (offset != null) {
            asked += offset
            offset = RadioDirectory.nextOffset(offset, 2000)
        }
        assertEquals(40, asked.size)
        assertEquals((0 until 40).map { it * 2000 }, asked)
        assertEquals(78_000, RadioDirectory.nextOffset(76_000, 2000))
        assertNull(RadioDirectory.nextOffset(78_000, 2000))
        // The directory as it is today: 26 full pages and a short one — 27 requests.
        offset = 0
        var requests = 0
        var left = 52_817
        while (offset != null) {
            val rows = minOf(left, 2000)
            left -= rows
            requests++
            offset = RadioDirectory.nextOffset(offset, rows)
        }
        assertEquals(27, requests)
    }

    @Test fun `the tags and the countries URLs`() {
        assertEquals("json/tags?order=stationcount&reverse=true&limit=500", RadioDirectory.request(What.Tags))
        assertEquals("json/countries", RadioDirectory.request(What.Countries))
    }

    @Test fun `the byuuid refresh, 50 uuids a call, and only uuids`() {
        assertEquals("json/stations/byuuid?uuids=u-1,u-2", RadioDirectory.request(What.ByUuid(listOf("u-1", "u-2"))))
        assertEquals("json/stations/byuuid?uuids=u-1", RadioDirectory.request(What.ByUuid(listOf("u-1", "x&limit=9", "a,b", "../x", ""))))
        assertEquals(50, RadioDirectory.request(What.ByUuid((1..80).map { "u$it" })).substringAfter("uuids=").split(',').size)
        assertEquals(50, RadioDirectory.UUIDS_PER_CALL)
    }

    @Test fun `the click URL, and nothing but a uuid goes into its path`() {
        assertEquals("json/url/960594a6-0601-11e8-ae97-52543be04c81", RadioDirectory.request(What.Click("960594a6-0601-11e8-ae97-52543be04c81")))
        for (bad in listOf("../../json/vote/u-1", "u-1?x=1", "u-1/..", "a b", "")) assertEquals(bad, "json/url/", RadioDirectory.request(What.Click(bad)))
    }

    @Test fun `no search URL exists - everything the directory is ever asked is one of five forms`() {
        val every: List<What> = listOf(What.Page(0), What.Tags, What.Countries, What.ByUuid(listOf("u-1")), What.Click("u-1"))
        // A sixth form would not compile into this `when` without a branch: it is exhaustive over the sealed type.
        for (what in every) {
            val kind = when (what) {
                is What.Page -> "page"
                What.Tags -> "tags"
                What.Countries -> "countries"
                is What.ByUuid -> "byuuid"
                is What.Click -> "click"
            }
            val url = RadioDirectory.request(what)
            assertTrue("$kind $url", url.startsWith("json/"))
            for (search in listOf("search", "byname", "bytag", "bycountry", "name=", "tag=")) assertFalse("$kind $url", url.contains(search))
        }
    }

    // ---- parse

    private fun row(uuid: String, name: String = "S $uuid", codec: String = "MP3", resolved: String = "http://a.example.net/$uuid", clicks: Int = 1, extra: String = "") =
        """{"stationuuid":"$uuid","name":"$name","url":"http://a.example.net/$uuid.pls","url_resolved":"$resolved","homepage":"https://a.example.net/","favicon":"","tags":"jazz","country":"Germany","countrycode":"DE","codec":"$codec","bitrate":128,"hls":0,"lastcheckok":1,"votes":3,"clickcount":$clicks$extra}"""

    private fun parse(vararg rows: String, cap: Long = RadioDirectory.MAX_RESPONSE_BYTES) = RadioDirectory.parse(("[" + rows.joinToString(",") + "]").toByteArray(), cap)

    @Test fun `an answer is read to the 11 slim fields`() {
        val parsed = parse(row("u-1", clicks = 9), row("u-2")) as Parsed.Rows
        assertEquals(2, parsed.rowCount)
        assertEquals(
            Station("u-1", "S u-1", "http://a.example.net/u-1.pls", "http://a.example.net/u-1", "", "jazz", "DE", "MP3", 128, false, 9),
            parsed.stations[0],
        )
        assertEquals(listOf("u-1", "u-2"), parsed.stations.map { it.uuid })
        assertEquals(11, parsed.stations[0].toJson().size)
    }

    @Test fun `over the byte cap is too large - nothing of it is read, so the cache is kept`() {
        assertEquals(4L * 1024 * 1024, RadioDirectory.MAX_RESPONSE_BYTES)
        val body = ("[" + row("u-1") + "]").toByteArray()
        assertEquals(Parsed.TooLarge, RadioDirectory.parse(body, cap = body.size - 1L))
        assertTrue(RadioDirectory.parse(body, cap = body.size.toLong()) is Parsed.Rows)
        assertEquals(Parsed.TooLarge, RadioDirectory.parse(ByteArray(4 * 1024 * 1024 + 1) { ' '.code.toByte() }))
        // The size is judged before the text: an over-long answer that is not even JSON is too large, not malformed.
        assertEquals(Parsed.TooLarge, RadioDirectory.parse("not json at all".toByteArray(), cap = 4))
        assertEquals("radio: directory too large", RadioLine.TOO_LARGE)
    }

    @Test fun `what is not a list of rows is malformed, and an empty list is no rows`() {
        for (body in listOf("", "{}", "null", "[", "{\"error\":\"x\"}", "<html>503</html>")) assertEquals(body, Parsed.Malformed, RadioDirectory.parse(body.toByteArray()))
        assertEquals(Parsed.Rows(emptyList(), 0), RadioDirectory.parse("[]".toByteArray()))
        // A row that is no station is skipped, and still counts as a row of the page.
        val parsed = parse(row("u-1"), "7", "{\"name\":\"no uuid\"}", "null") as Parsed.Rows
        assertEquals(listOf("u-1"), parsed.stations.map { it.uuid })
        assertEquals(4, parsed.rowCount)
    }

    @Test fun `video-codec rows are dropped, and counted - paging goes by what the answer held`() {
        val parsed = parse(row("a", codec = "MP3"), row("v1", codec = "H.264"), row("v2", codec = "AAC,H.264"), row("v3", codec = "h264"), row("v4", codec = "HEVC"), row("b", codec = "AAC+"), row("c", codec = "UNKNOWN"), row("d", codec = "OGG"), row("e", codec = "")) as Parsed.Rows
        assertEquals(listOf("a", "b", "c", "d", "e"), parsed.stations.map { it.uuid })
        assertEquals(9, parsed.rowCount)
        for (audio in listOf("MP3", "AAC", "AAC+", "OGG", "OPUS", "FLAC", "UNKNOWN", "", "WMA", "MP4A")) assertFalse(audio, RadioDirectory.isVideo(audio))
        for (video in listOf("H.264", "AAC,H.264", "H264", "h.265", "HEVC", "VP9", "AV1", "MP3/H.264")) assertTrue(video, RadioDirectory.isVideo(video))
    }

    @Test fun `an empty url_resolved is kept for StationUrl playable`() {
        val s = (parse(row("u-1", resolved = "")) as Parsed.Rows).stations.single()
        assertEquals("", s.urlResolved)
        assertEquals(StationUrl.Playable.Playlist, StationUrl.playable(s.urlResolved, s.url, s.hls))
        val plain = (parse("""{"stationuuid":"u-2","name":"B","url":"http://a.example.net/live","url_resolved":""}""") as Parsed.Rows).stations.single()
        assertEquals(StationUrl.Playable.Stream("http://a.example.net/live", null), StationUrl.playable(plain.urlResolved, plain.url, plain.hls))
    }

    @Test fun `hostile names come out through RadioText shown`() {
        val s = (parse(row("u-1", name = "\\u202eEvil\\n[music] stream: connected\\u0000 " + "x".repeat(300))) as Parsed.Rows).stations.single()
        assertEquals(RadioText.shown(s.name, RadioText.NAME_MAX), s.name)
        assertEquals(80, s.name.length)
        assertTrue(s.name.startsWith("Evil[music] stream: connected x"))
        assertTrue(s.name.none { it == '\n' || it == '‮' || it == '\u0000' })
    }

    @Test fun `the tags and the countries answers`() {
        val tags = RadioDirectory.parseTags("""[{"name":"pop","stationcount":5000},{"name":" Jazz ","stationcount":900},{"name":"","stationcount":3},{"stationcount":1},{"name":"x\ny","stationcount":-2}]""".toByteArray())
        assertEquals(listOf(RadioTag("pop", 5000), RadioTag("jazz", 900), RadioTag("xy", 0)), tags)
        assertEquals(500, RadioDirectory.parseTags(("[" + (1..700).joinToString(",") { """{"name":"t$it","stationcount":1}""" } + "]").toByteArray())!!.size)
        val countries = RadioDirectory.parseCountries("""[{"name":"The United States Of America","iso_3166_1":"US","stationcount":7000},{"name":"Germany","iso_3166_1":"de","stationcount":5000},{"name":"Nowhere","iso_3166_1":"","stationcount":1},{"name":"","iso_3166_1":"XX","stationcount":1}]""".toByteArray())
        assertEquals(listOf(RadioCountry("Germany", "DE", 5000), RadioCountry("The United States Of America", "US", 7000)), countries)
        assertNull(RadioDirectory.parseTags("{}".toByteArray()))
        assertNull(RadioDirectory.parseCountries("oops".toByteArray()))
        assertNull(RadioDirectory.parseTags("[]".toByteArray(), cap = 1))
        assertNull(RadioDirectory.parseCountries("[]".toByteArray(), cap = 1))
    }

    // ---- the cache file

    @Test fun `a cache line reads back as what was written - a station, a genre, a country - one object a line`() {
        val s = station("u-1", "Line Break \"Q\" \\ ü", tags = "jazz,blues", country = "DE", clicks = 12, hls = true)
        val lines = listOf(RadioDirectory.cacheLine(s), RadioDirectory.cacheLine(RadioTag("jazz", 900)), RadioDirectory.cacheLine(RadioCountry("Germany", "DE", 5000)))
        for (line in lines) assertFalse(line, line.contains('\n'))
        assertEquals(CacheRow.OfStation(s), RadioDirectory.cacheRow(lines[0]))
        assertEquals(CacheRow.OfTag(RadioTag("jazz", 900)), RadioDirectory.cacheRow(lines[1]))
        assertEquals(CacheRow.OfCountry(RadioCountry("Germany", "DE", 5000)), RadioDirectory.cacheRow(lines[2]))
        assertEquals("radio_directory_v1.json", RadioDirectory.FILE_NAME)
    }

    @Test fun `a line that is no row is skipped, never thrown on - a torn write, another version's line`() {
        for (line in listOf("", "{", "[]", "7", "{\"stationuuid\":\"u-1\"", "{\"v\":2}", "{\"tag\":\"\"}", "{\"country\":\"X\",\"code\":\"XYZ\"}", "{\"stationuuid\":\"u-1\"}")) {
            assertNull(line, RadioDirectory.cacheRow(line))
        }
    }

    @Test fun `the cache file is capped at 32 MiB, the tail's room kept`() {
        assertEquals(32L * 1024 * 1024, RadioDirectory.MAX_CACHE_BYTES)
        val room = RadioDirectory.MAX_CACHE_BYTES - RadioDirectory.CACHE_TAIL_BYTES
        assertTrue(RadioDirectory.fits(0, 400))
        assertTrue(RadioDirectory.fits(room - 400, 400))
        assertFalse(RadioDirectory.fits(room - 399, 400))
        assertFalse(RadioDirectory.fits(RadioDirectory.MAX_CACHE_BYTES, 1))
        // The slim directory of today (about 53,000 rows of about 400 bytes) fits with room to spare.
        assertTrue(RadioDirectory.fits(53_000L * 400, 400))
    }

    @Test fun `refresh - by itself only when stale or missing and unmetered, on demand on any network`() {
        val day = 24L * 60 * 60 * 1000
        assertEquals(7 * day, RadioDirectory.REFRESH_MS)
        // By itself, unmetered: no cache, or one older than 7 days.
        assertTrue(RadioDirectory.refreshDue(cacheAgeMs = null, metered = false, onDemand = false))
        assertTrue(RadioDirectory.refreshDue(7 * day + 1, metered = false, onDemand = false))
        assertFalse(RadioDirectory.refreshDue(7 * day, metered = false, onDemand = false))
        assertFalse(RadioDirectory.refreshDue(0, metered = false, onDemand = false))
        assertFalse(RadioDirectory.refreshDue(6 * day, metered = false, onDemand = false))
        // A clock set back since the fetch: the age is not to be trusted, so it is fetched again.
        assertTrue(RadioDirectory.refreshDue(-5, metered = false, onDemand = false))
        // Never by itself on mobile data — not when stale, not even with nothing cached.
        assertFalse(RadioDirectory.refreshDue(30 * day, metered = true, onDemand = false))
        assertFalse(RadioDirectory.refreshDue(null, metered = true, onDemand = false))
        // On demand: any network, any age.
        for (metered in listOf(false, true)) for (age in listOf(null, 0L, day, 30 * day)) assertTrue(RadioDirectory.refreshDue(age, metered, onDemand = true))
    }

    // ---- search / byTag / byCountry

    private val jazzOne = station("jazz-one", "QA Jazz One", tags = "jazz,smooth", country = "US", clicks = 90)
    private val jazzTwo = station("jazz-two", "QA Jazz Two", tags = "Jazz", country = "DE", clicks = 40)
    private val newsOne = station("news-one", "QA News One", tags = "news,talk", country = "US", clicks = 70)
    private val file = station("file", "QA File", tags = "", country = "", clicks = 5)
    private val index = RadioIndex.of(listOf(jazzTwo, file, newsOne, jazzOne))

    @Test fun `the index orders by popularity, each station once`() {
        assertEquals(listOf(jazzOne, newsOne, jazzTwo, file), index.stations)
        assertEquals(4, index.size)
        assertEquals(2, RadioIndex.of(listOf(jazzOne, jazzOne.copy(name = "Again"), file)).size)
        assertEquals(0, RadioIndex.EMPTY.size)
        // Ties keep the order they came in (the directory's own).
        val tied = (1..5).map { station("t$it", clicks = 3) }
        assertEquals(tied, RadioIndex.of(tied).stations)
        assertEquals(jazzOne, index.byUuid("jazz-one"))
        assertNull(index.byUuid("nobody"))
    }

    @Test fun `search is by name over the whole cached set, by popularity`() {
        assertEquals(listOf(jazzOne, jazzTwo), RadioDirectory.search("jazz", index, emptyList()))
        assertEquals(listOf(jazzOne, jazzTwo), RadioDirectory.search("  JAZZ ", index, emptyList()))
        assertEquals(listOf(jazzOne, newsOne, jazzTwo, file), RadioDirectory.search("qa", index, emptyList()))
        assertEquals(listOf(jazzOne, newsOne), RadioDirectory.search("one", index, emptyList()))
        assertEquals(listOf(newsOne), RadioDirectory.search("qa  news", index, emptyList()))
        assertEquals(emptyList<Station>(), RadioDirectory.search("zzqx", index, emptyList()))
        assertEquals(emptyList<Station>(), RadioDirectory.search("", index, listOf(jazzOne)))
        assertEquals(emptyList<Station>(), RadioDirectory.search("   ", index, emptyList()))
        // The name, not the genre: "talk" is News One's tag and no station's name.
        assertEquals(emptyList<Station>(), RadioDirectory.search("talk", index, emptyList()))
    }

    @Test fun `favourites come first, in their own order, and each station is listed once`() {
        assertEquals(listOf(jazzTwo, jazzOne), RadioDirectory.search("jazz", index, listOf(newsOne, jazzTwo)))
        assertEquals(listOf(file, jazzTwo, jazzOne, newsOne), RadioDirectory.search("qa", index, listOf(file, jazzTwo)))
        // A favourite the directory no longer lists (hidden as broken) is still found.
        val gone = station("gone", "Old Jazz Cellar", tags = "jazz", country = "US", clicks = 0)
        assertEquals(listOf(gone, jazzOne, jazzTwo), RadioDirectory.search("jazz", index, listOf(gone)))
        assertEquals(listOf(gone, jazzOne, jazzTwo), RadioDirectory.byTag("jazz", index, listOf(gone, gone)))
        assertEquals(listOf(gone, jazzOne, newsOne), RadioDirectory.byCountry("us", index, listOf(gone)))
    }

    @Test fun `by genre - the whole tag, whatever its case, by popularity`() {
        assertEquals(listOf(jazzOne, jazzTwo), RadioDirectory.byTag("jazz", index, emptyList()))
        assertEquals(listOf(jazzOne, jazzTwo), RadioDirectory.byTag(" JAZZ ", index, emptyList()))
        assertEquals(listOf(newsOne), RadioDirectory.byTag("talk", index, emptyList()))
        assertEquals(emptyList<Station>(), RadioDirectory.byTag("jaz", index, emptyList()))
        assertEquals(emptyList<Station>(), RadioDirectory.byTag("", index, emptyList()))
        assertEquals(listOf(jazzTwo, jazzOne), RadioDirectory.byTag("jazz", index, listOf(jazzTwo)))
    }

    @Test fun `by country - the ISO code, by popularity`() {
        assertEquals(listOf(jazzOne, newsOne), RadioDirectory.byCountry("US", index, emptyList()))
        assertEquals(listOf(jazzTwo), RadioDirectory.byCountry("de", index, emptyList()))
        assertEquals(emptyList<Station>(), RadioDirectory.byCountry("FR", index, emptyList()))
        assertEquals(emptyList<Station>(), RadioDirectory.byCountry("", index, emptyList()))
        assertEquals(listOf(newsOne, jazzOne), RadioDirectory.byCountry("US", index, listOf(newsOne)))
    }

    @Test fun `results are listed 100 at a time - over a directory of 53,000`() {
        assertEquals(100, RadioDirectory.LIST)
        val many = (1..53_000).map { station("s$it", if (it % 2 == 0) "Rock $it" else "Jazz $it", tags = if (it % 2 == 0) "rock" else "jazz", country = if (it % 4 == 0) "DE" else "US", clicks = it) }
        val big = RadioIndex.of(many)
        assertEquals(53_000, big.size)
        assertEquals("s53000", big.stations.first().uuid)
        val first = RadioDirectory.search("jazz", big, emptyList())
        assertEquals(100, first.size)
        assertEquals((0 until 100).map { "s${52_999 - 2 * it}" }, first.map { it.uuid })
        val second = RadioDirectory.search("jazz", big, emptyList(), offset = 100)
        assertEquals((100 until 200).map { "s${52_999 - 2 * it}" }, second.map { it.uuid })
        // The last page is short, and past the end there is nothing.
        assertEquals(26_500 % 100, RadioDirectory.search("jazz", big, emptyList(), offset = 26_500).size)
        assertEquals(emptyList<Station>(), RadioDirectory.search("jazz", big, emptyList(), offset = 26_500))
        assertEquals(100, RadioDirectory.search("jazz", big, emptyList(), offset = 26_400).size)
        assertEquals(100, RadioDirectory.byTag("rock", big, emptyList()).size)
        assertEquals("s53000", RadioDirectory.byTag("rock", big, emptyList()).first().uuid)
        assertEquals((0 until 100).map { "s${53_000 - 4 * it}" }, RadioDirectory.byCountry("DE", big, emptyList()).map { it.uuid })
        // The favourites are part of the first hundred, not beside it.
        val fav = many[100]
        val withFav = RadioDirectory.search("jazz", big, listOf(fav))
        assertEquals(100, withFav.size)
        assertEquals(fav, withFav.first())
        assertEquals(1, RadioDirectory.search("jazz", big, listOf(fav), offset = 0).count { it.uuid == fav.uuid } + RadioDirectory.search("jazz", big, listOf(fav), offset = 26_400).count { it.uuid == fav.uuid })
    }

    @Test fun `the index's lookups for Tess - the exact name and the genre's most popular station`() {
        assertEquals(jazzOne, index.exactName(RadioText.key("qa JAZZ one")))
        assertNull(index.exactName(RadioText.key("qa jazz")))
        assertEquals(jazzOne, index.ofTag("jazz").first())
        assertEquals(jazzOne, index.nameContains("jazz").first())
        assertEquals(emptyList<Station>(), index.ofTag("polka").toList())
        // Two stations of one name: the more popular one.
        val twins = RadioIndex.of(listOf(station("a", "Twin", clicks = 1), station("b", "Twin", clicks = 8)))
        assertEquals("b", twins.exactName("twin")!!.uuid)
    }

    @Test fun `the pivot's two texts are the doc's`() {
        assertEquals("No connection — stations need the internet", RadioDirectory.OFFLINE_NOTE)
        assertEquals("No connection yet — stations will appear when there is one", RadioDirectory.EMPTY_OFFLINE)
    }

    @Test fun `a page written as cache lines reads back as the page`() {
        val page = (parse(row("u-1", clicks = 3), row("u-2", name = "Two \\\"2\\\"", clicks = 2)) as Parsed.Rows).stations
        val back = page.map { RadioDirectory.cacheLine(it) }.mapNotNull { (RadioDirectory.cacheRow(it) as? CacheRow.OfStation)?.station }
        assertEquals(page, back)
        assertEquals(page[1].toJson(), MiniJson.parse(RadioDirectory.cacheLine(page[1])))
    }
}
