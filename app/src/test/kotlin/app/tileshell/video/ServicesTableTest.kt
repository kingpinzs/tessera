package app.tileshell.video

import app.tileshell.video.catalogue.TitleType
import app.tileshell.video.handoff.HandoffPlan
import app.tileshell.video.handoff.ServicesTable
import app.tileshell.video.handoff.TitleRef
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 17 build task 11: the services table of BS-4, the Wikidata query and its answer, and the hand-off's plan. */
class ServicesTableTest {
    private fun service(id: String, debug: Boolean = true) = ServicesTable.services(debug).first { it.id == id }
    private val film = TitleRef(TitleType.MOVIE, 78, "Blade Runner", "1982")
    private val sequel = TitleRef(TitleType.MOVIE, 335984, "Blade Runner 2049", "2017")

    private fun wikidata(tmdb: String): String {
        val rel = "docs/plan/qa/phase-17/fixtures/catalogue/wikidata.json"
        val all = app.tileshell.net.MiniJson.parse(listOf(File(rel), File("../$rel")).first { it.exists() }.readText()) as Map<*, *>
        return app.tileshell.net.MiniJson.write(all[tmdb])
    }

    @Test fun `the table is BS-4's ten services, with Jellyfin's app left out`() {
        val release = ServicesTable.services(debug = false)
        assertEquals(
            mapOf(
                "netflix" to "com.netflix.mediaclient", "prime-video" to "com.amazon.avod.thirdpartyclient", "disney-plus" to "com.disney.disneyplus",
                "hulu" to "com.hulu.plus", "max" to "com.wbd.stream", "apple-tv" to "com.apple.atve.androidtv.appletv", "paramount-plus" to "com.cbs.app",
                "peacock" to "com.peacocktv.peacockandroid", "youtube" to "com.google.android.youtube", "plex" to "com.plexapp.android",
            ),
            release.associate { it.id to it.packageName },
        )
        assertTrue(release.none { it.packageName.contains("jellyfin") })
    }

    @Test fun `the QA-Flix fixture service is in a debug build's table only`() {
        assertTrue(ServicesTable.services(debug = false).none { it.id == ServicesTable.QA_FLIX_ID })
        val qa = service("qa-flix")
        assertEquals("app.tileshell.testclient.qaflix", qa.packageName)
        assertFalse(ServicesTable.sparql(TitleType.MOVIE, 78, debug = false).contains(ServicesTable.QA_FLIX_PROPERTY))
        assertTrue(ServicesTable.sparql(TitleType.MOVIE, 78, debug = true).contains("OPTIONAL { ?item wdt:P99990001 ?qaflix }"))
        assertNull(ServicesTable.match("QA-Flix", debug = false))
        assertEquals("qa-flix", ServicesTable.match("QA-Flix", debug = true)?.id)
    }

    @Test fun `the query is BS-4's, keyed on the TMDB id by the title's kind`() {
        val q = ServicesTable.sparql(TitleType.MOVIE, 78, debug = false)
        assertEquals(
            "SELECT ?item ?netflix ?primeGti ?primeId ?disneyBrowse ?huluMovie ?huluSeries ?hboMax ?appleMovie ?appleShow WHERE { ?item wdt:P4947 \"78\" ." +
                " OPTIONAL { ?item wdt:P1874 ?netflix } OPTIONAL { ?item wdt:P14462 ?primeGti } OPTIONAL { ?item wdt:P14440 ?primeId }" +
                " OPTIONAL { ?item wdt:P13902 ?disneyBrowse } OPTIONAL { ?item wdt:P6466 ?huluMovie } OPTIONAL { ?item wdt:P6467 ?huluSeries }" +
                " OPTIONAL { ?item wdt:P8298 ?hboMax } OPTIONAL { ?item wdt:P9586 ?appleMovie } OPTIONAL { ?item wdt:P9751 ?appleShow } }",
            q,
        )
        assertTrue(ServicesTable.sparql(TitleType.TV, 117884, debug = false).contains("?item wdt:P4983 \"117884\" ."))
    }

