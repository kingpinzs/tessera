package app.tileshell.video

import app.tileshell.video.catalogue.CatalogueCache
import app.tileshell.video.catalogue.CatalogueItem
import app.tileshell.video.catalogue.CatalogueNotice
import app.tileshell.video.catalogue.CatalogueRules
import app.tileshell.video.catalogue.FetchOutcome
import app.tileshell.video.catalogue.TitleType
import app.tileshell.video.catalogue.TmdbParse
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 build task 12: TMDB's answers parsed from the fixture's recorded JSON (the files the catalogue fixture
 * serves), the 7-day cache rule, the diagnostics lines and the notices.
 */
class CatalogueRulesTest {
    private fun fixture(name: String): String {
        val rel = "docs/plan/qa/phase-17/fixtures/catalogue/$name"
        return listOf(File(rel), File("../$rel")).first { it.exists() }.readText()
    }

    @Test fun `the search answer is the three titles, the person row dropped`() {
        val items = TmdbParse.items(fixture("search_blade_runner.json"))
        assertEquals(
            listOf("Blade Runner" to "1982", "Blade Runner 2049" to "2017", "Blade Runner: Black Lotus" to "2021"),
            items.map { it.title to it.year },
        )
        assertEquals(listOf(TitleType.MOVIE, TitleType.MOVIE, TitleType.TV), items.map { it.type })
        assertEquals(listOf("78", "335984", "tv-117884"), items.map { it.key })
        assertEquals("/qa-poster-78.png", items[0].posterPath)
        assertTrue(items[0].overview.startsWith("In the smog-choked"))
    }

    @Test fun `a title with no artwork has no poster path, and no result is an empty list`() {
        assertNull(TmdbParse.items(fixture("search_no_artwork.json")).single().posterPath)
        assertEquals(emptyList<CatalogueItem>(), TmdbParse.items(fixture("search_empty.json")))
        assertEquals(emptyList<CatalogueItem>(), TmdbParse.items("not json"))
        assertEquals(emptyList<CatalogueItem>(), TmdbParse.items("""{"results": 7}"""))
        assertEquals(emptyList<CatalogueItem>(), TmdbParse.items("""{"results": [{"media_type":"movie"}, {"id":"x","media_type":"movie","title":"t"}, 5]}"""))
    }

    @Test fun `a typed list needs its default type, and a year is four digits or nothing`() {
        val body = """{"results":[{"id":1,"title":"A","release_date":"1999-03-31"},{"id":2,"title":"B","release_date":""},{"id":3,"title":"C","release_date":"n/a"}]}"""
        assertEquals(listOf("1999", "", ""), TmdbParse.items(body, TitleType.MOVIE).map { it.year })
        assertEquals(emptyList<CatalogueItem>(), TmdbParse.items(body))
        val tv = """{"results":[{"id":9,"name":"S","first_air_date":"2021-11-13"}]}"""
        assertEquals("2021", TmdbParse.items(tv, TitleType.TV).single().year)
    }

    @Test fun `the configuration gives the image base`() {
        assertEquals("http://10.0.2.2:8090/img/", TmdbParse.imageBase(fixture("configuration.json")))
        assertNull(TmdbParse.imageBase("""{"images":{}}"""))
        assertNull(TmdbParse.imageBase("""{"status_code":7}"""))
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg", CatalogueRules.imageUrl("https://image.tmdb.org/t/p/", "/abc.jpg"))
        assertNull(CatalogueRules.imageUrl("https://image.tmdb.org/t/p/", null))
        assertNull(CatalogueRules.imageUrl(null, "/abc.jpg"))
    }

    @Test fun `a title page reads its details and its genres`() {
        val (item, genres) = TmdbParse.details(fixture("movie_335984.json"), TitleType.MOVIE)!!
        assertEquals("Blade Runner 2049", item.title)
        assertEquals("2017", item.year)
        assertEquals(listOf("Science Fiction", "Drama"), genres)
        val (series, _) = TmdbParse.details(fixture("tv_117884.json"), TitleType.TV)!!
        assertEquals("Blade Runner: Black Lotus" to "2021", series.title to series.year)
        assertNull(TmdbParse.details("""{"status_code":34}""", TitleType.MOVIE))
    }

