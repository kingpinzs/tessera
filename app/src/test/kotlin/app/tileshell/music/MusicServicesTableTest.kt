package app.tileshell.music

import app.tileshell.music.handoff.MusicHandoffEntry
import app.tileshell.music.handoff.MusicLauncher
import app.tileshell.music.handoff.MusicServicesTable
import app.tileshell.video.handoff.HandoffIntent
import app.tileshell.video.handoff.HandoffPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 20 build tasks 7 (d) and 8 (r3 D3, D7): the music hand-off's table, the plan a "Listen on" tap tries, the
 * "Listen on" list, the app a spoken name means, and the `[music] handoff:` lines.
 */
class MusicServicesTableTest {
    private fun service(id: String, debug: Boolean = true) = MusicServicesTable.services(debug).first { it.id == id }
    private fun unknown(packageName: String, label: String) = MusicHandoffEntry(packageName, label, packageName, emptyList())
    private val self = "app.tileshell"

    @Test fun `the table is the seven recorded services, and QA Tunes in a debug build only`() {
        val release = MusicServicesTable.services(debug = false)
        assertEquals(
            mapOf(
                "pandora" to "com.pandora.android", "youtube-music" to "com.google.android.apps.youtube.music", "amazon-music" to "com.amazon.mp3",
                "apple-music" to "com.apple.android.music", "deezer" to "deezer.android.app", "soundcloud" to "com.soundcloud.android", "tidal" to "com.aspiro.tidal",
            ),
            release.associate { it.id to it.packageName },
        )
        assertTrue("Tidal has no link", service("tidal").searchLinks.isEmpty())
        assertTrue(release.none { it.id == MusicServicesTable.QA_TUNES_ID || it.packageName == MusicServicesTable.QA_TUNES_PACKAGE })
        val qa = service(MusicServicesTable.QA_TUNES_ID, debug = true)
        assertEquals(MusicHandoffEntry("qa-tunes", "QA Tunes", "app.tileshell.testclient.qatunes", listOf("https://qa-tunes.test/search?q=%s")), qa)
        assertEquals(release, MusicServicesTable.services(debug = true).dropLast(1))
    }

    // ---- MusicServicesTable.plan(service, title, artist)

    @Test fun `Pandora - its deep link, then its web link, then play-from-search, then the plain open`() {
        assertEquals(
            listOf(
                HandoffPlan.Search("pandorav8://search/QA%20Song%20A%20QA%20Artist/all"),
                HandoffPlan.Search("https://www.pandora.com/search/QA%20Song%20A%20QA%20Artist/all"),
                HandoffPlan.PlayFromSearch("QA Song A QA Artist"),
                HandoffPlan.Launch,
            ),
            MusicServicesTable.plan(service("pandora"), "QA Song A", "QA Artist"),
        )
    }

    @Test fun `each other recorded service opens its own search link first`() {
        fun first(id: String) = (MusicServicesTable.plan(service(id), "Bloom", "Beach House").first() as HandoffPlan.Search).url
        assertEquals("https://music.youtube.com/search?q=Bloom+Beach+House", first("youtube-music"))
        assertEquals("https://music.amazon.com/search/Bloom%20Beach%20House", first("amazon-music"))
        assertEquals("https://music.apple.com/us/search?term=Bloom+Beach+House", first("apple-music"))
        assertEquals("https://www.deezer.com/search/Bloom%20Beach%20House", first("deezer"))
        assertEquals("https://soundcloud.com/search?q=Bloom+Beach+House", first("soundcloud"))
        assertEquals("https://qa-tunes.test/search?q=QA+Song+A+QA+Artist", (MusicServicesTable.plan(service("qa-tunes"), "QA Song A", "QA Artist").first() as HandoffPlan.Search).url)
        // One link, then the two fallbacks, in that order.
        for (id in listOf("youtube-music", "amazon-music", "apple-music", "deezer", "soundcloud", "qa-tunes")) {
            val plan = MusicServicesTable.plan(service(id), "Bloom", "Beach House")
            assertEquals(id, 3, plan.size)
            assertEquals(id, listOf(HandoffPlan.PlayFromSearch("Bloom Beach House"), HandoffPlan.Launch), plan.drop(1))
        }
    }

