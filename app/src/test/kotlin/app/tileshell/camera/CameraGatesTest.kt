package app.tileshell.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The capability gates (phase 17 build task 6 clause 5; Decisions T17-2 as r3 D10 replaces its gates): the mode list
 * and every `unavailable` line are derived from a small characteristics value, in both directions.
 */
class CameraGatesTest {
    /** The emulator's back camera as BUILDSTART/README.md records it. */
    private val avd = CameraCaps(
        hardwareLevel = 3,
        capabilities = setOf(0, 1, 2, 5, 6, 4, 7, 3, 18, 19, 20, 8),
        aeModes = setOf(0, 1, 2, 3), evLow = -9, evHigh = 9, evStep = 1f / 3f,
        minFocusDistance = 20f, awbModes = setOf(0, 1, 2, 3, 5, 8), maxZoom = 10f,
        isoLow = 100, isoHigh = 1600, exposureLowNs = 1_000L, exposureHighNs = 300_000_000L, hasFlash = true,
    )
    private val avdEnv = CameraEnv(hdrExtension = false, highSpeedSession = false, primaryAbi = "x86_64", panoramaLibrary = false)
    private val phoneEnv = CameraEnv(hdrExtension = true, highSpeedSession = true, primaryAbi = "arm64-v8a", panoramaLibrary = true)
    private val bare = CameraCaps(
        hardwareLevel = 2, capabilities = setOf(0), aeModes = setOf(1), evLow = 0, evHigh = 0, evStep = 0f,
        minFocusDistance = 0f, awbModes = setOf(1), maxZoom = 1f,
    )

    @Test fun `the emulator shows photo video pro and living images and hides the rest with a reason each`() {
        assertEquals(listOf("photo", "video", "pro", "livingimages"), CameraGates.shownModeIds(avd, avdEnv))
        assertEquals(
            listOf(
                "mode hdr: unavailable (no HDR extension)",
                "mode slowmo: unavailable (no high-speed session)",
                "mode panorama: unavailable (no native library for x86_64)",
            ),
            CameraGates.hiddenLines(avd, avdEnv),
        )
    }

    @Test fun `a phone with everything shows every mode and owes no line`() {
        assertEquals(CameraGates.MODE_IDS, CameraGates.shownModeIds(avd, phoneEnv))
        assertEquals(emptyList<String>(), CameraGates.hiddenLines(avd, phoneEnv))
    }

    @Test fun `every mode is either shown or has its line, never both and never neither`() {
        for (caps in listOf(avd, bare)) for (env in listOf(avdEnv, phoneEnv, phoneEnv.copy(panoramaLibrary = false))) {
            val shown = CameraGates.shownModeIds(caps, env)
            val lines = CameraGates.hiddenLines(caps, env)
            for (id in CameraGates.MODE_IDS) {
                val hasLine = lines.any { it.startsWith("mode $id: unavailable (") }
                assertTrue("$id shown=${id in shown} line=$hasLine", (id in shown) != hasLine)
            }
        }
    }

    @Test fun `the emulator admits all five dial controls`() {
        assertTrue(CameraGates.proControls(avd).all { it.second.available })
        assertEquals(listOf(ProControl.EXPOSURE, ProControl.SHUTTER, ProControl.ISO, ProControl.FOCUS, ProControl.WB), CameraGates.proControls(avd).map { it.first })
    }

    @Test fun `exposure needs only a compensation range other than zero to zero`() {
        assertTrue(CameraGates.exposure(bare.copy(evLow = -6, evHigh = 6)).available)
        assertEquals("mode pro.exposure: unavailable (no exposure compensation)", CameraGates.exposure(bare).line)
    }

