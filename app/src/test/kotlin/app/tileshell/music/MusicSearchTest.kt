package app.tileshell.music

import app.tileshell.music.radio.Favourite
import app.tileshell.music.radio.RadioIndex
import app.tileshell.music.radio.Station
import app.tileshell.music.radio.station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSearchTest {

    private fun t(id: Long, title: String, artist: String, album: String, albumId: Long) =
        Track(id, title, artist, album, albumId, 90_000L, 0L)

    private val library = listOf(
        t(1, "Bloom", "Radiohead", "The King of Limbs", 10),
        t(2, "Codex", "Radiohead", "The King of Limbs", 10),
        t(3, "4 Minute Warning", "Radiohead", "The King of Limbs", 10),
        t(4, "An Ending", "Brian Eno", "Apollo", 20),
        t(5, "Deep Blue Day", "Brian Eno", "Apollo", 20),
        t(6, "Zoo Station", "U2", "Achtung Baby", 30),
    )

    @Test fun `an exact song plays from itself within its album`() {
        val m = MusicSearch.resolve("codex", library)!!
        assertEquals(MusicSearch.Kind.SONG, m.kind)
        assertEquals("Codex by Radiohead", m.label)
        assertEquals(listOf(1L, 2L, 3L), m.queue.map { it.id })
        assertEquals(1, m.startIndex)
    }

    @Test fun `an artist plays all of their tracks from the first`() {
        val m = MusicSearch.resolve("Brian Eno", library)!!
        assertEquals(MusicSearch.Kind.ARTIST, m.kind)
        assertEquals(listOf(4L, 5L), m.queue.map { it.id })
        assertEquals(0, m.startIndex)
    }

    @Test fun `an album matches without its leading the`() {
        val m = MusicSearch.resolve("king of limbs", library)!!
        assertEquals(MusicSearch.Kind.ALBUM, m.kind)
        assertEquals(3, m.queue.size)
    }

    @Test fun `a song beats an artist on the same words`() {
        val lib = library + t(7, "U2", "Someone", "Covers", 40)
        assertEquals(MusicSearch.Kind.SONG, MusicSearch.resolve("u2", lib)!!.kind)
    }

    @Test fun `contains matches only from three letters`() {
        assertEquals("Zoo Station by U2", MusicSearch.resolve("station", library)!!.label)
        assertNull(MusicSearch.resolve("oo", library))
    }

    @Test fun `punctuation and case do not matter`() {
        assertEquals(MusicSearch.Kind.SONG, MusicSearch.resolve("DEEP BLUE DAY!", library)!!.kind)
    }

    @Test fun `a name the library does not have resolves to nothing`() {
        assertNull(MusicSearch.resolve("zzqx nothing", library))
        assertNull(MusicSearch.resolve("", library))
        assertNull(MusicSearch.resolve("bloom", emptyList()))
    }

    // ---- phase 20 (r3 D6): the one resolver, with the stations ---------------------------------------------------

    private val jazzOne = station("j1", name = "QA Jazz One", tags = "jazz,smooth", clicks = 90)
    private val jazzTwo = station("j2", name = "QA Jazz Two", tags = "jazz", clicks = 40)
    private val news = station("n1", name = "QA News One", tags = "news", clicks = 70)
    private val bloomFm = station("b1", name = "Bloom FM", tags = "pop", clicks = 500)
    private val codex = station("c1", name = "Codex", tags = "talk", clicks = 300)
    private val paradise = station("p1", name = "Radio Paradise", tags = "eclectic", clicks = 800)
    private val jazzRadio = station("jr", name = "Jazz Radio", tags = "jazz", clicks = 5)
    private val directory = listOf(jazzOne, jazzTwo, news, bloomFm, codex, paradise)

    private fun stations(favourites: List<Favourite> = emptyList(), rows: List<Station> = directory) =
        MusicSearch.Stations(favourites) { RadioIndex.of(rows) }

    private fun resolve(query: String, favourites: List<Favourite> = emptyList(), rows: List<Station> = directory, lib: List<Track> = library) =
        MusicSearch.resolve(query, lib, stations(favourites, rows))

    private fun stationOf(m: MusicSearch.Match?): Station? {
        if (m?.station != null) {
            assertEquals(MusicSearch.Kind.STATION, m.kind)
            assertEquals(m.station!!.name, m.label)
            assertTrue("a station match carries no tracks", m.queue.isEmpty())
        }
        return m?.station
    }

    @Test fun `rule 1 - radio is the favourite played most recently`() {
        val favourites = listOf(Favourite(jazzTwo, 100L), Favourite(news, 900L), Favourite(jazzOne, 500L))
        assertEquals(news, stationOf(resolve("radio", favourites)))
        assertEquals(news, stationOf(resolve("  Radio! ", favourites)))
        // "the radio" is the same ask: a leading "the" never counts.
        assertEquals(news, stationOf(resolve("the radio", favourites)))
    }

    @Test fun `rule 1 - with none played yet, radio is the first favourite`() {
        assertEquals(jazzTwo, stationOf(resolve("radio", listOf(Favourite(jazzTwo), Favourite(news)))))
    }

    @Test fun `rule 1 - with no favourite, radio is a library song titled Radio, and the directory is never asked`() {
        val lib = library + t(7, "Radio", "Lana", "Born", 40)
        var asked = false
        val none = MusicSearch.Stations(emptyList()) { asked = true; RadioIndex.of(directory + station("r", name = "Radio", clicks = 9_000)) }
        val m = MusicSearch.resolve("radio", lib, none)!!
        assertEquals(MusicSearch.Kind.SONG, m.kind)
        assertEquals("Radio by Lana", m.label)
        assertNull(m.station)
        assertTrue("rule 1 never reaches the directory", !asked)
    }

    @Test fun `rule 1 - with no favourite and nothing in the library, radio is the miss`() {
        val lib = listOf(t(4, "An Ending", "Brian Eno", "Apollo", 20))
        assertNull(resolve("radio", lib = lib))
        assertNull(resolve("radio", lib = emptyList()))
    }

    @Test fun `rule 2 - jazz radio is the genre's most-clicked station`() {
        assertEquals(jazzOne, stationOf(resolve("jazz radio")))
        assertEquals(jazzOne, stationOf(resolve("radio jazz")))
        assertEquals(jazzOne, stationOf(resolve("Jazz Radio")))
        // By click count, whatever order the rows came in.
        assertEquals(jazzOne, stationOf(resolve("jazz radio", rows = listOf(jazzTwo, news, jazzOne))))
    }

    @Test fun `rule 2 - a favourite by exact name comes first, with the word and then without it`() {
        // "paradise radio" names the favourite "Paradise", although the directory has a Radio Paradise.
        val plain = station("pp", name = "Paradise", clicks = 1)
        assertEquals(plain, stationOf(resolve("paradise radio", listOf(Favourite(plain)))))
        assertEquals(paradise, stationOf(resolve("radio paradise", listOf(Favourite(plain), Favourite(paradise)))))
        // With the word wins over without it, whatever the favourites' order.
        val both = listOf(Favourite(station("jz", name = "Jazz")), Favourite(jazzRadio))
        assertEquals(jazzRadio, stationOf(resolve("jazz radio", both)))
        // A favourite beats the genre's most-clicked station.
        assertEquals("jz", stationOf(resolve("jazz radio", both.take(1)))!!.uuid)
    }

    @Test fun `rule 2 - then the directory's exact name, then the genre, then a name that contains the rest, then the library`() {
        // Exact name (with the word) before the genre.
        assertEquals(jazzRadio, stationOf(resolve("jazz radio", rows = directory + jazzRadio)))
        assertEquals(paradise, stationOf(resolve("radio paradise")))
        // No genre "news one": the name that contains the rest.
        assertEquals(news, stationOf(resolve("news one radio")))
        // Contains by popularity: both jazz rows contain "qa jazz"; the more clicked is first.
        assertEquals(jazzOne, stationOf(resolve("qa jazz radio")))
        // Under three letters nothing is contains-matched.
        assertNull(resolve("qa radio", lib = emptyList()))
        // Nothing among the stations: the library, by the whole query.
        val lib = library + t(8, "Zzyzx Radio", "Someone", "Roads", 50)
        assertEquals("Zzyzx Radio by Someone", resolve("zzyzx radio", lib = lib)!!.label)
        assertNull(resolve("zzyzx radio"))
    }

    @Test fun `rule 3 - a favourite's exact name beats the library`() {
        assertEquals(MusicSearch.Kind.SONG, resolve("codex")!!.kind)
        assertEquals(codex, stationOf(resolve("codex", listOf(Favourite(codex)))))
        assertEquals(codex, stationOf(resolve("CODEX!", listOf(Favourite(news), Favourite(codex)))))
    }

    @Test fun `rule 3 - bloom stays a library song although a cached station contains it, and the directory is not asked`() {
        var asked = false
        val m = MusicSearch.resolve("bloom", library, MusicSearch.Stations(emptyList()) { asked = true; RadioIndex.of(directory) })!!
        assertEquals("Bloom by Radiohead", m.label)
        assertNull(m.station)
        assertTrue("a library hit never reaches the directory", !asked)
        // Not even with no such song: the directory is never contains-matched for a query without "radio".
        assertNull(resolve("bloom", lib = emptyList()))
        assertNull(resolve("jazz"))
        // A favourite is not contains-matched either.
        assertEquals("Bloom by Radiohead", resolve("bloom", listOf(Favourite(bloomFm)))!!.label)
    }

    @Test fun `rule 3 - the cached directory's exact name comes last, after the library's contains`() {
        // "station" is contained in the library's "Zoo Station": the library wins over a station named exactly so.
        val exact = station("st", name = "Station", clicks = 9_999)
        assertEquals("Zoo Station by U2", resolve("station", rows = directory + exact)!!.label)
        // With nothing in the library, the exact name is found — and only the exact one.
        assertEquals(news, stationOf(resolve("qa news one")))
        assertEquals(news, stationOf(resolve("QA  News One")))
        assertNull(resolve("qa news"))
        // And an exact library song still beats it.
        assertEquals("Codex by Radiohead", resolve("codex")!!.label)
        assertEquals(codex, stationOf(resolve("codex", lib = emptyList())))
    }

    @Test fun `a miss, an empty query, and no station at all`() {
        assertNull(resolve("zzqx nothing"))
        assertNull(resolve(""))
        assertNull(resolve("   "))
        // No stations handed: the library-only search, word for word.
        for (q in listOf("codex", "Brian Eno", "king of limbs", "station", "oo", "zzqx nothing", "", "radio", "jazz radio", "qa news one")) {
            assertEquals(q, MusicSearch.resolve(q, library), MusicSearch.resolve(q, library, MusicSearch.Stations.NONE))
        }
    }
}
