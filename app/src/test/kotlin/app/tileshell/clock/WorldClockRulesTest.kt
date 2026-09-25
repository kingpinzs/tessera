package app.tileshell.clock

import app.tileshell.clock.WorldClockRules.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class WorldClockRulesTest {
    private val us = Locale.US
    private val denver = ZoneId.of("America/Denver")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val london = ZoneId.of("Europe/London")
    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun ms(z: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, z).toInstant().toEpochMilli()

    @Test fun exemplarNameFallsBackToTheIdsLastSegment() {
        assertEquals("Tokyo", WorldClockRules.name(Zone("Asia/Tokyo", "Tokyo")))
        assertEquals("GMT+5", WorldClockRules.name(Zone("Etc/GMT+5", null)))
        assertEquals("GMT+5", WorldClockRules.name(Zone("Etc/GMT+5", "  ")))
        assertEquals("New York", WorldClockRules.name(Zone("America/New_York", null)))
        assertEquals("Indianapolis", WorldClockRules.name(Zone("America/Indiana/Indianapolis", null)))
        assertEquals("UTC", WorldClockRules.name(Zone("UTC", null)))
        assertEquals("", WorldClockRules.region("UTC"))
        assertEquals("America", WorldClockRules.region("America/Indiana/Indianapolis"))
    }

    @Test fun twoZonesWithOneNameShowTheirRegion() {
        val entries = WorldClockRules.entries(
            listOf(Zone("Europe/Paris", "Paris"), Zone("America/Paris", "Paris"), Zone("Asia/Tokyo", "Tokyo"), Zone("Etc/GMT+5", null)),
        )
        assertEquals(listOf("GMT+5", "Paris, America", "Paris, Europe", "Tokyo"), entries.map { it.label })
        assertEquals("Paris", entries[1].name)
        assertEquals("America", entries[1].region)
    }

    @Test fun searchMatchesTheLabelStartsFirst() {
        val entries = WorldClockRules.entries(listOf(Zone("Asia/Tokyo", "Tokyo"), Zone("Europe/Stockholm", "Stockholm"), Zone("America/Toronto", "Toronto")))
        // Names that START with the query lead; "Stockholm" merely contains "to" and follows.
        assertEquals(listOf("Tokyo", "Toronto", "Stockholm"), WorldClockRules.search(entries, "to").map { it.label })
        assertEquals(listOf("Tokyo", "Toronto"), WorldClockRules.search(entries, "to", limit = 2).map { it.label })
        // Nothing starts with "o": every match is a "contains", in the list's own (sorted) order.
        assertEquals(listOf("Stockholm", "Tokyo", "Toronto"), WorldClockRules.search(entries, "o").map { it.label })
        assertEquals(listOf("Stockholm"), WorldClockRules.search(entries, "STOCK").map { it.label })
        assertTrue(WorldClockRules.search(entries, "  ").isEmpty())
        assertTrue(WorldClockRules.search(entries, "xyz").isEmpty())
    }

    @Test fun differenceWordingIsW10Ms() {
        // 2026-09-23 10:00 in Denver (MDT, UTC-6): Tokyo is UTC+9 → 15 hours ahead, and already the 24th → "Thursday".
        val now = ms(denver, 2026, 9, 23, 10, 0)
        assertEquals("Thursday, 15 hours ahead", WorldClockRules.difference(tokyo, denver, now, us))
        assertEquals("Today, 7 hours ahead", WorldClockRules.difference(london, denver, now, us)) // BST, UTC+1
        assertEquals("Today, 7 hours behind", WorldClockRules.difference(denver, london, now, us))
        assertEquals("Today, 11 hours 30 minutes ahead", WorldClockRules.difference(kolkata, denver, now, us))
        assertEquals("Today, same time", WorldClockRules.difference(denver, denver, now, us))
        assertEquals("1 hour ahead", WorldClockRules.offsetWords(60))
        assertEquals("1 hour behind", WorldClockRules.offsetWords(-60))
        assertEquals("30 minutes ahead", WorldClockRules.offsetWords(30))
    }

    @Test fun differenceFollowsDstOnBothSides() {
        // 2026-11-02 10:00 Denver: MST (UTC-7) and London back on GMT: 7 hours again.
        assertEquals("Today, 7 hours ahead", WorldClockRules.difference(london, denver, ms(denver, 2026, 11, 2, 10, 0), us))
        // 2026-03-15 10:00: Denver on MDT (since the 8th), London still GMT (until the 29th): 6 hours.
        assertEquals("Today, 6 hours ahead", WorldClockRules.difference(london, denver, ms(denver, 2026, 3, 15, 10, 0), us))
        // Tokyo late evening in Denver: Tokyo is tomorrow there, on a Wednesday.
        assertEquals("Wednesday, 15 hours ahead", WorldClockRules.difference(tokyo, denver, ms(denver, 2026, 9, 22, 23, 0), us))
        // And the other way: Denver from Tokyo's morning is still yesterday, Tuesday.
        assertEquals("Tuesday, 15 hours behind", WorldClockRules.difference(denver, tokyo, ms(tokyo, 2026, 9, 23, 9, 0), us))
    }

    @Test fun compareModeStepsWholeHours() {
        val now = ms(denver, 2026, 9, 23, 10, 30)
        assertEquals(now + 3_600_000L, WorldClockRules.compareInstant(now, 1))
        assertEquals(listOf("08", "09", "10", "11", "12"), WorldClockRules.stripHours(now, denver))
        assertEquals(listOf("22", "23", "00", "01", "02"), WorldClockRules.stripHours(ms(denver, 2026, 9, 23, 0, 0), denver))
        assertEquals("Wednesday, September 23, 2026", WorldClockRules.fullDate(now, denver, us))
        assertEquals("Thursday, September 24, 2026", WorldClockRules.fullDate(now, tokyo, us))
    }
}
