package app.tileshell.music

import app.tileshell.music.MusicItemRule.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ledger L18-1: what the exported session does with one item a controller hands it — every combination. */
class MusicItemRuleTest {
    private val shell = 10077
    private val stranger = 10123

    private fun decide(uid: Int, id: String? = "", uri: Boolean = false, query: Boolean = false, me: Int = shell) =
        MusicItemRule.decide(uid, me, id, uri, query)

    @Test fun `the shell's own controller keeps the URI its item carries - the library queue and the one-file item alike`() {
        assertEquals(Decision.Keep, decide(shell, id = "41", uri = true))
        // The one-file item's id is not a library id, and an item may carry a search beside its URI: still kept.
        assertEquals(Decision.Keep, decide(shell, id = MusicFile.mediaId(3), uri = true))
        assertEquals(Decision.Keep, decide(shell, id = "", uri = true, query = true))
    }

    @Test fun `another app's controller NEVER keeps the URI it sent - whatever the id, the search or the uid`() {
        val uids = listOf(stranger, 0, 1000, 2000, -1, Int.MIN_VALUE, Int.MAX_VALUE, shell + 1, shell - 1, shell + 100_000)
        val ids = listOf(null, "", "41", "0", "-5", "probe", MusicFile.mediaId(1), "41 ", "file:///sdcard/a.mp3")
        for (uid in uids) for (id in ids) for (query in listOf(false, true)) {
            assertNotEquals("uid $uid id $id query $query", Decision.Keep, decide(uid, id, uri = true, query = query))
        }
    }

    @Test fun `another app's item with a URI is rebuilt from the library by its id, or dropped - the same as one without`() {
        assertEquals(Decision.Rebuild(41), decide(stranger, id = "41", uri = true))
        assertEquals(Decision.Drop, decide(stranger, id = "probe", uri = true))
        assertEquals(Decision.Drop, decide(stranger, id = "", uri = true))
        assertEquals(Decision.Drop, decide(stranger, id = null, uri = true))
        assertEquals(Decision.Search, decide(stranger, id = "", uri = true, query = true))
        // Whether a stranger's item carries a URI changes nothing: the answer cannot depend on what it sent.
        for (id in listOf(null, "", "41", "probe")) for (query in listOf(false, true)) {
            assertEquals(decide(stranger, id, uri = false, query = query), decide(stranger, id, uri = true, query = query))
        }
    }

    @Test fun `an item without a URI is a search, a library id or nothing - for the shell and for anyone`() {
        for (uid in listOf(shell, stranger)) {
            assertEquals(Decision.Search, decide(uid, id = "41", query = true))
            assertEquals(Decision.Rebuild(41), decide(uid, id = "41"))
            assertEquals(Decision.Drop, decide(uid, id = ""))
            assertEquals(Decision.Drop, decide(uid, id = MusicFile.mediaId(1)))
        }
    }

    @Test fun `no uid at all is never the shell - a session that cannot name itself keeps nothing`() {
        assertEquals(Decision.Drop, decide(-1, id = "", uri = true, me = -1))
        assertEquals(Decision.Rebuild(7), decide(-1, id = "7", uri = true, me = -1))
    }

    @Test fun `a library id is a positive decimal number exactly as the library writes it`() {
        assertEquals(41L, MusicItemRule.libraryId("41"))
        assertEquals(Long.MAX_VALUE, MusicItemRule.libraryId(Long.MAX_VALUE.toString()))
        for (bad in listOf(null, "", "0", "-1", "+41", "041", " 41", "41 ", "4 1", "41.0", "0x29", "٤١", "9223372036854775808")) {
            assertNull("\"$bad\"", MusicItemRule.libraryId(bad))
        }
    }
}