    @Test fun `Tidal and an app the table does not know - play-from-search, then the plain open`() {
        val expected = listOf(HandoffPlan.PlayFromSearch("Bloom Beach House"), HandoffPlan.Launch)
        assertEquals(expected, MusicServicesTable.plan(service("tidal"), "Bloom", "Beach House"))
        assertEquals(expected, MusicServicesTable.plan(unknown("org.oxycblt.auxio", "Auxio"), " Bloom ", " Beach House "))
        // No artist (Tess's "listen to <x> on <app>"): the words alone.
        assertEquals(listOf(HandoffPlan.PlayFromSearch("qa artist"), HandoffPlan.Launch), MusicServicesTable.plan(service("tidal"), "qa artist", ""))
    }

    @Test fun `play-from-search carries the query and the focus, names the package, and has no data URI`() {
        val plan = MusicServicesTable.plan(service("tidal"), "Bloom", "Beach House").first() as HandoffPlan.PlayFromSearch
        assertEquals(
            HandoffIntent(
                action = "android.media.action.MEDIA_PLAY_FROM_SEARCH",
                packageName = "com.aspiro.tidal",
                data = null,
                // SearchManager.QUERY and MediaStore.EXTRA_MEDIA_FOCUS, by their values.
                extras = mapOf("query" to "Bloom Beach House", "android.intent.extra.focus" to "vnd.android.cursor.item/*"),
            ),
            plan.intent("com.aspiro.tidal"),
        )
        assertEquals(android.app.SearchManager.QUERY, "query")
        assertEquals(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "android.intent.extra.focus")
    }

    @Test fun `a title's own punctuation cannot change a link's shape`() {
        val hostile = "AC/DC ?x=1&y=2 #frag ../.. 100% +1"
        val path = MusicServicesTable.link("https://www.pandora.com/search/%s/all", hostile)
        assertEquals("https://www.pandora.com/search/AC%2FDC%20%3Fx%3D1%26y%3D2%20%23frag%20..%2F..%20100%25%20%2B1/all", path)
        val query = MusicServicesTable.link("https://soundcloud.com/search?q=%s", hostile)
        assertEquals("https://soundcloud.com/search?q=AC%2FDC+%3Fx%3D1%26y%3D2+%23frag+..%2F..+100%25+%2B1", query)
        assertEquals("pandorav8://search/caf%C3%A9%20%22live%22/all", MusicServicesTable.link("pandorav8://search/%s/all", "café \"live\""))
    }

    // ---- MusicServicesTable.entries(installed, musicLaunchers, self)

    @Test fun `the list is the installed table rows, then every other music launcher - never the shell, never a row twice`() {
        val launchers = listOf(
            MusicLauncher("org.oxycblt.auxio", "Auxio"),
            MusicLauncher(self, "Music"),                               // the shell declares the category itself
            MusicLauncher("com.pandora.android", "Pandora"),            // a table package that also declares it
            MusicLauncher("com.example.zplayer", "ZPlayer"),
            MusicLauncher("org.oxycblt.auxio", "Auxio"),                // two activities of one app
            MusicLauncher("com.example.aplayer", "aPlayer"),
        )
        val entries = MusicServicesTable.entries(setOf("com.soundcloud.android", "com.pandora.android", "com.unrelated.app"), launchers, self, debug = false)
        assertEquals(listOf("pandora", "soundcloud", "com.example.aplayer", "org.oxycblt.auxio", "com.example.zplayer"), entries.map { it.id })
        assertEquals(listOf("Pandora", "SoundCloud", "aPlayer", "Auxio", "ZPlayer"), entries.map { it.label })
        assertTrue(entries.none { it.packageName == self })
        assertEquals("a table package is listed once", 1, entries.count { it.packageName == "com.pandora.android" })
        // An app the table does not know is a plain-open entry.
        assertTrue(entries.first { it.id == "org.oxycblt.auxio" }.searchLinks.isEmpty())
        // Nothing installed, nothing declared: an empty list (and the shell alone is still nothing).
        assertEquals(emptyList<MusicHandoffEntry>(), MusicServicesTable.entries(emptySet(), listOf(MusicLauncher(self, "Music")), self, debug = true))
        // A table row that is not installed is not listed, whatever declares the category.
        assertTrue(MusicServicesTable.entries(emptySet(), launchers, self, debug = false).none { it.id == "soundcloud" })
    }

