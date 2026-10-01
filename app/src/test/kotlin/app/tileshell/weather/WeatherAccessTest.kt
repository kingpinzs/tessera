package app.tileshell.weather

import app.tileshell.weather.WeatherFeed.Problem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 14 gate review: the pod bay's "Location is off" line asks the feed to look again on every Start resume, and the
 * feed must refresh only when access has actually come back — with Location still off it fetched with the last place's
 * coordinates, one request per Home press.
 */
class WeatherAccessTest {

    @Test
    fun `Location still off is not a reason to refresh`() {
        assertFalse(WeatherFeed.accessRestored(listOf(Problem.LOCATION_OFF), hasPermission = true, locationOn = false))
    }

    @Test
    fun `the permission still missing is not a reason to refresh`() {
        assertFalse(WeatherFeed.accessRestored(listOf(Problem.NO_PERMISSION), hasPermission = false, locationOn = true))
        assertFalse(WeatherFeed.accessRestored(listOf(Problem.NO_PERMISSION), hasPermission = false, locationOn = false))
    }

    @Test
    fun `Location switched back on is`() {
        assertTrue(WeatherFeed.accessRestored(listOf(Problem.LOCATION_OFF), hasPermission = true, locationOn = true))
    }

    @Test
    fun `the permission granted is, once`() {
        // Granted with Location off: one refresh, which turns the problem into LOCATION_OFF — and that one waits.
        assertTrue(WeatherFeed.accessRestored(listOf(Problem.NO_PERMISSION), hasPermission = true, locationOn = false))
        assertFalse(WeatherFeed.accessRestored(listOf(Problem.LOCATION_OFF), hasPermission = true, locationOn = false))
    }

    @Test
    fun `problems the access line does not stand for never refresh from it`() {
        assertFalse(WeatherFeed.accessRestored(listOf(Problem.NO_NETWORK, Problem.PROVIDER_ERROR, Problem.NO_LOCATION), true, true))
        assertFalse(WeatherFeed.accessRestored(emptyList(), true, true))
    }
}
