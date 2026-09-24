package app.tileshell.tiles.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L11-1 (Jeremy "(a)", 2026-09-24): a playing session > the API queue > notifications > a paused track. */
class TileSourcePrecedenceTest {
    private val playing = TileContent(emptyList(), front = TileFace.NowPlaying(null, "Song", "Artist", playing = true, controls = listOf(Transport.PLAY_PAUSE)), sourceTag = "music")
    private val paused = TileContent(listOf(TileFace.NowPlaying(null, "Song", "Artist", playing = false)), sourceTag = "music")
    private val api = TileContent(listOf(TileFace.TextLines(listOf("from the app"))), sourceTag = "api")
    private val notif = TileContent(listOf(TileFace.TextLines(listOf("a message"))), sourceTag = "notification")

    private fun winner(vararg s: Pair<PackageSource, TileContent>) = TileSourcePrecedence.resolve(mapOf(*s))?.first

    @Test fun aPlayingSessionBeatsEverything() {
        assertEquals(PackageSource.MUSIC, winner(PackageSource.MUSIC to playing, PackageSource.API to api, PackageSource.NOTIFICATIONS to notif))
        assertEquals(PackageSource.MUSIC, winner(PackageSource.NOTIFICATIONS to notif, PackageSource.MUSIC to playing))
    }

    @Test fun theApiQueueBeatsNotificationsAndAPausedTrack() {
        assertEquals(PackageSource.API, winner(PackageSource.API to api, PackageSource.NOTIFICATIONS to notif))
        assertEquals(PackageSource.API, winner(PackageSource.MUSIC to paused, PackageSource.API to api))
    }

    @Test fun notificationsBeatAPausedTrack() =
        assertEquals(PackageSource.NOTIFICATIONS, winner(PackageSource.MUSIC to paused, PackageSource.NOTIFICATIONS to notif))

    @Test fun aPausedTrackShowsWhenNothingElseHas() = assertEquals(PackageSource.MUSIC, winner(PackageSource.MUSIC to paused))

    @Test fun nothingIsNothing() = assertNull(TileSourcePrecedence.resolve(emptyMap()))

    /** The race as the gate saw it (qa/phase-11/E10/l11-1-sequence.txt): the face, then notification updates with none eligible. */
    @Test fun notificationNullsNoLongerWipeAPlayingFace() {
        val pkg = "test.l11_1.race"
        LiveTileEngine.publishPackage(pkg, PackageSource.MUSIC, playing)
        repeat(7) { LiveTileEngine.publishPackage(pkg, PackageSource.NOTIFICATIONS, null) }
        assertEquals(playing, LiveTileEngine.content.value[LiveTileEngine.packageKey(pkg)])
        // ... and the API clearing its queue does not either.
        LiveTileEngine.publishPackage(pkg, PackageSource.API, null)
        assertEquals(playing, LiveTileEngine.content.value[LiveTileEngine.packageKey(pkg)])
    }

    @Test fun eachSourceClearsOnlyItsOwnSlot() {
        val pkg = "test.l11_1.slots"
        LiveTileEngine.publishPackage(pkg, PackageSource.NOTIFICATIONS, notif)
        LiveTileEngine.publishPackage(pkg, PackageSource.MUSIC, playing)
        assertEquals(playing, LiveTileEngine.content.value[LiveTileEngine.packageKey(pkg)])
        LiveTileEngine.publishPackage(pkg, PackageSource.MUSIC, paused)          // paused: the message shows again
        assertEquals(notif, LiveTileEngine.content.value[LiveTileEngine.packageKey(pkg)])
        LiveTileEngine.publishPackage(pkg, PackageSource.NOTIFICATIONS, null)    // the message goes: the paused track
        assertEquals(paused, LiveTileEngine.content.value[LiveTileEngine.packageKey(pkg)])
        LiveTileEngine.publishPackage(pkg, PackageSource.MUSIC, null)
        assertNull(LiveTileEngine.content.value[LiveTileEngine.packageKey(pkg)])
    }
}
