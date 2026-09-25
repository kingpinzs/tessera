package app.tileshell.start

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 11's pure rules (build tasks 1, 3 and 4): the selection rule, the decision, the geometry, the motion. */
class QuickRulesTest {

    private fun m(id: String, rank: Int, enabled: Boolean = true) = ShortcutCandidate(id, manifest = true, rank = rank, enabled = enabled)
    private fun d(id: String, rank: Int, enabled: Boolean = true) = ShortcutCandidate(id, manifest = false, rank = rank, enabled = enabled)

    // ---- selection (E3 asserts exactly these ids)

    @Test fun fixtureAShowsItsFirstFourByRank() {
        val a = listOf(m("qa_five", 4), m("qa_three", 2), m("qa_one", 0), m("qa_four", 3), m("qa_two", 1))
        assertEquals(listOf("qa_one", "qa_two", "qa_three", "qa_four"), QuickSelection.select(a).map { it.id })
    }

    @Test fun manifestBeforeDynamicWhateverTheRanks() {
        val all = listOf(d("dyn0", 0), m("man3", 3), d("dyn1", 1), m("man2", 2))
        assertEquals(listOf("man2", "man3", "dyn0", "dyn1"), QuickSelection.select(all).map { it.id })
    }

    @Test fun equalRanksBreakTiesById() {
        val all = listOf(m("c", 0), m("a", 0), m("e", 0), m("b", 0), m("d", 0))
        assertEquals(listOf("a", "b", "c", "d"), QuickSelection.select(all).map { it.id })
    }

    /** The isEnabled filter: Android deletes a disabled unpinned dynamic shortcut, so only this test can drive it (T11-15). */
    @Test fun disabledShortcutsAreNeverShown() {
        val all = listOf(m("on", 1), m("off", 0, enabled = false), d("dynOff", 0, enabled = false))
        assertEquals(listOf("on"), QuickSelection.select(all).map { it.id })
    }

    // ---- the decision and its lines

    private class FakePlatform(
        val host: Boolean = true,
        val quiet: Boolean = false,
        val list: List<ShortcutCandidate> = emptyList(),
        val boom: Exception? = null,
    ) : ShortcutPlatform<String> {
        var queried = false
        override fun isShortcutHost() = host
        override fun isQuiet(query: String) = quiet
        override fun shortcuts(query: String): List<ShortcutCandidate> {
            queried = true
            boom?.let { throw it }
            return list
        }
        override fun describe(query: String) = query
    }

    private val a = "app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity/0"

    @Test fun showLogsTheExactLineE1Asserts() {
        val lines = ArrayList<String>()
        val p = FakePlatform(list = (0..4).map { m(listOf("qa_one", "qa_two", "qa_three", "qa_four", "qa_five")[it], it) })
        val out = QuickRule.decide(p, a) { lines += it }
        assertTrue(out is QuickDecision.Show)
        assertEquals(listOf("shortcuts for $a: 5 (4 shown: qa_one,qa_two,qa_three,qa_four)"), lines)
    }

    @Test fun emptyIsNoShortcutsWithAZeroLine() {
        val lines = ArrayList<String>()
        val out = QuickRule.decide(FakePlatform(), "app.tileshell/.weather.WeatherActivity/0") { lines += it }
        assertEquals(QuickDecision.None(NoBurstReason.NO_SHORTCUTS), out)
        assertEquals(listOf("shortcuts for app.tileshell/.weather.WeatherActivity/0: 0 (0 shown)"), lines)
    }

    @Test fun notTheHostIsCheckedBeforeTheQuery() {
        val p = FakePlatform(host = false, list = listOf(m("x", 0)))
        assertEquals(QuickDecision.None("not the shortcut host"), QuickRule.decide(p, a) {})
        assertFalse("getShortcuts must not run without the host permission", p.queried)
    }

    @Test fun aQuietProfileIsNeverQueried() {
        val p = FakePlatform(quiet = true, list = listOf(m("x", 0)))
        assertEquals(QuickDecision.None("profile quiet"), QuickRule.decide(p, a) {})
        assertFalse(p.queried)
    }

    /** E14's one JVM-proved line: `no burst … : query failed <exception>` (T11-21). */
    @Test fun aThrowingQueryIsCaughtAndBecomesTheReason() {
        val lines = ArrayList<String>()
        val out = QuickRule.decide(FakePlatform(boom = IllegalStateException("user locked")), a) { lines += it }
        assertEquals(QuickDecision.None("query failed java.lang.IllegalStateException: user locked"), out)
        assertTrue("no shortcuts line when the query never answered", lines.isEmpty())
    }

