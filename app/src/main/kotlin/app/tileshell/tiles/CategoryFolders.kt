package app.tileshell.tiles

/**
 * The Games and Office folders (Jeremy, 2026-09-22: "there should be games and office folders").
 *
 * WHAT GOES IN THEM is the whole question, and the answer is not a list this file keeps: every app
 * declares its own category in its manifest (`android:appCategory`), and Android hands that back as
 * [android.content.pm.ApplicationInfo.category]. A folder built from that is built from what each app
 * says about itself, so a game written after this file was is still a game, and nothing has to be
 * recognised by name. Apps that declare nothing stay out rather than being guessed at.
 *
 * The rules are pure and live here so the one thing that would be embarrassing to get wrong — a folder
 * appearing with one app in it, or with every app in it — is settled without a device.
 */
object CategoryFolders {

    /** The ADD markers. Versioned: a later change of rule is a new marker, not a silent re-seed. */
    const val GAMES_ADD = "folder:games:v1"
    const val OFFICE_ADD = "folder:office:v1"

    const val GAMES_NAME = "Games"
    const val OFFICE_NAME = "Office"

    /**
     * Fewer than two and it is not a folder: phase 02's H19 dissolves a folder left holding one tile, so
     * seeding one would produce a folder that vanishes the first time it is touched.
     */
    const val MIN_MEMBERS = 2

    /**
     * A cap, because a folder is opened and read: 16 is two full rows of medium tiles on the 4-unit grid
     * and more than that is a list, not a folder. The newest-installed win, which is the half a long list
     * is most likely to be looked for.
     */
    const val MAX_MEMBERS = 16

    /** One installed app, as much of it as the rules need. */
    data class Candidate(
        val key: TileKey,
        val label: String,
        /** [android.content.pm.ApplicationInfo.category]; -1 (UNDEFINED) when the app declares none. */
        val category: Int,
        val installedAtMs: Long,
    )

    /**
     * The members of the folder for [category], newest install first so the cap keeps what is current,
     * then alphabetical inside that. Empty when too few apps declare it — the caller creates nothing.
     */
    fun members(candidates: List<Candidate>, category: Int): List<TileKey> {
        val matching = candidates.filter { it.category == category }
        if (matching.size < MIN_MEMBERS) return emptyList()
        return matching
            .sortedWith(compareByDescending<Candidate> { it.installedAtMs }.thenBy { it.label.lowercase() })
            .take(MAX_MEMBERS)
            .map { it.key }
    }
}