    @Test fun `the QA Tunes row is a table row only when DEBUG`() {
        val pkg = MusicServicesTable.QA_TUNES_PACKAGE
        val launchers = listOf(MusicLauncher(pkg, "QA Tunes"))
        assertEquals(listOf(service("qa-tunes")), MusicServicesTable.entries(setOf(pkg), launchers, self, debug = true))
        // A release build has no such row: the stub would be one more music app, opened plainly.
        assertEquals(listOf(unknown(pkg, "QA Tunes")), MusicServicesTable.entries(setOf(pkg), launchers, self, debug = false))
        assertEquals(emptyList<MusicHandoffEntry>(), MusicServicesTable.entries(setOf(pkg), emptyList(), self, debug = false))
    }

    // ---- MusicServicesTable.byLabel(phrase, entries)

    @Test fun `a spoken app is the label that is the phrase, else the first that starts with it, else none`() {
        val entries = MusicServicesTable.services(debug = true) + unknown("org.oxycblt.auxio", "Auxio") + unknown("com.example.pan", "Pan")
        fun id(phrase: String) = MusicServicesTable.byLabel(phrase, entries)?.id
        // Normalised exact: case, spaces and punctuation do not matter.
        assertEquals("pandora", id("pandora"))
        assertEquals("qa-tunes", id("qa tunes"))
        assertEquals("qa-tunes", id("QA-Tunes"))
        assertEquals("youtube-music", id("you tube music"))
        assertEquals("org.oxycblt.auxio", id("auxio"))
        // Exact beats a prefix that comes earlier in the list: "pan" is Pan, not Pandora.
        assertEquals("com.example.pan", id("pan"))
        // Then prefix, the first in the list's order.
        assertEquals("youtube-music", id("youtube"))
        assertEquals("amazon-music", id("amazon"))
        assertEquals("pandora", id("pand"))
        // No match.
        assertNull(id("spotify"))
        assertNull(id("music"))
        assertNull(id("pandora radio"))
        assertNull(id(""))
        assertNull(id(" - "))
        assertNull(MusicServicesTable.byLabel("pandora", emptyList()))
    }

    // ---- the lines

    @Test fun `the handoff lines are worded as the doc words them`() {
        val tunes = service("qa-tunes")
        val plan = MusicServicesTable.plan(tunes, "QA Song A", "QA Artist")
        assertEquals("handoff: qa-tunes \"QA Song A\" -> https://qa-tunes.test/search?q=QA+Song+A+QA+Artist", MusicServicesTable.line(tunes, "QA Song A", plan[0], opened = true))
        assertEquals("handoff: qa-tunes did not open at the title", MusicServicesTable.line(tunes, "QA Song A", plan[0], opened = false))
        assertEquals("handoff: qa-tunes \"QA Song A\" -> android.media.action.MEDIA_PLAY_FROM_SEARCH", MusicServicesTable.line(tunes, "QA Song A", plan[1], opened = true))
        assertEquals("handoff: qa-tunes did not open at the title", MusicServicesTable.line(tunes, "QA Song A", plan[1], opened = false))
        // The plain open: the last fallback of a service that has a link …
        assertEquals("handoff: qa-tunes: search not passed", MusicServicesTable.line(tunes, "QA Song A", HandoffPlan.Launch, opened = true))
        // … and the ordinary way in for one that has none.
        val auxio = unknown("org.oxycblt.auxio", "Auxio")
        assertEquals("handoff: Auxio \"QA Song A\" -> open (no search link)", MusicServicesTable.line(auxio, "QA Song A", HandoffPlan.Launch, opened = true))
        assertEquals("handoff: tidal \"QA Song A\" -> open (no search link)", MusicServicesTable.line(service("tidal"), "QA Song A", HandoffPlan.Launch, opened = true))
        assertEquals("handoff: Auxio did not open at the title", MusicServicesTable.line(auxio, "QA Song A", HandoffPlan.PlayFromSearch("QA Song A"), opened = false))
        // A plain open that nothing answers is the stale tap: its text is the page's, not a line.
        assertNull(MusicServicesTable.line(tunes, "QA Song A", HandoffPlan.Launch, opened = false))
        assertEquals("That app isn't installed any more", MusicServicesTable.TEXT_NOT_INSTALLED)
        // Foreign text — a title, an app's label — cannot start a line of its own.
        val forged = MusicServicesTable.line(unknown("x.y", "Evil\n[music] server h: connected"), "T\nitle", HandoffPlan.Launch, opened = true)!!
        assertEquals("handoff: Evil[music] server h: connected \"Title\" -> open (no search link)", forged)
    }
}
