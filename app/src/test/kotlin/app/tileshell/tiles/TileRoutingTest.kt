package app.tileshell.tiles

import app.tileshell.tiles.engine.TileRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileRoutingTest {
    private val shell = "app.tileshell"
    private val musicTag = "androidx.media3.session.id.music"
    private val musicKey = "cmp:app.tileshell/app.tileshell.music.MusicActivity"

    @Test fun sessionIdStripsTheMedia3Prefix() {
        assertEquals("music", TileRouting.sessionId(musicTag))
        assertEquals("recorder", TileRouting.sessionId("androidx.media3.session.id.recorder"))
        // Media3 with no id set writes the bare prefix: an empty id, which is not "music".
        assertEquals("", TileRouting.sessionId("androidx.media3.session.id."))
        assertNull(TileRouting.sessionId("MediaPlaybackService"))
        assertNull(TileRouting.sessionId(null))
    }

    @Test fun anotherAppsSessionKeepsThePackageRule() {
        assertEquals("pkg:org.oxycblt.auxio", TileRouting.sessionContentKey("org.oxycblt.auxio", "Auxio", shell))
        assertEquals("org.oxycblt.auxio", TileRouting.sessionGrowthKey("org.oxycblt.auxio", "Auxio", shell))
        // Even a third-party app that happens to use Media3's id form keeps the package rule.
        assertEquals("pkg:com.example", TileRouting.sessionContentKey("com.example", musicTag, shell))
    }

    @Test fun theShellsMusicSessionGoesToMusicsComponentOnly() {
        assertEquals(musicKey, TileRouting.sessionContentKey(shell, musicTag, shell))
        assertEquals(musicKey, TileRouting.sessionGrowthKey(shell, musicTag, shell))
    }

    @Test fun theShellsOwnSecondaryTilesOpenAlarmsAndClock() {
        // Phase 15 T15-40: a pinned timer or the stopwatch opens the clock's component, never "whichever in-APK app sorts first".
        assertEquals("app.tileshell.clock.ClockActivity", TileRouting.shellSecondaryActivity("timer.ta1b2c3d4"))
        assertEquals("app.tileshell.clock.ClockActivity", TileRouting.shellSecondaryActivity("stopwatch"))
        assertNull(TileRouting.shellSecondaryActivity("stopwatch2"))
        assertNull(TileRouting.shellSecondaryActivity("timer"))
        assertNull(TileRouting.shellSecondaryActivity("recorder.take1"))
    }

    @Test fun everyOtherShellSessionGoesToNoTile() {
        for (tag in listOf("androidx.media3.session.id.recorder", "androidx.media3.session.id.video", "androidx.media3.session.id.", "x", null)) {
            assertNull(tag, TileRouting.sessionContentKey(shell, tag, shell))
            assertNull(tag, TileRouting.sessionGrowthKey(shell, tag, shell))
        }
    }

    @Test fun aShellAppsTileReadsItsComponentAndNoCount() {
        assertEquals(musicKey, TileRouting.tileContentKey(shell, "app.tileshell.music.MusicActivity", shell))
        assertEquals(
            "cmp:app.tileshell/app.tileshell.clock.ClockActivity",
            TileRouting.tileContentKey(shell, "app.tileshell.clock.ClockActivity", shell),
        )
        assertEquals(musicKey, TileRouting.tileGrowthKey(shell, "app.tileshell.music.MusicActivity", shell))
        assertNull(TileRouting.tileBadgeKey(shell, shell))
    }

    @Test fun anotherAppsTileReadsItsPackage() {
        assertEquals("pkg:org.oxycblt.auxio", TileRouting.tileContentKey("org.oxycblt.auxio", "org.oxycblt.auxio.MainActivity", shell))
        assertEquals("org.oxycblt.auxio", TileRouting.tileGrowthKey("org.oxycblt.auxio", "org.oxycblt.auxio.MainActivity", shell))
        assertEquals("org.oxycblt.auxio", TileRouting.tileBadgeKey("org.oxycblt.auxio", shell))
    }

    @Test fun musicsFaceNeverReachesAnotherShellAppsTile() {
        val face = TileRouting.sessionContentKey(shell, musicTag, shell)
        for (cls in listOf("app.tileshell.clock.ClockActivity", "app.tileshell.calc.CalculatorActivity", "app.tileshell.recorder.RecorderActivity")) {
            assert(TileRouting.tileContentKey(shell, cls, shell) != face) { cls }
            assert(TileRouting.tileGrowthKey(shell, cls, shell) != TileRouting.sessionGrowthKey(shell, musicTag, shell)) { cls }
        }
    }
}
