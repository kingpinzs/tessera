package app.tileshell.music.radio

import app.tileshell.net.FixedEndpoints
import app.tileshell.video.catalogue.CatalogueRules
import app.tileshell.video.catalogue.QaBases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (Decisions "the station directory"; T20-10; r3 D16): which server is asked, and the debug-only override. */
class RadioMirrorsTest {
    private val de1 = "de1.api.radio-browser.info"
    private val nl1 = "nl1.api.radio-browser.info"
    private val at1 = "at1.api.radio-browser.info"

    @Test fun `one entry is that mirror`() {
        for (seed in 0L..20L) assertEquals(listOf(de1), RadioMirrors.order(listOf(de1), seed))
    }

    @Test fun `several are all kept, shuffled by the seed - the same seed the same order, and every order turns up`() {
        val names = listOf(de1, nl1, at1)
        val orders = (0L..200L).map { RadioMirrors.order(names, it) }
        for (order in orders) assertEquals(names.toSet(), order.toSet())
        assertEquals(RadioMirrors.order(names, 7), RadioMirrors.order(names, 7))
        assertEquals(6, orders.toSet().size)
    }

    @Test fun `empty falls back to the round-robin name itself`() {
        assertEquals(listOf("all.api.radio-browser.info"), RadioMirrors.order(emptyList(), 1))
        assertEquals("all.api.radio-browser.info", RadioMirrors.ALL)
        // A lookup that gave only addresses with no name of the directory's is an empty one.
        assertEquals(listOf(RadioMirrors.ALL), RadioMirrors.order(listOf("91.98.4.78", "static.78.4.98.91.clients.your-server.de", ""), 1))
    }

    @Test fun `names outside api radio-browser info are dropped`() {
        val names = listOf(
            de1, "evil.example.net", "api.radio-browser.info.evil.example.net", "radio-browser.info", "api.radio-browser.info", "xapi.radio-browser.info",
            "de1.api.radio-browser.info.evil.net", "evil.net/de1.api.radio-browser.info", "evil.net#.api.radio-browser.info", "a.b.api.radio-browser.info",
            "user@de2.api.radio-browser.info", "-de3.api.radio-browser.info", "de 4.api.radio-browser.info", ".api.radio-browser.info", "91.98.4.78", "::1",
        )
        for (seed in 0L..10L) assertEquals(listOf(de1), RadioMirrors.order(names, seed))
        // The round-robin name is the directory's own too.
        assertEquals(listOf("all.api.radio-browser.info"), RadioMirrors.order(listOf("all.api.radio-browser.info"), 1))
    }

    @Test fun `deduped - by case and by a trailing dot too`() {
        assertEquals(listOf(de1), RadioMirrors.order(listOf(de1, de1, "DE1.API.Radio-Browser.info", "de1.api.radio-browser.info.", " de1.api.radio-browser.info "), 3))
        assertEquals(setOf(de1, nl1), RadioMirrors.order(listOf(de1, nl1, de1, nl1), 3).toSet())
        assertEquals(2, RadioMirrors.order(listOf(de1, nl1, de1, nl1), 3).size)
    }

    @Test fun `the next on failure, and none after the last`() {
        val order = RadioMirrors.order(listOf(de1, nl1, at1), 11)
        assertEquals(order[1], RadioMirrors.next(order, order[0]))
        assertEquals(order[2], RadioMirrors.next(order, order[1]))
        assertNull(RadioMirrors.next(order, order[2]))
        assertNull(RadioMirrors.next(order, "other.api.radio-browser.info"))
        assertNull(RadioMirrors.next(listOf(de1), de1))
    }

    @Test fun `a mirror's base is https on the mirror's name, inside the fixed endpoint's https-only domain`() {
        assertEquals("https://de1.api.radio-browser.info/", RadioMirrors.base(de1))
        assertEquals("https://all.api.radio-browser.info/", RadioMirrors.base(RadioMirrors.ALL))
        val fixed = FixedEndpoints.hostOf(FixedEndpoints.RADIO_BROWSER)
        for (name in RadioMirrors.order(listOf(de1, nl1, "evil.example.net"), 5) + RadioMirrors.order(emptyList(), 5)) {
            val base = RadioMirrors.base(name)
            assertTrue(base, base.startsWith("https://") && base.endsWith("/"))
            assertTrue(base, FixedEndpoints.hostOf(base).endsWith(".$fixed"))
        }
        assertEquals(listOf("https://de1.api.radio-browser.info/"), RadioMirrors.bases(null, listOf(de1), 1))
        assertEquals(listOf("https://all.api.radio-browser.info/"), RadioMirrors.bases(null, emptyList(), 1))
    }