    @Test fun `the answer gives the first value per variable and no item`() {
        assertEquals(mapOf("netflix" to "80185760", "appleMovie" to "umc.cmc.qa2049", "qaflix" to "qa-2049"), ServicesTable.parseWikidata(wikidata("335984")))
        assertEquals(mapOf("primeGti" to "amzn1.dv.gti.qa-78", "appleMovie" to "umc.cmc.qa78"), ServicesTable.parseWikidata(wikidata("78")))
        assertEquals(emptyMap<String, String>(), ServicesTable.parseWikidata(wikidata("*")))
        val twoRows = """{"results":{"bindings":[{"item":{"value":"x"},"netflix":{"type":"literal","value":"111"}},{"netflix":{"value":"222"},"hboMax":{"value":"movie/abc"}}]}}"""
        assertEquals(mapOf("netflix" to "111", "hboMax" to "movie/abc"), ServicesTable.parseWikidata(twoRows))
        assertNull(ServicesTable.parseWikidata("<html>busy</html>"))
        assertNull(ServicesTable.parseWikidata("""{"error":"x"}"""))
    }

    @Test fun `a found id opens the title, and the search is its fallback`() {
        val plan = ServicesTable.plan(service("qa-flix"), sequel, mapOf("qaflix" to "qa-2049"))
        assertEquals(HandoffPlan.Title("https://qa-flix.test/title/qa-2049", "https://qa-flix.test/search?q=Blade+Runner+2049+2017"), plan)
        assertEquals("watch-on qa-flix \"Blade Runner 2049\": id found (wikidata)", ServicesTable.idLine(service("qa-flix"), sequel, true))
        assertEquals(
            "watch-on qa-flix \"Blade Runner 2049\" -> https://qa-flix.test/title/qa-2049",
            ServicesTable.openLine(service("qa-flix"), sequel, (plan as HandoffPlan.Title).url),
        )
    }

    @Test fun `no id opens the service's search with the title and the year`() {
        val plan = ServicesTable.plan(service("qa-flix"), film, mapOf("primeGti" to "amzn1.dv.gti.qa-78"))
        assertEquals(HandoffPlan.Search("https://qa-flix.test/search?q=Blade+Runner+1982"), plan)
        assertEquals("watch-on qa-flix \"Blade Runner\": id none (wikidata)", ServicesTable.idLine(service("qa-flix"), film, false))
        assertEquals("watch-on qa-flix \"Blade Runner\" -> not installed", ServicesTable.openLine(service("qa-flix"), film, "not installed"))
    }

    @Test fun `each service's forms are BS-4's`() {
        val ids = mapOf(
            "netflix" to "80185760", "primeGti" to "amzn1.dv.gti.98a9f73a", "disneyBrowse" to "entity-1234", "huluMovie" to "uuid-m", "huluSeries" to "uuid-s",
            "hboMax" to "movie/abc", "appleMovie" to "umc.cmc.m", "appleShow" to "umc.cmc.s",
        )
        fun title(id: String, type: TitleType = TitleType.MOVIE) = ServicesTable.titleUrl(service(id), type, ids)
        assertEquals("https://www.netflix.com/title/80185760", title("netflix"))
        assertEquals("https://app.primevideo.com/detail?gti=amzn1.dv.gti.98a9f73a", title("prime-video"))
        assertEquals("https://www.disneyplus.com/browse/entity-1234", title("disney-plus"))
        assertEquals("https://www.hulu.com/movie/uuid-m", title("hulu"))
        assertEquals("https://www.hulu.com/series/uuid-s", title("hulu", TitleType.TV))
        assertEquals("https://play.hbomax.com/movie/abc", title("max"))
        assertEquals("https://tv.apple.com/movie/umc.cmc.m", title("apple-tv"))
        assertEquals("https://tv.apple.com/show/umc.cmc.s", title("apple-tv", TitleType.TV))
        assertEquals("https://app.primevideo.com/detail?gti=B00X", ServicesTable.titleUrl(service("prime-video"), TitleType.MOVIE, mapOf("primeId" to "B00X")))
        fun search(id: String) = ServicesTable.searchUrl(service(id), film)
        assertEquals("https://www.netflix.com/search?q=Blade+Runner+1982", search("netflix"))
        assertEquals("https://www.primevideo.com/search/?phrase=Blade+Runner+1982", search("prime-video"))
        assertEquals("https://www.hulu.com/search?q=Blade+Runner+1982", search("hulu"))
        assertEquals("https://play.hbomax.com/search?q=Blade+Runner+1982", search("max"))
        assertEquals("https://tv.apple.com/search?term=Blade+Runner+1982", search("apple-tv"))
        assertEquals("https://www.paramountplus.com/search/?q=Blade+Runner+1982", search("paramount-plus"))
        assertEquals("https://www.peacocktv.com/watch/search?q=Blade+Runner+1982", search("peacock"))
        assertEquals("https://www.youtube.com/results?search_query=Blade+Runner+1982", search("youtube"))
        assertEquals("https://watch.plex.tv/search?q=Blade+Runner+1982", search("plex"))
    }