    /** G-D1: the background load always completes; only cancellation escapes. */
    @Test fun guardTurnsAnyExceptionIntoItsFailureValue() {
        assertEquals("ok", QuickRule.guard({ "ok" }) { "failed $it" })
        assertEquals("failed java.lang.NullPointerException: x", QuickRule.guard({ throw NullPointerException("x") }) { "failed $it" })
        assertEquals("failed", QuickRule.guard({ mapOf<String, String>().getValue("y") }) { "failed" })
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun guardLetsCancellationThrough() {
        QuickRule.guard({ throw kotlinx.coroutines.CancellationException("stop") }) { "swallowed" }
    }

    // ---- geometry (this AVD: 1080 px wide, 3 epx per epx-unit)

    private val s = 164.8f          // one small tile
    private val standOff = 48f      // 16 epx
    private val g = 13.125f         // one gutter
    private val lh = 48f            // the 16-epx caption line
    private val page = QRect(9.75f, 84f, 1080f - 15.75f, 1935f - 13.125f)

    @Test fun cornerArrangementSitsDiagonallyOutsideEachCornerAtTheStandOff() {
        val tile = QRect(365f, 489f, 708f, 832f) // the fixture's middle-column cell, contracted
        val out = QuickGeometry.layout(tile, page, 4, s, standOff, g, lh, inBottomRow = false)
        assertEquals(Arrangement.CORNER, out.arrangement)
        val (tl, tr, bl, br) = out.satellites.map { it.square }
        assertEquals(tile.l - standOff, tl.r, 0.01f); assertEquals(tile.t - standOff, tl.b, 0.01f)
        assertEquals(tile.r + standOff, tr.l, 0.01f); assertEquals(tile.t - standOff, tr.b, 0.01f)
        assertEquals(tile.l - standOff, bl.r, 0.01f); assertEquals(tile.b + standOff, bl.t, 0.01f)
        assertEquals(tile.r + standOff, br.l, 0.01f); assertEquals(tile.b + standOff, br.t, 0.01f)
        out.satellites.forEach { assertEquals(s, it.square.w, 0.01f); assertEquals(s, it.square.h, 0.01f) }
        // Labels outside, on the outer side: above the top pair, below the bottom pair.
        assertEquals(tl.t, out.satellites[0].label.b, 0.01f)
        assertEquals(bl.b, out.satellites[2].label.t, 0.01f)
        assertNoneOnTheTile(out, tile)
    }

    @Test fun fewerThanFourFillTopLeftFirst() {
        val out = QuickGeometry.layout(QRect(365f, 489f, 708f, 832f), page, 1, s, standOff, g, lh, false)
        assertEquals(1, out.satellites.size)
        assertTrue(out.satellites[0].square.r <= 365f && out.satellites[0].square.b <= 489f)
    }

    @Test fun topLeftTileTakesTheLineBelowWithLabelsBelow() {
        val tile = QRect(46f, 169f, 389f, 512f) // the grid's top-left MEDIUM tile, contracted toward the fixed point
        val out = QuickGeometry.layout(tile, page, 4, s, standOff, g, lh, false)
        assertEquals(Arrangement.LINE_BELOW, out.arrangement)
        out.satellites.forEach {
            assertEquals(tile.b + g, it.square.t, 0.01f)
            assertEquals(it.square.b, it.label.t, 0.01f)
            assertInside(it.hit, page)
        }
        assertNoneOnTheTile(out, tile)
    }

    /**
     * The rule as written ("never the held tile"): a full-width WIDE tile mid-grid keeps a CLAMPED corner — its
     * satellites slide in to the margins and sit above and below it, never on it (build finding, INDEX Change Log).
     */
    @Test fun aFullWidthWideTileMidGridKeepsAClampedCorner() {
        val tile = QRect(9.75f, 900f, 1064.25f, 1421f) // WIDE across two columns, show more tiles off
        val out = QuickGeometry.layout(tile, page, 4, s, standOff, g, lh, false)
        assertEquals(Arrangement.CORNER, out.arrangement)
        out.satellites.forEach { assertInside(it.hit, page) }
        assertNoneOnTheTile(out, tile)
    }

    /** In the 2-column grid's first row the top pair cannot clear the status bar without landing on it: a line. */
    @Test fun aFullWidthWideTileInTheFirstRowTakesALine() {
        val tile = QRect(9.75f, 160.4f, 1064.25f, 681.1f) // first row, contracted toward the fixed point
        val out = QuickGeometry.layout(tile, page, 4, s, standOff, g, lh, false)
        assertEquals(Arrangement.LINE_BELOW, out.arrangement)
        out.satellites.forEach { assertEquals(tile.b + g, it.square.t, 0.01f); assertInside(it.hit, page) }
        assertNoneOnTheTile(out, tile)
    }

    @Test fun aBottomRowTileAlwaysTakesTheLineAboveInsideTheMargins() {
        val row = QRect(9.75f, 1935f, 352f, 2182f) // the row's leftmost tile
        val out = QuickGeometry.layout(row, page, 4, s, standOff, g, lh, inBottomRow = true)
        assertEquals(Arrangement.LINE_ABOVE, out.arrangement)
        out.satellites.forEach { assertEquals(row.t - g, it.square.b, 0.01f); assertInside(it.hit, page) }
        // One gutter apart after the clamp.
        out.satellites.zipWithNext { x, y -> assertEquals(g, y.square.l - x.square.r, 0.01f) }
    }

    @Test fun lineLabelsNeverOverlapTheirNeighbours() {
        val out = QuickGeometry.layout(QRect(9.75f, 1935f, 352f, 2182f), page, 4, s, standOff, g, lh, true)
        out.satellites.zipWithNext { x, y -> assertTrue(x.label.r <= y.label.l + 0.01f) }
    }

    private fun assertNoneOnTheTile(out: BurstLayout, tile: QRect) = out.satellites.forEach {
        assertFalse("satellite on the held tile", it.square.intersects(tile))
        assertFalse("label on the held tile", it.label.intersects(tile))
    }

    private fun assertInside(r: QRect, page: QRect) {
        assertTrue("$r inside $page", r.l >= page.l - 0.01f && r.r <= page.r + 0.01f && r.t >= page.t - 0.01f && r.b <= page.b + 0.01f)
    }

    // ---- motion: Compose's own spring(0.65, 1500) against the doc's fingerprint, frame by frame at 60 Hz

    private fun frames(anim: TargetBasedAnimation<Float, *>, stopWhen: (Float) -> Boolean): List<QuickMotion.Sample> {
        val out = ArrayList<QuickMotion.Sample>()
        var i = 0
        while (true) {
            val nanos = (i * 1_000_000_000L) / 60
            val v = anim.getValueFromNanos(nanos)
            out += QuickMotion.Sample(nanos / 1_000_000f, v)
            if (stopWhen(v) || anim.isFinishedFromNanos(nanos)) break
            i++
        }
        return out
    }

    @Test fun openMatchesTheDocsFingerprint() {
        val travel = 426.8f // the fixture MEDIUM tile's diagonal travel (T11-29)
        val anim = TargetBasedAnimation(spring(QuickMotion.DAMPING, QuickMotion.STIFFNESS, visibilityThreshold = 0.25f / travel), Float.VectorConverter, 0f, 1f)
        val st = QuickMotion.openStats(frames(anim) { false }, travel)
        assertEquals("peak 107 ± 17 ms", 107f, st.peakMs, 17f)
        assertEquals("overshoot 6.8 ± 2 %", 6.8f, st.overshootPct, 2f)
        assertTrue("settle ≤ 267 ms, got ${st.settleMs}", st.settleMs <= 267f)
        assertTrue("settle is not the first zero crossing (≈77 ms), got ${st.settleMs}", st.settleMs > 184f)
        assertEquals(16.7f, st.maxGapMs, 0.1f)
    }

    @Test fun closeIsGoneAtTheHalfway() {
        val anim = TargetBasedAnimation(spring(QuickMotion.DAMPING, QuickMotion.STIFFNESS), Float.VectorConverter, 0f, 1f)
        val samples = frames(anim) { QuickMotion.closeAlpha(it) <= 0f }
        assertEquals("alpha0 36 ± 17 ms", 36f, QuickMotion.closeAlpha0(samples), 17f)
    }

    @Test fun alphaAndScaleRideTheFirstHalf() {
        assertEquals(0f, QuickMotion.openAlpha(0f), 0f)
        assertEquals(1f, QuickMotion.openAlpha(0.5f), 0f)
        assertEquals(1f, QuickMotion.openAlpha(1.07f), 0f)
        assertEquals(0.5f, QuickMotion.openScale(0f), 0f)
        assertEquals(1f, QuickMotion.openScale(0.6f), 0f)
    }

    // ---- the launch's failure path (T11-24)

    @Test fun theThreeStartFailuresBecomeFailed() {
        listOf<Exception>(
            android.content.ActivityNotFoundException("Shortcut could not be started"),
            IllegalStateException("disabled"),
            SecurityException("not allowed"),
        ).forEach { e ->
            val out = QuickLaunch.run { throw e }
            assertTrue("$e", out is QuickLaunchOutcome.Failed && out.error === e)
        }
        assertEquals(QuickLaunchOutcome.Ok, QuickLaunch.run { })
    }

    @Test(expected = UnsupportedOperationException::class)
    fun anythingElseIsNotSwallowed() {
        QuickLaunch.run { throw UnsupportedOperationException() }
    }
}
