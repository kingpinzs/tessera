package app.tileshell.music.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (Decisions "the station directory", "queues"; r3 D15): every rule about the favourites. */
class RadioFavouritesTest {
    private val jazz = station("jazz-one", "QA Jazz One")
    private val news = station("news-one", "QA News One")
    private val rock = station("rock-one", "QA Rock One")

    private fun favs(vararg stations: Station) = stations.fold(emptyList<Favourite>()) { list, s -> RadioFavourites.add(list, s) }

    @Test fun `add puts a station at the end, once`() {
        val list = favs(jazz, news)
        assertEquals(listOf(jazz, news), list.map { it.station })
        assertTrue(list.all { it.lastPlayedWall == 0L })
        assertSame(list, RadioFavourites.add(list, jazz))
        assertSame(list, RadioFavourites.add(list, jazz.copy(name = "Renamed")))
        assertTrue(RadioFavourites.isFavourite(list, "jazz-one"))
        assertFalse(RadioFavourites.isFavourite(list, "rock-one"))
    }

    @Test fun `remove takes it out and leaves the order, and a station that is not there changes nothing`() {
        val list = favs(jazz, news, rock)
        assertEquals(listOf(jazz, rock), RadioFavourites.remove(list, "news-one").map { it.station })
        assertSame(list, RadioFavourites.remove(list, "nobody"))
        assertEquals(emptyList<Favourite>(), RadioFavourites.remove(favs(jazz), "jazz-one"))
    }

    @Test fun `the list holds 200 and the 201st is not added`() {
        assertEquals(200, RadioFavourites.MAX)
        var list = emptyList<Favourite>()
        for (i in 1..200) list = RadioFavourites.add(list, station("s$i"))
        assertEquals(200, list.size)
        assertSame(list, RadioFavourites.add(list, station("s201")))
        // One out, one in.
        val room = RadioFavourites.remove(list, "s7")
        assertEquals(200, RadioFavourites.add(room, station("s201")).size)
        assertEquals("s201", RadioFavourites.add(room, station("s201")).last().station.uuid)
    }

    @Test fun `played stamps the favourite and nothing else`() {
        val list = RadioFavourites.played(favs(jazz, news), "news-one", 1_700_000_000_000L)
        assertEquals(listOf(0L, 1_700_000_000_000L), list.map { it.lastPlayedWall })
        assertEquals(listOf(jazz, news), list.map { it.station })
        assertSame(list, RadioFavourites.played(list, "rock-one", 5L))
    }

    @Test fun `play radio is the favourite played most recently, else the first, else none`() {
        assertNull(RadioFavourites.lastPlayed(emptyList()))
        assertEquals(jazz, RadioFavourites.lastPlayed(favs(jazz, news, rock)))
        var list = RadioFavourites.played(favs(jazz, news, rock), "news-one", 100L)
        assertEquals(news, RadioFavourites.lastPlayed(list))
        list = RadioFavourites.played(list, "rock-one", 200L)
        assertEquals(rock, RadioFavourites.lastPlayed(list))
        list = RadioFavourites.played(list, "news-one", 300L)
        assertEquals(news, RadioFavourites.lastPlayed(list))
        // The one played last is removed: the next most recent.
        assertEquals(rock, RadioFavourites.lastPlayed(RadioFavourites.remove(list, "news-one")))
    }

    @Test fun `a favourite's queue is the favourites in order, starting at it`() {
        val favourites = listOf(jazz, news, rock)
        assertEquals(StationQueue(favourites, 0), RadioFavourites.queueFor(jazz, favourites))
        assertEquals(StationQueue(favourites, 1), RadioFavourites.queueFor(news, favourites))
        assertEquals(StationQueue(favourites, 2), RadioFavourites.queueFor(rock, favourites))
        // It is a favourite by its uuid: the directory's fresher row of the same station starts at the same place.
        assertEquals(StationQueue(favourites, 1), RadioFavourites.queueFor(news.copy(name = "QA News 1", clickCount = 9), favourites))
    }

    @Test fun `a non-favourite's queue is that station alone`() {
        val other = station("other")
        assertEquals(StationQueue(listOf(other), 0), RadioFavourites.queueFor(other, listOf(jazz, news)))
        assertEquals(StationQueue(listOf(other), 0), RadioFavourites.queueFor(other, emptyList()))
    }

    @Test fun `a refresh by uuid replaces the rows it names and keeps order and stamps`() {
        val list = RadioFavourites.played(favs(jazz, news, rock), "news-one", 42L)
        val fresh = listOf(news.copy(urlResolved = "http://new.example.net/news", clickCount = 7), station("stranger"))
        val out = RadioFavourites.refreshed(list, fresh)
        assertEquals(listOf("jazz-one", "news-one", "rock-one"), out.map { it.station.uuid })
        assertEquals("http://new.example.net/news", out[1].station.urlResolved)
        assertEquals(42L, out[1].lastPlayedWall)
        assertEquals(jazz, out[0].station)
        assertEquals(list, RadioFavourites.refreshed(list, emptyList()))
    }

    @Test fun `the file holds the full slim row and the stamp, and reads back the same`() {
        val full = Station("u-1", "Jazz \"One\"", "http://a.example.net/listen.pls", "http://a.example.net/live", "https://a.example.net/logo.png", "jazz,smooth jazz", "DE", "MP3", 128, true, 4321)
        val list = RadioFavourites.played(favs(full, news), "u-1", 1_700_000_000_123L)
        val text = RadioFavourites.toJson(list)
        assertEquals(list, RadioFavourites.fromJson(text))
        assertTrue(text, text.contains("\"lastPlayedWall\":1700000000123"))
        assertTrue(text, text.contains("\"url_resolved\":\"http://a.example.net/live\""))
    }

    @Test fun `a file that is not the favourites' is null, and a damaged row is skipped`() {
        assertNull(RadioFavourites.fromJson(""))
        assertNull(RadioFavourites.fromJson("not json"))
        assertNull(RadioFavourites.fromJson("[]"))
        assertNull(RadioFavourites.fromJson("{\"version\":1}"))
        assertEquals(emptyList<Favourite>(), RadioFavourites.fromJson("{\"favourites\":[]}"))
        val text = "{\"favourites\":[{\"station\":{\"name\":\"no uuid\"}},7,{\"station\":${app.tileshell.net.MiniJson.write(jazz.toJson())},\"lastPlayedWall\":-5}," +
            "{\"station\":${app.tileshell.net.MiniJson.write(jazz.toJson())},\"lastPlayedWall\":9}]}"
        assertEquals(listOf(Favourite(jazz, 0L)), RadioFavourites.fromJson(text))
    }

    @Test fun `a file with more than 200 rows is read as its first 200`() {
        val rows = (1..250).joinToString(",") { "{\"station\":${app.tileshell.net.MiniJson.write(station("s$it").toJson())}}" }
        val list = RadioFavourites.fromJson("{\"favourites\":[$rows]}")!!
        assertEquals(200, list.size)
        assertEquals("s200", list.last().station.uuid)
    }
}