    @Test fun `the four services with no usable id always search, and Disney with no id opens the app`() {
        for (id in listOf("paramount-plus", "peacock", "youtube", "plex")) {
            assertTrue(id, service(id).ids.isEmpty())
            assertTrue(id, ServicesTable.plan(service(id), film, mapOf("netflix" to "1")) is HandoffPlan.Search)
        }
        assertEquals(HandoffPlan.Launch, ServicesTable.plan(service("disney-plus"), film, emptyMap()))
        assertEquals(HandoffPlan.Title("https://www.disneyplus.com/browse/entity-9", null), ServicesTable.plan(service("disney-plus"), film, mapOf("disneyBrowse" to "entity-9")))
    }

    @Test fun `stale Max values and unsafe ids are skipped`() {
        assertNull(ServicesTable.titleUrl(service("max"), TitleType.MOVIE, mapOf("hboMax" to "feature/urn:hbo:feature:GXdu2")))
        assertNull(ServicesTable.titleUrl(service("max"), TitleType.TV, mapOf("hboMax" to "series/urn:hbo:series:GXdu2")))
        assertNull(ServicesTable.titleUrl(service("netflix"), TitleType.MOVIE, mapOf("netflix" to "1?next=https://evil.example")))
        assertNull(ServicesTable.titleUrl(service("netflix"), TitleType.MOVIE, mapOf("netflix" to "a b")))
        // A film's Hulu id is not used for a series.
        assertNull(ServicesTable.titleUrl(service("hulu"), TitleType.TV, mapOf("huluMovie" to "uuid-m")))
    }

    @Test fun `an id cannot climb out of its place in the address`() {
        fun max(value: String) = ServicesTable.titleUrl(service("max"), TitleType.MOVIE, mapOf("hboMax" to value))
        assertEquals("https://play.hbomax.com/movie/abc-123", max("movie/abc-123"))
        // A `..` segment anywhere, and a leading slash, would steer the link to another path of the service's host.
        for (bad in listOf("..", "../account", "movie/../account", "movie/..", "movie/../../x", "/account", "//attacker.example/x", "/")) {
            assertNull(bad, max(bad))
        }
        assertNull(ServicesTable.titleUrl(service("netflix"), TitleType.MOVIE, mapOf("netflix" to "../browse")))
        assertNull(ServicesTable.titleUrl(service("disney-plus"), TitleType.MOVIE, mapOf("disneyBrowse" to "entity-9/../../account")))
        // Dots that are not a `..` segment are an id's own.
        assertEquals("https://play.hbomax.com/movie/a..b", max("movie/a..b"))
        assertEquals("https://www.netflix.com/title/80.1", ServicesTable.titleUrl(service("netflix"), TitleType.MOVIE, mapOf("netflix" to "80.1")))
        // With no usable id the row falls back to the service's search.
        assertTrue(ServicesTable.plan(service("max"), TitleRef(TitleType.MOVIE, 1, "Dune", "2021"), mapOf("hboMax" to "movie/../account")) is HandoffPlan.Search)
    }

    @Test fun `a title with odd characters is encoded into the search`() {
        val odd = TitleRef(TitleType.MOVIE, 1, "Amélie & Co: #1?", "2001")
        assertEquals("https://qa-flix.test/search?q=Am%C3%A9lie+%26+Co%3A+%231%3F+2001", ServicesTable.searchUrl(service("qa-flix"), odd))
        assertEquals("https://qa-flix.test/search?q=No+Year", ServicesTable.searchUrl(service("qa-flix"), TitleRef(TitleType.TV, 2, "No Year", "")))
    }

    @Test fun `provider names map to their services`() {
        fun id(name: String) = ServicesTable.match(name, debug = false)?.id
        assertEquals("netflix", id("Netflix"))
        assertEquals("netflix", id("Netflix basic with Ads"))
        assertEquals("prime-video", id("Amazon Prime Video"))
        assertEquals("disney-plus", id("Disney Plus"))
        assertEquals("max", id("Max"))
        assertEquals("max", id("HBO Max"))
        assertEquals("apple-tv", id("Apple TV Plus"))
        assertEquals("paramount-plus", id("Paramount Plus"))
        assertEquals("peacock", id("Peacock Premium"))
        assertEquals("hulu", id("Hulu"))
        assertNull(id("Max Amazon Channel"))
        assertNull(id("Maxdome Store"))
        assertNull(id("Some Regional Service"))
    }
}
