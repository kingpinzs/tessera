package app.tileshell.music

/**
 * Playlists (phase 10 build task 8): create, rename, reorder, delete, and the two verbs those imply —
 * add a track and take one out. Pure, so every rule below is proved on the JVM.
 *
 * ### Why the build keeps its own, and where
 *
 * `MediaStore.Audio.Playlists` was deprecated in API 30 and is not writable under scoped storage: an
 * insert is ignored or throws, and the phase doc already records that. So the store is this build's
 * own — `music_playlists.json` in the app's private files directory, written the way
 * [app.tileshell.tiles.LayoutStore] writes `start_layout.json` (a temp file and a rename, so a kill
 * mid-write leaves the previous file intact rather than half a new one). That is the build-time call
 * task 8 was told to record when it started.
 *
 * ### What a playlist holds, and what it does not
 *
 * It holds **MediaStore track ids**, not file paths and not copies of the metadata. Two consequences,
 * both deliberate:
 *
 *  - A track's title, artist and album always come from the library, so a file that gets retagged
 *    shows its new tags in every playlist at once rather than keeping a stale copy.
 *  - An id the library no longer has is **skipped when the playlist is drawn but NOT deleted from the
 *    file**. A volume that is unmounted, or a rescan in progress, makes tracks vanish temporarily, and
 *    a store that pruned on sight would quietly eat a playlist the first time that happened.
 *
 * Duplicates are allowed. A person who adds the same track twice meant to, and W10M did not stop them.
 */
data class Playlist(val id: String, val name: String, val trackIds: List<Long>) {
    val size: Int get() = trackIds.size
}

object PlaylistRules {

    const val DEFAULT_NAME = "New playlist"

    /**
     * A name for a playlist nobody has named: "New playlist", then "New playlist (2)", and so on.
     *
     * Duplicate names are legal — the id is the identity — but handing out the same default twice in a
     * row would produce two rows that cannot be told apart at the moment they are made, which is when
     * it matters most.
     */
    fun defaultName(existing: List<Playlist>): String {
        val taken = existing.map { it.name }.toSet()
        if (DEFAULT_NAME !in taken) return DEFAULT_NAME
        var n = 2
        while ("$DEFAULT_NAME ($n)" in taken) n++
        return "$DEFAULT_NAME ($n)"
    }

    /** An id that is unique within [existing]; the clock supplies it and the loop settles collisions. */
    fun newId(nowMs: Long, existing: List<Playlist>): String {
        val taken = existing.map { it.id }.toSet()
        var candidate = "pl-$nowMs"
        var n = 1
        while (candidate in taken) candidate = "pl-$nowMs-${n++}"
        return candidate
    }

    fun create(playlists: List<Playlist>, id: String, name: String): List<Playlist> =
        playlists + Playlist(id, clean(name).ifEmpty { defaultName(playlists) }, emptyList())

    /**
     * Rename, ignoring a blank. An empty name is not a rename anyone meant: it would draw an empty row
     * that cannot be told from the others, and the old name is the better answer than a made-up one.
     */
    fun rename(playlists: List<Playlist>, id: String, name: String): List<Playlist> {
        val wanted = clean(name)
        if (wanted.isEmpty()) return playlists
        return playlists.map { if (it.id == id) it.copy(name = wanted) else it }
    }

    fun delete(playlists: List<Playlist>, id: String): List<Playlist> = playlists.filterNot { it.id == id }

    /** Append a track. Appending, not inserting, because a playlist is the order someone built. */
    fun add(playlists: List<Playlist>, id: String, trackId: Long): List<Playlist> =
        playlists.map { if (it.id == id) it.copy(trackIds = it.trackIds + trackId) else it }

    /** Remove by POSITION, not by track id: a duplicated track removes the one that was held. */
    fun removeAt(playlists: List<Playlist>, id: String, index: Int): List<Playlist> =
        playlists.map {
            if (it.id != id || index !in it.trackIds.indices) it
            else it.copy(trackIds = it.trackIds.toMutableList().apply { removeAt(index) })
        }

    /** Move one track to another position, clamping rather than refusing at the ends. */
    fun move(playlists: List<Playlist>, id: String, from: Int, to: Int): List<Playlist> =
        playlists.map { playlist ->
            if (playlist.id != id || from !in playlist.trackIds.indices) return@map playlist
            val target = to.coerceIn(0, playlist.trackIds.lastIndex)
            if (target == from) return@map playlist
            playlist.copy(
                trackIds = playlist.trackIds.toMutableList().apply { add(target, removeAt(from)) },
            )
        }

    /**
     * The tracks a playlist draws, in its own order.
     *
     * Ids the library does not have are skipped — see the note on the file about why they are skipped
     * rather than pruned — and a duplicated id appears as many times as it was added.
     */
    fun tracksOf(playlist: Playlist, library: List<Track>): List<Track> {
        val byId = library.associateBy { it.id }
        return playlist.trackIds.mapNotNull { byId[it] }
    }

    /** Playlists A-Z, the order the collection draws them in. */
    fun sorted(playlists: List<Playlist>): List<Playlist> =
        playlists.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    private fun clean(name: String): String = name.trim()
}
