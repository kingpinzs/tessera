package app.tileshell.music.radio

import app.tileshell.net.MiniJson
import app.tileshell.net.jsonArray
import app.tileshell.net.jsonLong
import app.tileshell.net.jsonObject

/** A favourite station: the full slim row, and when it was last started (wall clock; 0 = never). */
data class Favourite(val station: Station, val lastPlayedWall: Long = 0L)

/** A station queue and where it starts. */
data class StationQueue(val stations: List<Station>, val start: Int)

/**
 * Every rule about the favourites (phase 20 Decisions "the station directory", "queues"; r3 D15): at most [MAX], in
 * the order they were added, each station once. [RadioFavouritesStore] only knows how to keep what these give it.
 * Pure (`RadioFavouritesTest`).
 */
object RadioFavourites {
    const val MAX = 200

    fun isFavourite(list: List<Favourite>, uuid: String): Boolean = list.any { it.station.uuid == uuid }

    /** [station] added at the end; the same list when it is already there or the list is full. */
    fun add(list: List<Favourite>, station: Station): List<Favourite> =
        if (list.size >= MAX || isFavourite(list, station.uuid)) list else list + Favourite(station)

    fun remove(list: List<Favourite>, uuid: String): List<Favourite> =
        if (isFavourite(list, uuid)) list.filterNot { it.station.uuid == uuid } else list

    /** The last-played stamp of the favourite [uuid] set to [wall]; the same list when it is not a favourite. */
    fun played(list: List<Favourite>, uuid: String, wall: Long): List<Favourite> =
        if (isFavourite(list, uuid)) list.map { if (it.station.uuid == uuid) it.copy(lastPlayedWall = wall) else it } else list

    /** "play radio": the favourite played most recently, else the first favourite, else none. */
    fun lastPlayed(list: List<Favourite>): Station? =
        (list.filter { it.lastPlayedWall > 0L }.maxByOrNull { it.lastPlayedWall } ?: list.firstOrNull())?.station

    /**
     * What a tap on [station] plays: a favourite → the favourites in order, starting at it (next / previous step
     * through them like presets); any other station → that station alone.
     */
    fun queueFor(station: Station, favourites: List<Station>): StationQueue {
        val at = favourites.indexOfFirst { it.uuid == station.uuid }
        return if (at >= 0) StationQueue(favourites, at) else StationQueue(listOf(station), 0)
    }

    /** The directory's refresh by uuid: each favourite's row replaced by its fresh one; order and stamps kept. */
    fun refreshed(list: List<Favourite>, fresh: List<Station>): List<Favourite> {
        val byUuid = fresh.associateBy { it.uuid }
        return list.map { f -> byUuid[f.station.uuid]?.let { f.copy(station = it) } ?: f }
    }

    private const val VERSION = 1L

    fun toJson(list: List<Favourite>): String = MiniJson.write(
        linkedMapOf(
            "version" to VERSION,
            "favourites" to list.map { linkedMapOf("station" to it.station.toJson(), "lastPlayedWall" to it.lastPlayedWall) },
        ),
    )

    /** The file's favourites — each station once, at most [MAX] — or null when the text is not the file's form. */
    fun fromJson(text: String): List<Favourite>? {
        val rows = MiniJson.parseOrNull(text).jsonObject()?.get("favourites") as? List<*> ?: return null
        val out = ArrayList<Favourite>()
        for (row in rows.jsonArray()) {
            val o = row.jsonObject() ?: continue
            val station = Station.fromJson(o["station"].jsonObject()) ?: continue
            if (out.size < MAX && out.none { it.station.uuid == station.uuid }) out += Favourite(station, (o.jsonLong("lastPlayedWall") ?: 0L).coerceAtLeast(0L))
        }
        return out
    }
}
