package app.tileshell.feeds

import app.tileshell.start.TileTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Photos tile's slideshow and picture frame (INDEX Change Log 2026-09-21 item 4).
 *
 * The rule that must never bend is "it does not flip when that is set", and a frame publishes NO live
 * faces at all — the photo replaces the logo — so "does not flip" is structural rather than a flag some
 * later change could forget to read. These tests hold that structure in place.
 */
class PhotoRulesTest {

    // ---- the picture-frame rule ----

    @Test
    fun `a main photo leaves the tile with no faces to flip between`() {
        val plan = PhotoRules.plan(frameSet = true, slideshow = false, photos = 20)
        assertTrue(plan.frame)
        assertEquals(0, plan.faces)
    }

    @Test
    fun `a picture frame does not run a slideshow cadence`() {
        assertEquals(0, PhotoRules.plan(frameSet = true, slideshow = false, photos = 20).slideshowMs)
    }

    @Test
    fun `a picture frame does not grow, because nothing is happening on it`() {
        assertFalse(PhotoRules.plan(frameSet = true, slideshow = false, photos = 20).grow)
    }

    @Test
    fun `a main photo outranks the slideshow switch, so the tile still does not flip`() {
        // "it does not flip when that is set" is unconditional, and a slideshow IS flipping.
        val plan = PhotoRules.plan(frameSet = true, slideshow = true, photos = 20)
        assertTrue(plan.frame)
        assertEquals(0, plan.faces)
        assertEquals(0, plan.slideshowMs)
        assertFalse(plan.grow)
    }

    @Test
    fun `removing the main photo hands the tile back to the slideshow switch`() {
        val framed = PhotoRules.plan(frameSet = true, slideshow = true, photos = 20)
        val unframed = PhotoRules.plan(frameSet = false, slideshow = true, photos = 20)
        assertTrue(framed.frame)
        assertFalse(unframed.frame)
        assertTrue(unframed.grow)
    }

    @Test
    fun `a frame reads no photos out of the gallery at all`() {
        assertEquals(0, PhotoRules.photosToRead(frameSet = true, slideshow = true))
    }

    // ---- the slideshow ----

    @Test
    fun `a running slideshow advances on the fixed cadence and grows the tile`() {
        val plan = PhotoRules.plan(frameSet = false, slideshow = true, photos = 12)
        assertEquals(TileTiming.SLIDESHOW_MS, plan.slideshowMs)
        assertTrue(plan.grow)
        assertEquals(12, plan.faces)
    }

    @Test
    fun `a slideshow shows more photos than the plain tile, and is still bounded`() {
        assertEquals(PhotoRules.MAX_SLIDESHOW_PHOTOS, PhotoRules.plan(false, slideshow = true, photos = 500).faces)
        assertTrue(PhotoRules.MAX_SLIDESHOW_PHOTOS > PhotoRules.MAX_PHOTOS)
    }

    @Test
    fun `one photo is not a slideshow, so the tile neither advances nor grows`() {
        val plan = PhotoRules.plan(frameSet = false, slideshow = true, photos = 1)
        assertEquals(0, plan.slideshowMs)
        assertFalse(plan.grow)
        assertEquals(1, plan.faces)
    }

    @Test
    fun `no photos at all is not a slideshow either`() {
        val plan = PhotoRules.plan(frameSet = false, slideshow = true, photos = 0)
        assertEquals(0, plan.faces)
        assertFalse(plan.grow)
    }

    // ---- the tile phase 01 built, unchanged ----

    @Test
    fun `with both settings off the tile is exactly what phase 01 built`() {
        val plan = PhotoRules.plan(frameSet = false, slideshow = false, photos = 30)
        assertFalse(plan.frame)
        assertEquals(PhotoRules.MAX_PHOTOS, plan.faces)
        assertEquals(0, plan.slideshowMs)
        assertFalse(plan.grow)
    }

    @Test
    fun `turning the slideshow off returns the tile to its stored size`() {
        assertTrue(PhotoRules.plan(false, slideshow = true, photos = 8).grow)
        assertFalse(PhotoRules.plan(false, slideshow = false, photos = 8).grow)
    }

    @Test
    fun `how many photos to read follows the mode`() {
        assertEquals(PhotoRules.MAX_PHOTOS, PhotoRules.photosToRead(frameSet = false, slideshow = false))
        assertEquals(PhotoRules.MAX_SLIDESHOW_PHOTOS, PhotoRules.photosToRead(frameSet = false, slideshow = true))
    }
}
