package app.tileshell.music

import app.tileshell.music.catalogue.CatalogueSession
import app.tileshell.music.catalogue.CatalogueTrack
import app.tileshell.music.catalogue.MusicCatalogue
import app.tileshell.music.catalogue.MusicCatalogueNotice
import app.tileshell.music.catalogue.RequestGate
import app.tileshell.video.catalogue.FetchOutcome
import app.tileshell.video.catalogue.ImageHop
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 20 build task 8 (r3 D17): the music catalogue's rules — MusicBrainz's `recording` search, its answer, the Cover
 * Art Archive's address — and the session that asks: its lines, its one request per submit, its session-only cache.
 */
class MusicCatalogueTest {
    private val songA = "0c2ad8b2-1f0b-4a34-9d4e-6f6d2f8a1a01"
    private val songB = "0c2ad8b2-1f0b-4a34-9d4e-6f6d2f8a1a02"
    private val release = "76df3287-6cda-33eb-8e9a-044b5e15ffdd"

    private val answer = """
        {"created":"2026-10-07T00:00:00.000Z","count":4,"offset":0,"recordings":[
          {"id":"$songA","score":100,"title":"QA Song A","length":183000,
           "artist-credit":[{"name":"QA Artist","artist":{"id":"x","name":"QA Artist"}}],
           "releases":[{"id":"$release","title":"QA Album","date":"1999-05-17","release-group":{"id":"y"}},{"id":"11111111-2222-3333-4444-555555555555","title":"Later"}]},
          {"id":"$songB","score":90,"title":" QA Song B ",
           "artist-credit":[{"name":"QA Artist","joinphrase":" & "},{"name":"Guest"},{"name":"Third"}]},
          {"id":"not-an-mbid","title":"Dropped"},
          {"id":"0c2ad8b2-1f0b-4a34-9d4e-6f6d2f8a1a03"},
          7
        ]}
    """.trimIndent()

    // ---- MusicCatalogue.searchUrl / .parse / .coverUrl

    @Test fun `the search is MusicBrainz's recording search, limit 25, the query encoded`() {
        assertEquals("https://musicbrainz.org/ws/2/", MusicCatalogue.API)
        assertEquals("https://musicbrainz.org/ws/2/recording?query=qa+artist&fmt=json&limit=25", MusicCatalogue.searchUrl(MusicCatalogue.API, " qa artist "))
        // What a title can hold never changes the address's shape: each is encoded.
        assertEquals(
            "http://10.0.2.2:8081/ws/2/recording?query=AC%2FDC+%26+%22T.N.T.%22+%23%3F%3D%C3%A9&fmt=json&limit=25",
            MusicCatalogue.searchUrl("http://10.0.2.2:8081/ws/2/", "AC/DC & \"T.N.T.\" #?=é"),
        )
        assertEquals(25, MusicCatalogue.LIMIT)
    }

    @Test fun `title, artist and release are parsed, and a row that is not a recording is dropped`() {
        assertEquals(
            listOf(
                CatalogueTrack(songA, "QA Song A", "QA Artist", "QA Album", release, "1999", 183000L),
                // No release: no artwork key, and the credits joined as MusicBrainz joins them.
                CatalogueTrack(songB, "QA Song B", "QA Artist & Guest, Third", "", null, "", null),
            ),
            MusicCatalogue.parse(answer),
        )
        assertEquals(emptyList<CatalogueTrack>(), MusicCatalogue.parse("""{"count":0,"recordings":[]}"""))
        assertNull(MusicCatalogue.parse("""{"error":"Your requests are exceeding the allowable rate limit."}"""))
        assertNull(MusicCatalogue.parse("<html>503</html>"))
        // A release whose id is not an MBID gives no artwork key (it would become part of an address).
        val odd = """{"recordings":[{"id":"$songA","title":"T","releases":[{"id":"../../x","title":"R","date":"19"}]}]}"""
        assertEquals(listOf(CatalogueTrack(songA, "T", "", "R", null, "", null)), MusicCatalogue.parse(odd))
    }

