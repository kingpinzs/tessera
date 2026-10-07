package app.tileshell.music.radio

import app.tileshell.music.MusicSearch
import app.tileshell.music.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (r3 D1; a trust rule): only the shell's own controller's search is resolved against the stations. */
class RadioSearchRuleTest {
    private val me = 10_123
    private val jazz = station("j1", name = "QA Jazz One", tags = "jazz", clicks = 9)
    private val all = MusicSearch.Stations(listOf(Favourite(jazz))) { RadioIndex.of(listOf(jazz)) }
    private val library = listOf(Track(1, "Bloom", "Beach House", "Bloom", 10, 90_000L, 0L))

    @Test fun `the shell's uid - the stations`() {
        assertTrue(RadioSearchRule.stationsFor(me, me))
        assertSame(all, RadioSearchRule.stationsFor(me, me) { all })
    }

    @Test fun `any other uid - none, and the stations are not even asked for`() {
        for (uid in listOf(0, 1000, 2000, me - 1, me + 1, 10_077, -1, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertFalse("uid $uid", RadioSearchRule.stationsFor(uid, me))
            assertSame("uid $uid", MusicSearch.Stations.NONE, RadioSearchRule.stationsFor(uid, me) { error("a stranger's search asked for the stations") })
        }
    }

    @Test fun `a uid below 0 is nobody's, even when both sides say it`() {
        assertFalse(RadioSearchRule.stationsFor(-1, -1))
        assertSame(MusicSearch.Stations.NONE, RadioSearchRule.stationsFor(-1, -1) { all })
    }

    @Test fun `a stranger's search for a station's name gets the library-only answer`() {
        fun search(uid: Int, query: String) = MusicSearch.resolve(query, library, RadioSearchRule.stationsFor(uid, me) { all })
        // The shell's own controller finds the station three ways; a stranger finds it none.
        for (query in listOf("QA Jazz One", "radio", "jazz radio")) {
            assertEquals(query, jazz, search(me, query)?.station)
            assertEquals(query, null, search(10_077, query))
            assertEquals(query, MusicSearch.resolve(query, library), search(10_077, query))
        }
        // And what a stranger does find is the library's, exactly as before.
        assertEquals("Bloom by Beach House", search(10_077, "bloom")!!.label)
        assertEquals(null, search(10_077, "bloom")!!.station)
    }
}
