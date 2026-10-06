package app.tileshell.files

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 18's two debug-only QA paces as rules (Q-18-3 the copy's bytes a second, Q-18-5 the search walk's entries a
 * second), in the form of `CatalogueRules.base`: the site hands the rule `BuildConfig.DEBUG` and the pref
 * (`video/TrustWiringScanTest` holds the sites). A release build is never paced, whatever the pref holds.
 */
class FilePaceRulesTest {
    private val rules: List<Pair<String, (Boolean, String?) -> Long?>> = listOf("rate" to FilePace::rate, "searchRate" to FilePace::searchRate)

    @Test
    fun `a release build has no pace even when the pref is set`() {
        for ((name, rule) in rules) {
            for (pref in listOf("8388608", "1", " 50 ", "9223372036854775807", null, "", "x")) assertEquals("$name(false, $pref)", null, rule(false, pref))
        }
        // And so no FilePace is made from it.
        assertEquals(null, FilePace.of(FilePace.rate(false, "8388608")))
    }

    @Test
    fun `a debug build is paced by the pref's number`() {
        for ((name, rule) in rules) {
            assertEquals(name, 8_388_608L, rule(true, "8388608"))
            assertEquals(name, 50L, rule(true, " 50\n"))
            assertEquals(name, 1L, rule(true, "1"))
        }
    }

    @Test
    fun `a debug build with no pref, or one that is not a number above 0, is unpaced`() {
        for ((name, rule) in rules) {
            for (pref in listOf(null, "", " ", "0", "-1", "fast", "1.5", "1e6", "99999999999999999999")) assertEquals("$name(true, $pref)", null, rule(true, pref))
        }
    }
}
