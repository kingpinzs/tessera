package app.tileshell.music

/**
 * What a play extra on MusicActivity's launch does (phase 18's ADD to phase 10: r3 D5, "below Q-18-2"; a trust rule).
 *
 * MusicActivity is exported, so an extra that starts playback must not be any app's to send: it is honoured ONLY for the
 * shell's own launch (`getLaunchedFromUid() == myUid`). It carries either one MediaStore audio id (a library track) or
 * one `content://` URI — and the URI form only when its authority is the shell's own FileProvider (a file with no
 * library row, handed over by Files; Q-18-2). Anything else is ignored and Music opens as it always does, with
 * `[music] play extra ignored: <why>`.
 *
 * Pure: the activity reads the two extras and the two uids and asks [decide]; `MusicPlayExtraTest` holds the rule.
 */
object MusicPlayExtra {
    /** Intent extra: one MediaStore audio id (a `Long`). */
    const val EXTRA_PLAY_ID = "app.tileshell.music.PLAY_ID"

    /** Intent extra: one `content://` URI of the shell's FileProvider (a `String`). */
    const val EXTRA_PLAY_URI = "app.tileshell.music.PLAY_URI"

    const val WHY_NOT_SHELL = "not the shell"
    const val WHY_TWO = "an id and a uri"
    const val WHY_BAD_ID = "not an audio id"
    const val WHY_NOT_CONTENT = "not a content uri"
    const val WHY_AUTHORITY = "not the shell's file provider"

    sealed interface Decision {
        /** The launch carries no play extra: Music opens as it always does, and nothing is written. */
        data object None : Decision

        /** Play the library track with this MediaStore audio id. */
        data class PlayId(val id: Long) : Decision

        /** Play this one file through the session, outside the library (`[music] play file <path> (not in library)`). */
        data class PlayUri(val uri: String) : Decision

        /** The extra is not honoured; Music opens as it always does. [line] goes to the `music` diagnostics tag. */
        data class Ignored(val why: String) : Decision {
            val line: String get() = "play extra ignored: $why"
        }
    }

    /**
     * @param launchedFromUid `Activity.getLaunchedFromUid()` (-1 when the platform does not say)
     * @param myUid `Process.myUid()`
     * @param id [EXTRA_PLAY_ID] when the launch has it, else null
     * @param uri [EXTRA_PLAY_URI] when the launch has it, else null
     * @param providerAuthority the shell FileProvider's authority, exactly as the manifest declares it
     */
    fun decide(launchedFromUid: Int, myUid: Int, id: Long?, uri: String?, providerAuthority: String): Decision {
        if (id == null && uri == null) return Decision.None
        // The caller first: for anyone but the shell the extra's contents are not even looked at.
        if (launchedFromUid != myUid) return Decision.Ignored(WHY_NOT_SHELL)
        if (id != null && uri != null) return Decision.Ignored(WHY_TWO)
        if (id != null) return if (id > 0) Decision.PlayId(id) else Decision.Ignored(WHY_BAD_ID)
        val u = uri!!
        if (!u.startsWith(CONTENT) || u.any { it <= ' ' || it == '\u007f' }) return Decision.Ignored(WHY_NOT_CONTENT)
        val rest = u.substring(CONTENT.length)
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        // Exactly the authority, nothing in front of it: `10@authority` would name another user's provider.
        if (providerAuthority.isEmpty() || authority != providerAuthority) return Decision.Ignored(WHY_AUTHORITY)
        // A provider URI names a file: a path after the authority, and no query or fragment to reinterpret it.
        val path = rest.substring(authority.length)
        if (path.length < 2 || !path.startsWith("/") || u.contains('?') || u.contains('#')) return Decision.Ignored(WHY_NOT_CONTENT)
        return Decision.PlayUri(u)
    }

    private const val CONTENT = "content://"
}
