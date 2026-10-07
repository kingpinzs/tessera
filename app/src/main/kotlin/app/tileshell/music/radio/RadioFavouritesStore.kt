package app.tileshell.music.radio

import android.content.Context
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Where the favourite stations live (phase 20 build task 5): `radio_favourites.json` in the app's private files
 * directory, written with a temp file and a rename as [app.tileshell.music.PlaylistStore] writes its own, so a kill
 * mid-write leaves the previous file whole. Each favourite is the full slim row — it lists and plays with no
 * directory cache — and its last-played stamp, which "play radio" reads. [RadioFavourites] holds every rule about
 * what may be written; this file only knows how to keep it.
 */
class RadioFavouritesStore private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val state = MutableStateFlow(load())
    val favourites: StateFlow<List<Favourite>> = state.asStateFlow()

    /** The favourite stations, in order: [RadioFavourites.queueFor]'s second argument. */
    fun stations(): List<Station> = state.value.map { it.station }

    fun isFavourite(uuid: String): Boolean = RadioFavourites.isFavourite(state.value, uuid)

    /** False when the list is full ([RadioFavourites.MAX]) or the station is already there. */
    fun add(station: Station): Boolean = mutate("add") { RadioFavourites.add(it, station) }

    fun remove(uuid: String): Boolean = mutate("remove") { RadioFavourites.remove(it, uuid) }

    /** A user-initiated start of the favourite [uuid]: its last-played stamp. Nothing happens for another station. */
    fun played(uuid: String, wall: Long = System.currentTimeMillis()): Boolean = mutate(null) { RadioFavourites.played(it, uuid, wall) }

    /** "play radio": the favourite played most recently, else the first, else null. */
    fun lastPlayed(): Station? = RadioFavourites.lastPlayed(state.value)

    /** The directory refresh's fresh rows, by uuid. */
    fun refresh(fresh: List<Station>): Boolean = mutate("refresh") { RadioFavourites.refreshed(it, fresh) }

    @Synchronized
    private fun mutate(what: String?, block: (List<Favourite>) -> List<Favourite>): Boolean {
        val next = block(state.value)
        if (next == state.value) return false
        save(next)
        state.value = next
        if (what != null) Diagnostics.add(StreamLine.TAG, "radio: favourites $what: ${next.size} station(s)")
        return true
    }

    private fun save(list: List<Favourite>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(RadioFavourites.toJson(list))
        if (!tmp.renameTo(file)) error("radio favourites rename failed")
    }

    private fun load(): List<Favourite> {
        if (!file.exists()) return emptyList()
        val list = runCatching { RadioFavourites.fromJson(file.readText()) }.getOrNull()
        // Said, not swallowed: a pivot with no favourites and a file that would not parse look the same on screen.
        if (list == null) Diagnostics.add(StreamLine.TAG, "radio: favourites would not parse, starting empty")
        return list.orEmpty()
    }

    companion object {
        const val FILE_NAME = "radio_favourites.json"

        @Volatile
        private var instance: RadioFavouritesStore? = null

        fun get(context: Context): RadioFavouritesStore =
            instance ?: synchronized(this) {
                instance ?: RadioFavouritesStore(context.applicationContext).also { instance = it }
            }
    }
}
