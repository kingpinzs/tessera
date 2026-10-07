package app.tileshell.music.radio

import app.tileshell.music.MusicSearch

/**
 * Whose search may find a radio station (phase 20, r3 D1; a trust rule). `MusicService` is exported, so any app's
 * controller can send the session a search — and a station match makes the shell open a network address a community
 * directory named. So the session hands `MusicSearch.resolve` the stations ONLY for a controller that runs as the
 * shell itself (Tess's `playFromSearch`, Music's own controller); every other controller's search stays the
 * library-only search it always was, whatever its words are. A uid below 0 is nobody's, as in `MusicItemRule`.
 *
 * Pure (`RadioSearchRuleTest`); `media/UriAccessWiringScanTest` holds the session to asking it with the controller's
 * uid beside the shell's real one and to having no other way to the stations.
 */
object RadioSearchRule {
    /** Whether a search by the controller [controllerUid] is resolved against the stations at all. */
    fun stationsFor(controllerUid: Int, myUid: Int): Boolean = myUid >= 0 && controllerUid == myUid

    /** The stations that controller's search is resolved against: [all] for the shell's own, none for anyone else — [all] is not even asked. */
    fun stationsFor(controllerUid: Int, myUid: Int, all: () -> MusicSearch.Stations): MusicSearch.Stations =
        if (stationsFor(controllerUid, myUid)) all() else MusicSearch.Stations.NONE
}
