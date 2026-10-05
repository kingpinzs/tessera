package app.tileshell.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class PhotoGroupsTest {
    private val zone = ZoneId.of("America/Denver")

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun entry(id: Long, dateMs: Long, bucket: String = "Camera", video: Boolean = false) = MediaEntry(
        id = id, video = video, dateMs = dateMs, bucketId = bucket.hashCode().toLong(), bucket = bucket,
        mime = if (video) "video/mp4" else "image/png", name = "f$id", width = 640, height = 480, size = 1, relativePath = "DCIM/$bucket/",
        durationMs = 0, dateTakenMs = null,
    )

    @Test
    fun `newest first and the higher id first inside one time`() {
        val items = listOf(entry(1, at(2026, 10, 5, 12, 0)), entry(2, at(2026, 10, 5, 12, 3)), entry(3, at(2026, 10, 5, 12, 0)))
        assertEquals(listOf(2L, 3L, 1L), PhotoGroups.sorted(items).map { it.id })
    }

    @Test
    fun `six pictures a minute apart sit under one month and one day in rows of three`() {
        // media_up's order: qa-photo-i is i minutes older than qa-photo-0.
        val items = PhotoGroups.sorted((0L..5L).map { entry(100 + it, at(2026, 10, 5, 12, 30) - it * 60_000) })
        val rows = PhotoGroups.collection(items, zone)
        assertEquals(CollectionRow.Month(YearMonth.of(2026, 10), first = true), rows[0])
        assertEquals(CollectionRow.Day(LocalDate.of(2026, 10, 5), 6, afterTiles = false), rows[1])
        assertEquals(listOf(listOf(100L, 101L, 102L), listOf(103L, 104L, 105L)), rows.drop(2).map { (it as CollectionRow.Tiles).items.map { e -> e.id } })
    }

    @Test
    fun `a month header per month and a day row per day`() {
        val items = PhotoGroups.sorted(listOf(
            entry(1, at(2026, 10, 5)), entry(2, at(2026, 10, 5)), entry(3, at(2026, 10, 5)), entry(4, at(2026, 10, 5)),
            entry(5, at(2026, 10, 2)), entry(6, at(2026, 9, 30, 23, 59)), entry(7, at(2025, 10, 5)),
        ))
        val rows = PhotoGroups.collection(items, zone)
        assertEquals(
            listOf("m:2026-10", "d:2026-10-05", "t:4", "t:1", "d:2026-10-02", "t:5", "m:2026-09", "d:2026-09-30", "t:6", "m:2025-10", "d:2025-10-05", "t:7"),
            rows.map { it.key },
        )
        assertEquals(4, (rows[1] as CollectionRow.Day).count)
        assertTrue((rows[0] as CollectionRow.Month).first)
        assertTrue(!(rows[6] as CollectionRow.Month).first)
        assertTrue(!(rows[1] as CollectionRow.Day).afterTiles)
        assertTrue((rows[4] as CollectionRow.Day).afterTiles)
        assertTrue(!(rows[7] as CollectionRow.Day).afterTiles)
        // Every key is unique: the list's item keys.
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun `the day is the day in the phone's zone`() {
        // 2026-10-01 05:30 UTC is still 30 September in Denver.
        val item = entry(1, ZonedDateTime.of(2026, 10, 1, 5, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli())
        assertEquals(LocalDate.of(2026, 9, 30), PhotoGroups.dayOf(item, zone))
    }

    @Test
    fun `albums are buckets with counts and the newest item as the cover`() {
        val items = PhotoGroups.sorted(listOf(
            entry(1, at(2026, 10, 5, 12, 0), "Camera"), entry(2, at(2026, 10, 5, 12, 1), "QA-Album"),
            entry(3, at(2026, 10, 5, 12, 2), "Camera"), entry(4, at(2026, 10, 4), "QA-Album", video = true),
        ))
        val albums = PhotoGroups.albums(items)
        assertEquals(listOf("Camera" to 2, "QA-Album" to 2), albums.map { it.name to it.count })
        assertEquals(listOf(3L, 2L), albums.map { it.cover.id })
    }

    @Test
    fun `a row with no date taken is ordered by its file's modified time`() {
        assertEquals(1_791_220_000_000L, PhotoGroups.dateMs(null, 1_791_220_000L))
        assertEquals(1_791_220_000_000L, PhotoGroups.dateMs(0L, 1_791_220_000L))
        assertEquals(1_600_000_000_123L, PhotoGroups.dateMs(1_600_000_000_123L, 1_791_220_000L))
        // Six PNGs (no EXIF date) whose files are a minute apart, the first the newest: that is their order.
        val base = 1_791_220_000L
        val pngs = (0L..5L).map { entry(50 - it, PhotoGroups.dateMs(null, base - it * 60)) }
        assertEquals(listOf(50L, 49L, 48L, 47L, 46L, 45L), PhotoGroups.sorted(pngs.shuffled(java.util.Random(7))).map { it.id })
    }

    @Test
    fun `nothing in gives nothing out`() {
        assertEquals(emptyList<CollectionRow>(), PhotoGroups.collection(emptyList(), zone))
        assertEquals(emptyList<Album>(), PhotoGroups.albums(emptyList()))
    }

    @Test
    fun `the count reads items`() {
        assertEquals("1 item", PhotoGroups.countText(1))
        assertEquals("29 items", PhotoGroups.countText(29))
    }
}