    @Test fun `iso and shutter need auto-exposure off and the manual sensor together`() {
        val noOff = avd.copy(aeModes = setOf(1, 2, 3))
        assertEquals("auto-exposure cannot be turned off", CameraGates.iso(noOff).reason)
        assertFalse(CameraGates.shutter(noOff).available)
        val noManual = avd.copy(capabilities = setOf(0))
        assertEquals("no manual sensor", CameraGates.iso(noManual).reason)
        assertEquals("no manual sensor", CameraGates.shutter(noManual).reason)
        assertFalse(CameraGates.iso(avd.copy(isoLow = null)).available)
        assertFalse(CameraGates.shutter(avd.copy(exposureHighNs = null)).available)
    }

    @Test fun `manual focus needs a minimum focus distance above zero`() {
        assertTrue(CameraGates.focus(avd).available)
        assertEquals("mode pro.focus: unavailable (fixed focus)", CameraGates.focus(avd.copy(minFocusDistance = 0f)).line)
    }

    @Test fun `each white balance preset is offered only if the camera lists it`() {
        assertEquals(listOf(WbPreset.INCANDESCENT, WbPreset.FLUORESCENT, WbPreset.DAYLIGHT, WbPreset.SHADE), CameraGates.wbPresets(avd))
        // AWB OFF (0) is not a preset, and its absence hides nothing (r3 D10).
        assertEquals(CameraGates.wbPresets(avd), CameraGates.wbPresets(avd.copy(awbModes = setOf(1, 2, 3, 5, 8))))
        assertFalse(CameraGates.wb(bare).available)
    }

    @Test fun `a camera with no manual control hides the dial with one line and none for its controls`() {
        assertEquals("mode pro: unavailable (no manual control)", CameraGates.pro(bare).line)
        assertEquals(1, CameraGates.hiddenLines(bare, phoneEnv).count { it.startsWith("mode pro") })
    }

    @Test fun `a dial that is shown says which of its controls is hidden`() {
        val partly = avd.copy(minFocusDistance = 0f, aeModes = setOf(1))
        val lines = CameraGates.hiddenLines(partly, phoneEnv)
        assertEquals(
            listOf(
                "mode pro.shutter: unavailable (auto-exposure cannot be turned off)",
                "mode pro.iso: unavailable (auto-exposure cannot be turned off)",
                "mode pro.focus: unavailable (fixed focus)",
            ),
            lines,
        )
    }

    @Test fun `zoom and flash have their lines where the camera lacks them`() {
        assertEquals("mode zoom: unavailable (max zoom 1.0)", CameraGates.zoom(bare).line)
        assertTrue(CameraGates.zoom(avd).available)
        assertTrue(CameraGates.hiddenLines(bare, phoneEnv).containsAll(listOf("mode zoom: unavailable (max zoom 1.0)", "mode flash: unavailable (no flash unit)")))
    }

    @Test fun `panorama is hidden on any abi but arm64 and when the library did not load`() {
        assertEquals("no native library for x86_64", CameraGates.panorama(avdEnv).reason)
        assertEquals("no native library for armeabi-v7a", CameraGates.panorama(phoneEnv.copy(primaryAbi = "armeabi-v7a")).reason)
        assertEquals("the native library did not load", CameraGates.panorama(phoneEnv.copy(panoramaLibrary = false)).reason)
        assertTrue(CameraGates.panorama(phoneEnv).available)
    }

    @Test fun `the dynamic shortcuts are exactly the admitted modes at ranks two and three`() {
        assertEquals(emptyList<Pair<String, Int>>(), CameraGates.dynamicShortcuts(avd, avdEnv))
        assertEquals(listOf("camera_panorama" to 2, "camera_slowmo" to 3), CameraGates.dynamicShortcuts(avd, phoneEnv))
        assertEquals(listOf("camera_slowmo" to 3), CameraGates.dynamicShortcuts(avd, phoneEnv.copy(primaryAbi = "x86_64")))
        assertEquals(listOf("camera_panorama" to 2), CameraGates.dynamicShortcuts(avd, phoneEnv.copy(highSpeedSession = false)))
        // No camera open: nothing is published.
        assertEquals(emptyList<Pair<String, Int>>(), CameraGates.dynamicShortcuts(null, phoneEnv))
    }

