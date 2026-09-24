package app.tileshell.tiles.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** F-4: "playing" is a front NowPlaying face whose playing is true — a front face alone is not. */
    @Test fun aFrontFaceThatIsNotPlayingIsNotPlaying() {
        assertFalse(TileSourcePrecedence.playing(TileContent(emptyList(), front = TileFace.NowPlaying(null, "S", "A", playing = false))))
        assertFalse(TileSourcePrecedence.playing(paused))
        assertTrue(TileSourcePrecedence.playing(playing))
    }

    /** F-5 (i): the API queue clearing lets the notification show again (row (a) of the device gate, on the JVM). */
    @Test fun theApiClearingFallsBackToNotifications() {
        val pkg = "test.l11_1.apiclear"
        LiveTileEngine.publishPackage(pkg, PackageSource.NOTIFICATIONS, notif)
        LiveTileEngine.publishPackage(pkg, PackageSource.API, api)
        assertEquals(api, LiveTileEngine.content.value[LiveTileEngine.packageKey(pkg)])
        LiveTileEngine.publishPackage(pkg, PackageSource.API, null)
        assertEquals(notif, LiveTileEngine.content.value[LiveTileEngine.packageKey(pkg)])
    }

    /** F-5 (ii): content with no faces and no front clears that source, as a null does. */
    @Test fun emptyContentClearsItsSource() {
        val pkg = "test.l11_1.empty"
        LiveTileEngine.publishPackage(pkg, PackageSource.NOTIFICATIONS, notif)
        LiveTileEngine.publishPackage(pkg, PackageSource.API, api)
        LiveTileEngine.publishPackage(pkg, PackageSource.API, TileContent(emptyList()))
        assertEquals(notif, LiveTileEngine.content.value[LiveTileEngine.packageKey(pkg)])
    }

    /** F-1: a key the arbiter never held — a secondary tile's — survives a producer clearing that "package". */
    @Test fun aSecondaryTilesKeyIsNeverClearedByTheArbiter() {
        LiveTileEngine.publish("pkg:test.owner#t1", api)
        LiveTileEngine.publishPackage("test.owner#t1", PackageSource.NOTIFICATIONS, null)
        assertEquals(api, LiveTileEngine.content.value["pkg:test.owner#t1"])
        assertFalse("test.owner#t1" in LiveTileEngine.packages())
    }

    /** F-2: a forgotten package keeps nothing from any source, and its producers are told. */
    @Test fun aForgottenPackageKeepsNothing() {
        val pkg = "test.l11_1.gone"
        val told = ArrayList<String>()
        LiveTileEngine.addForgetListener { told += it }
        LiveTileEngine.publishPackage(pkg, PackageSource.MUSIC, paused)
        LiveTileEngine.publishPackage(pkg, PackageSource.NOTIFICATIONS, notif)
        LiveTileEngine.forgetPackage(pkg)
        assertNull(LiveTileEngine.content.value[LiveTileEngine.packageKey(pkg)])
        assertFalse(pkg in LiveTileEngine.packages())
        assertTrue(pkg in told)
        // A later notification for a reinstalled package starts from nothing: the old paused face does not come back.
        LiveTileEngine.publishPackage(pkg, PackageSource.NOTIFICATIONS, notif)
        LiveTileEngine.publishPackage(pkg, PackageSource.NOTIFICATIONS, null)
        assertNull(LiveTileEngine.content.value[LiveTileEngine.packageKey(pkg)])
    }
}