    // ---- QaBases: the three phase-20 prefs (r3 D16)

    @Test fun `the three prefs are named beside phase 17's`() {
        assertEquals("qa_radio_base", QaBases.RADIO)
        assertEquals("qa_music_catalogue_base", QaBases.MUSIC_CATALOGUE)
        assertEquals("qa_coverart_base", QaBases.COVERART)
    }

    /** What `RadioNet.qaBase` computes, with the build's flag and the pref's value as arguments. */
    private fun qaBase(debug: Boolean, pref: String?) = RadioMirrors.qaBase(CatalogueRules.base(debug, pref, FixedEndpoints.RADIO_BROWSER))

    @Test fun `the override is honoured only when DEBUG`() {
        assertEquals("http://10.0.2.2:8080/", qaBase(debug = true, pref = "http://10.0.2.2:8080/"))
        assertEquals("http://10.0.2.2:8080/", qaBase(debug = true, pref = " http://10.0.2.2:8080 "))
        for (pref in listOf("http://10.0.2.2:8080/", "https://evil.example.net/", null, "")) assertNull("$pref", qaBase(debug = false, pref = pref))
        // In a debug build with no pref — or one that is not an address — there is no override either.
        for (pref in listOf(null, "", "   ", "10.0.2.2:8080", "file:///sdcard/x", "ftp://10.0.2.2/")) assertNull("$pref", qaBase(debug = true, pref = pref))
        // The same gate for the other two, which build task 8 reads: a release build gets the fixed endpoint.
        assertEquals(FixedEndpoints.MUSICBRAINZ, CatalogueRules.base(false, "http://10.0.2.2:8081/ws/2/", FixedEndpoints.MUSICBRAINZ))
        assertEquals(FixedEndpoints.COVER_ART_ARCHIVE, CatalogueRules.base(false, "http://10.0.2.2:8081/coverart/", FixedEndpoints.COVER_ART_ARCHIVE))
        assertEquals("http://10.0.2.2:8081/ws/2/", CatalogueRules.base(true, "http://10.0.2.2:8081/ws/2/", FixedEndpoints.MUSICBRAINZ))
    }

    @Test fun `a set radio override bypasses the mirror lookup`() {
        val qa = qaBase(debug = true, pref = "http://10.0.2.2:8080/")
        assertFalse(RadioMirrors.needsLookup(qa))
        assertTrue(RadioMirrors.needsLookup(null))
        assertTrue(RadioMirrors.needsLookup(qaBase(debug = false, pref = "http://10.0.2.2:8080/")))
        // Whatever a lookup would have said, the one base asked is the override.
        for (names in listOf(emptyList(), listOf(de1), listOf(de1, nl1, at1))) {
            assertEquals(listOf("http://10.0.2.2:8080/"), RadioMirrors.bases(qa, names, 9))
        }
    }

    @Test fun `a set radio override also carries the click call, and every other call`() {
        val base = RadioMirrors.bases(qaBase(debug = true, pref = "http://10.0.2.2:8080/"), listOf(de1), 9).single()
        assertEquals("http://10.0.2.2:8080/json/url/jazz-one", base + RadioDirectory.request(RadioDirectory.What.Click("jazz-one")))
        assertEquals("http://10.0.2.2:8080/json/countries", base + RadioDirectory.request(RadioDirectory.What.Countries))
        // … and it names the one private host a station may play from.
        assertEquals("10.0.2.2", StationUrl.qaHost(base))
        assertEquals(StationUrl.Accept.Ok, StationUrl.accept("http://10.0.2.2:8080/stream/jazz-one", StationUrl.qaHost(base)))
        // A release build: the click goes to a mirror, and the fixture's host plays nothing.
        val release = qaBase(debug = false, pref = "http://10.0.2.2:8080/")
        assertEquals(listOf("https://de1.api.radio-browser.info/"), RadioMirrors.bases(release, listOf(de1), 9))
        assertNull(StationUrl.qaHost(release))
        assertEquals(StationUrl.Accept.UnsupportedHost, StationUrl.accept("http://10.0.2.2:8080/stream/jazz-one", StationUrl.qaHost(release)))
    }
}