    @Test fun `the discs are the next mode on the right and the previous on the left`() {
        val three = CameraGates.cycle(avd, phoneEnv)
        assertEquals(listOf("photo", "video", "panorama"), three)
        assertEquals(ModeDiscs("panorama", "video"), ModeDiscs.of(three, "photo"))
        assertEquals(ModeDiscs("photo", "panorama"), ModeDiscs.of(three, "video"))
        assertEquals(ModeDiscs("video", "photo"), ModeDiscs.of(three, "panorama"))
    }

    @Test fun `with two modes one disc is drawn, right from photo and left from video`() {
        val two = CameraGates.cycle(avd, avdEnv)
        assertEquals(listOf("photo", "video"), two)
        assertEquals(ModeDiscs(null, "video"), ModeDiscs.of(two, "photo"))
        assertEquals(ModeDiscs("photo", null), ModeDiscs.of(two, "video"))
    }

    @Test fun `the dial's values stay inside the sensor's ranges`() {
        assertEquals(listOf(100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600), ProValues.isos(avd))
        val speeds = ProValues.shutterSpeeds(avd)
        assertEquals("1/16000", speeds.first().second)
        assertEquals("1/4", speeds.last().second)
        assertTrue(speeds.all { it.first in 1_000L..300_000_000L })
        assertEquals(19, ProValues.evIndices(avd).size)
        assertEquals(listOf(0f, 10f, 20f), ProValues.focusStops(avd, 2))
    }

    @Test fun `the labels read as the dial shows them`() {
        assertEquals("0.0", ProValues.evLabel(0, 1f / 3f))
        assertEquals("+0.3", ProValues.evLabel(1, 1f / 3f))
        assertEquals("-3.0", ProValues.evLabel(-9, 1f / 3f))
        assertEquals("infinity", ProValues.focusLabel(0f))
        assertEquals("2.0 m", ProValues.focusLabel(0.5f))
        assertEquals("5 cm", ProValues.focusLabel(20f))
        assertEquals(listOf("auto", "incandescent", "fluorescent", "daylight", "shade"), ProValues.labels(avd, ProControl.WB))
        assertEquals("auto", ProValues.labels(avd, ProControl.ISO).first())
        assertEquals("0.0", ProValues.labels(avd, ProControl.EXPOSURE)[ProValues.dialIndex(avd, ProControl.EXPOSURE, null)])
    }

    @Test fun `dial positions and engine values convert both ways`() {
        for (control in ProControl.entries) for (dial in 0 until ProValues.labels(avd, control).size) {
            assertEquals(dial, ProValues.dialIndex(avd, control, ProValues.engineIndex(control, dial)))
        }
        assertEquals(null, ProValues.engineIndex(ProControl.ISO, 0))
        assertEquals(0, ProValues.engineIndex(ProControl.ISO, 1))
    }

    @Test fun `sizes are matched to the aspect ratio and labelled as W10M wrote them`() {
        assertTrue(CameraChoices.matchesAspect(4000, 3000, wide = false))
        assertTrue(CameraChoices.matchesAspect(1080, 1920, wide = true))
        assertFalse(CameraChoices.matchesAspect(1920, 1080, wide = false))
        assertFalse(CameraChoices.matchesAspect(0, 0, wide = false))
        assertEquals("JPEG (12 MP)", CameraChoices.sizeLabel(4000, 3000))
        assertEquals("JPEG (2.1 MP)", CameraChoices.sizeLabel(1920, 1080))
        assertEquals("1920x1080p/30 fps", CameraChoices.videoLabel(1080, 1920))
        assertEquals("2×", zoomLabel(2f))
        assertEquals("2.5×", zoomLabel(2.5f))
    }
}