    @Test fun `watch providers are the country's names, each once, in display order`() {
        val all = fixture("providers.json")
        fun body(key: String) = app.tileshell.net.MiniJson.write((app.tileshell.net.MiniJson.parse(all) as Map<*, *>)[key])
        assertEquals(listOf("QA-Flix"), TmdbParse.providers(body("movie/335984"), "US"))
        assertEquals(listOf("QA-Flix"), TmdbParse.providers(body("movie/335984"), "us"))
        assertEquals(emptyList<String>(), TmdbParse.providers(body("movie/335984"), "DE"))
        assertEquals(emptyList<String>(), TmdbParse.providers(body("tv/117884"), "US"))
        val mixed = """{"results":{"US":{"buy":[{"provider_name":"Z","display_priority":1}],"flatrate":[{"provider_name":"B","display_priority":5},{"provider_name":"A","display_priority":2}],"rent":[{"provider_name":"A","display_priority":0}]}}}"""
        assertEquals(listOf("A", "B", "Z"), TmdbParse.providers(mixed, "US"))
    }

    @Test fun `an entry is stale after seven days`() {
        val day = 24L * 60 * 60 * 1000
        assertFalse(CatalogueRules.isStale(7 * day))
        assertTrue(CatalogueRules.isStale(7 * day + 1))
        assertFalse(CatalogueRules.isStale(0))
    }

    @Test fun `the lines carry the query and the status only`() {
        assertEquals("catalogue \"Blade Runner\": 3", CatalogueRules.line("Blade Runner", CatalogueRules.countStatus(3, false)))
        // B2-L5: the query is the user's text and a title is the catalogue's: one line, bounded, whatever it holds.
        assertEquals("catalogue \"blade[video] server x: connected\": 3", CatalogueRules.line("blade\n[video] server x: connected\r\u2028", "3"))
        assertEquals("catalogue \"${"q".repeat(80)}\": offline", CatalogueRules.line("q".repeat(500), "offline"))
        assertEquals("catalogue \"Blade Runner\": 3 (refreshed)", CatalogueRules.line("Blade Runner", CatalogueRules.countStatus(3, true)))
        assertEquals("catalogue \"Blade Runner\": error 500", CatalogueRules.line("Blade Runner", CatalogueRules.errorStatus(FetchOutcome.Status(500))))
        assertEquals("error connect", CatalogueRules.errorStatus(FetchOutcome.NoConnection))
        assertEquals("error 429", CatalogueRules.errorStatus(FetchOutcome.Status(429)))
        assertEquals("error 401", CatalogueRules.errorStatus(FetchOutcome.Status(401)))
        assertEquals("catalogue: no TMDB key saved", CatalogueRules.LINE_NO_KEY)
    }

    @Test fun `each failure has its words on the page`() {
        assertEquals("The catalogue isn't answering", CatalogueRules.notice(FetchOutcome.Status(500)).text)
        assertEquals("The catalogue isn't answering", CatalogueRules.notice(FetchOutcome.NoConnection).text)
        assertEquals("The catalogue is busy, try again in a minute", CatalogueRules.notice(FetchOutcome.Status(429)).text)
        assertEquals(CatalogueNotice.BAD_KEY, CatalogueRules.notice(FetchOutcome.Status(401)))
        assertEquals("You're offline — showing what was saved", CatalogueNotice.OFFLINE.text)
    }

    @Test fun `only a debug build follows the QA pref`() {
        val fixed = "https://api.themoviedb.org/"
        assertEquals("http://10.0.2.2:8090/", CatalogueRules.base(true, "http://10.0.2.2:8090/", fixed))
        assertEquals("http://10.0.2.2:8090/500/", CatalogueRules.base(true, "http://10.0.2.2:8090/500", fixed))
        assertEquals(fixed, CatalogueRules.base(false, "http://10.0.2.2:8090/", fixed))
        assertEquals(fixed, CatalogueRules.base(true, null, fixed))
        assertEquals(fixed, CatalogueRules.base(true, "  ", fixed))
        assertEquals(fixed, CatalogueRules.base(true, "file:///sdcard/x", fixed))
    }