    // Review R20-10: MusicBrainz is community-edited, as the station directory is. Its strings reach a row, a page, a
    // hand-off's query and Tess's reply, so each is made fit to show AT PARSE (RadioText.shown) - cleaned, then cut.
    @Test fun `a title, an artist and a release are cleaned and cut at parse - no control or bidi character, no megabyte`() {
        fun q(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")
        val long = "x".repeat(600_000)
        val hostile = """{"recordings":[{"id":"$songA","title":"${q(long)}",
            "artist-credit":[{"name":"a‮b\nc","joinphrase":" ⁦&⁩ "},{"name":"${q(long)}"}],
            "releases":[{"id":"$release","title":"R\u0007e l​ease ${q(long)}","date":"1999"}]}]}"""
        val row = MusicCatalogue.parse(hostile)!!.single()
        assertEquals("x".repeat(120), row.title)
        assertEquals(("abc & " + long).take(80), row.artist)
        assertEquals(("Release " + long).take(120), row.release)
        assertEquals(release, row.releaseId)
        // Every string, in one row: nothing a line, a page or a spoken reply cannot take.
        val forged = """{"recordings":[{"id":"$songA","title":" ‮evilT\r\n[music] forged line\u0000 ",
            "artist-credit":[{"name":"⁧A⁩\tB"}],"releases":[{"id":"$release","title":"﻿Al­bum "}]}]}"""
        assertEquals(listOf(CatalogueTrack(songA, "evilT[music] forged line", "AB", "Album", release, "", null)), MusicCatalogue.parse(forged))
        // A title that is nothing once cleaned is no title: the row is dropped, as a row with none is.
        val empty = """{"recordings":[{"id":"$songA","title":"​‮ \n"},{"id":"$songB","title":"Kept"}]}"""
        assertEquals(listOf(CatalogueTrack(songB, "Kept", "", "", null, "", null)), MusicCatalogue.parse(empty))
        assertEquals(120, app.tileshell.music.radio.RadioText.TITLE_MAX)
        assertEquals(80, app.tileshell.music.radio.RadioText.NAME_MAX)
    }

    @Test fun `the artwork is the first release's front-250, and no release is the placeholder`() {
        assertEquals("https://coverartarchive.org/", MusicCatalogue.COVERS)
        assertEquals("https://coverartarchive.org/release/$release/front-250", MusicCatalogue.coverUrl(MusicCatalogue.COVERS, release))
        assertEquals("http://10.0.2.2:8081/coverart/release/$release/front-250", MusicCatalogue.coverUrl("http://10.0.2.2:8081/coverart/", release))
        assertNull("no release: the placeholder", MusicCatalogue.coverUrl(MusicCatalogue.COVERS, null))
        assertNull(MusicCatalogue.coverUrl(MusicCatalogue.COVERS, "../../etc"))
        // The parsed row's key is releases[0].id.
        assertEquals(release, MusicCatalogue.parse(answer)!!.first().releaseId)
    }

    @Test fun `the outcome lines are worded as the doc words them`() {
        assertEquals("catalogue \"qa artist\": offline", MusicCatalogue.line("qa artist", MusicCatalogue.STATUS_OFFLINE))
        assertEquals("catalogue \"qa artist\": error 503", MusicCatalogue.line("qa artist", MusicCatalogue.errorStatus(FetchOutcome.Status(503))))
        assertEquals("catalogue \"qa artist\": error 500", MusicCatalogue.line("qa artist", MusicCatalogue.errorStatus(FetchOutcome.Status(500))))
        assertEquals("catalogue \"qa artist\": error connect", MusicCatalogue.line("qa artist", MusicCatalogue.errorStatus(FetchOutcome.NoConnection)))
        // A typed query cannot start a line of its own.
        assertEquals("catalogue \"a[music] forged\": offline", MusicCatalogue.line("a\n[music] forged", MusicCatalogue.STATUS_OFFLINE))
        assertEquals(MusicCatalogueNotice.BUSY, MusicCatalogue.notice(FetchOutcome.Status(503)))
        assertEquals(MusicCatalogueNotice.NOT_ANSWERING, MusicCatalogue.notice(FetchOutcome.Status(500)))
        assertEquals(MusicCatalogueNotice.NOT_ANSWERING, MusicCatalogue.notice(FetchOutcome.NoConnection))
    }

    // ---- the session: one request per submit, the User-Agent, the lines, the session's cache

    private class Wire(var online: Boolean = true, var outcome: FetchOutcome) {
        val gets = mutableListOf<Pair<String, Map<String, String>>>()
        val hops = mutableListOf<Pair<String, Map<String, String>>>()
        val lines = mutableListOf<String>()
        var image: (String) -> ImageHop = { ImageHop.Failed }
        var clock = 0L
        val starts = mutableListOf<Long>()
        val session = CatalogueSession(
            apiBase = { MusicCatalogue.API }, coverBase = { MusicCatalogue.COVERS }, userAgent = "Tessera/1.2 (test)",
            online = { online },
            get = { url, headers -> starts += clock; gets += url to headers; outcome },
            hop = { url, headers -> hops += url to headers; image(url) },
            gate = RequestGate({ clock }) { clock += it },
            log = { lines += it },
        )
    }

    @Test fun `a search is one request with the User-Agent, its rows kept for the session`() {
        val wire = Wire(outcome = FetchOutcome.Answer(answer))
        val result = wire.session.search(" qa artist ")
        assertEquals(listOf("QA Song A", "QA Song B"), result.tracks.map { it.title })
        assertEquals(MusicCatalogueNotice.NONE, result.notice)
        assertEquals(listOf("https://musicbrainz.org/ws/2/recording?query=qa+artist&fmt=json&limit=25"), wire.gets.map { it.first })
        assertEquals("Tessera/1.2 (test)", wire.gets.single().second["User-Agent"])
        assertEquals(listOf("catalogue \"qa artist\": 2"), wire.lines)
        // The same search again, however it is typed: the session's rows, no request, no line.
        assertEquals(2, wire.session.search("QA Artist").tracks.size)
        assertEquals(2, wire.session.cached("qa artist")!!.size)
        assertEquals(1, wire.gets.size)
        assertEquals(1, wire.lines.size)
        assertNull(wire.session.cached("someone else"))
        // Nothing typed asks nothing.
        assertTrue(wire.session.search("   ").tracks.isEmpty())
        assertEquals(1, wire.gets.size)
    }

    @Test fun `a 503 is logged and NEVER retried - the next request is the next submit`() {
        val wire = Wire(outcome = FetchOutcome.Status(503))
        val result = wire.session.search("qa artist")
        assertTrue(result.tracks.isEmpty())
        assertEquals(MusicCatalogueNotice.BUSY, result.notice)
        assertEquals("one request, no retry", 1, wire.gets.size)
        assertEquals(listOf("catalogue \"qa artist\": error 503"), wire.lines)
        // A failure is not kept: the user's next submit asks again, a second later at the earliest.
        assertNull(wire.session.cached("qa artist"))
        wire.outcome = FetchOutcome.Answer(answer)
        assertEquals(2, wire.session.search("qa artist").tracks.size)
        assertEquals(2, wire.gets.size)
        assertEquals(listOf(0L, 1000L), wire.starts)
    }

    @Test fun `offline, a failed connection and an answer that is not a search each say so`() {
        val wire = Wire(online = false, outcome = FetchOutcome.Answer(answer))
        assertEquals(MusicCatalogueNotice.OFFLINE, wire.session.search("qa artist").notice)
        assertEquals("offline: no request at all", 0, wire.gets.size)
        wire.online = true
        wire.outcome = FetchOutcome.NoConnection
        assertEquals(MusicCatalogueNotice.NOT_ANSWERING, wire.session.search("qa artist").notice)
        wire.outcome = FetchOutcome.Answer("<html>")
        assertEquals(MusicCatalogueNotice.NOT_ANSWERING, wire.session.search("qa artist").notice)
        assertEquals(listOf("catalogue \"qa artist\": offline", "catalogue \"qa artist\": error connect", "catalogue \"qa artist\": error parse"), wire.lines)
    }

    @Test fun `five searches submitted back to back start a second apart`() {
        val wire = Wire(outcome = FetchOutcome.Answer(answer))
        for (n in 1..5) wire.session.search("song $n")
        assertEquals(listOf(0L, 1000L, 2000L, 3000L, 4000L), wire.starts)
    }

    @Test fun `a cover is front-250 of the row's release, asked with the User-Agent, kept for the session`() {
        val wire = Wire(outcome = FetchOutcome.Answer(answer))
        val row = wire.session.search("qa artist").tracks.first()
        val png = byteArrayOf(1, 2, 3)
        wire.image = { url -> if (url.startsWith("https://coverartarchive.org/")) ImageHop.Redirect("https://archive.org/download/x/y.jpg") else ImageHop.Body(png) }
        assertArrayEquals(png, wire.session.cover(row))
        assertEquals(
            listOf(
                "https://coverartarchive.org/release/$release/front-250" to mapOf("User-Agent" to "Tessera/1.2 (test)"),
                // The followed hop carries no header at all.
                "https://archive.org/download/x/y.jpg" to emptyMap(),
            ),
            wire.hops,
        )
        assertArrayEquals(png, wire.session.cover(row))
        assertEquals("the second read is the session's", 2, wire.hops.size)
        // No release: the placeholder, and nothing is asked. Offline: nothing is asked either.
        assertNull(wire.session.cover(row.copy(releaseId = null)))
        wire.online = false
        assertNull(wire.session.cover(row.copy(releaseId = "11111111-2222-3333-4444-555555555555")))
        assertEquals(2, wire.hops.size)
    }
}