    @Test fun `a saved key that cannot be a header's value is never made into a request's headers`() {
        // B2-M4: what Catalogue.request sends is this rule's answer, and null is "no request".
        assertEquals(mapOf("Authorization" to "Bearer qa-dummy-token", "Accept" to "application/json"), CatalogueRules.headers("qa-dummy-token"))
        for (bad in listOf("abc\rdef", "abc\ndef", "abc\r\nX-Injected: 1", "abc\u0000def", "abc\u007fdef", "abcédef", "abc\u2028def", "")) {
            assertNull(bad.take(4), CatalogueRules.headers(bad))
        }
        assertEquals("catalogue: the saved TMDB key cannot be sent", CatalogueRules.LINE_UNUSABLE_KEY)
    }

    @Test fun `outside a debug build posters come from TMDB's image host only`() {
        val fixed = app.tileshell.net.FixedEndpoints.TMDB_IMAGES
        assertEquals("https://image.tmdb.org/", fixed)
        fun release(base: String?) = CatalogueRules.posterBase(false, base, fixed)
        assertEquals("https://image.tmdb.org/t/p/", release("https://image.tmdb.org/t/p/"))
        // Another host, plain http, a host that only starts like TMDB's, a user part, nothing.
        for (bad in listOf(
            "https://images.attacker.example/t/p/", "http://image.tmdb.org/t/p/", "https://image.tmdb.org.attacker.example/t/p/",
            "https://image.tmdb.org@attacker.example/", "https://image.tmdb.org", "HTTPS://IMAGE.TMDB.ORG/t/p/", "http://10.0.2.2:8090/img/", "", null,
        )) {
            assertNull(bad, release(bad))
        }
        // B2-M4: a base that merely CONTAINS TMDB's image host — as a path, a query or a fragment of another host's address.
        for (bad in listOf(
            "https://attacker.example/https://image.tmdb.org/t/p/", "https://attacker.example/?u=https://image.tmdb.org/",
            "https://attacker.example/#https://image.tmdb.org/", " https://image.tmdb.org/t/p/", "xhttps://image.tmdb.org/t/p/",
        )) {
            assertNull(bad, release(bad))
        }
        // The QA catalogue's own image base is followed by a debug build only.
        assertEquals("http://10.0.2.2:8090/img/", CatalogueRules.posterBase(true, "http://10.0.2.2:8090/img/", fixed))
        assertNull(CatalogueRules.posterBase(true, null, fixed))
        // No base, no address: the poster is not fetched.
        assertNull(CatalogueRules.imageUrl(release("https://images.attacker.example/t/p/"), "/abc.jpg"))
        assertEquals("catalogue: image base is not TMDB's, posters are not fetched", CatalogueRules.LINE_IMAGE_BASE_REFUSED)
    }

    @Test fun `the cache keeps an entry per key, ages it by its file's time, and writes through a rename`() {
        val dir = Files.createTempDirectory("cat").toFile()
        var now = 1_000_000_000_000L
        val cache = CatalogueCache(dir) { now }
        assertNull(cache.read("search", "Blade Runner"))
        cache.write("search", "Blade Runner", "{\"results\":[]}")
        cache.write("title", "movie-78", "{}")
        assertEquals("{\"results\":[]}", cache.read("search", "blade runner ")!!.body)
        assertTrue(cache.file("title", "movie-78").name == "title-movie-78.json")
        assertTrue(dir.list()!!.none { it.endsWith(".tmp") })
        // The age is the file's modification time against the clock: moving the file's time back makes it stale.
        val f = cache.file("search", "Blade Runner")
        f.setLastModified(now - 8L * 24 * 60 * 60 * 1000)
        assertTrue(CatalogueRules.isStale(cache.read("search", "Blade Runner")!!.ageMs))
        now += 5
        cache.write("search", "Blade Runner", "{\"results\":[1]}")
        f.setLastModified(now)
        assertFalse(CatalogueRules.isStale(cache.read("search", "Blade Runner")!!.ageMs))
        // A key that is not a plain word never becomes a path.
        val odd = cache.file("search", "../../etc/passwd")
        assertEquals(dir, odd.parentFile)
        assertFalse(odd.name.contains(".."))
    }
}
