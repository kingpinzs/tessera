package app.tileshell.photos

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * One row of Photos' library index (phase 17, Q4 A: images and videos together), free of Android types so the grouping
 * below is unit-tested. [id] is the MediaStore row id (the same id in the files, images and video tables); [dateMs] is
 * what the collection sorts and groups by — DATE_TAKEN where the file carries one, else the file's modified time;
 * [dateModifiedS] is MediaStore's DATE_MODIFIED itself (seconds), which with [id] and [size] names one state of the file
 * (what the Living Images check is remembered by — [LivingRules.key]).
 */
data class MediaEntry(
    val id: Long,
    val video: Boolean,
    val dateMs: Long,
    val bucketId: Long,
    val bucket: String,
    val mime: String,
    val name: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val relativePath: String,
    val durationMs: Long,
    val dateTakenMs: Long?,
    val dateModifiedS: Long = 0L,
)

/** A row of the collection list (Y1): a month header, a day row with its count, or up to three tiles. */
sealed interface CollectionRow {
    val key: String

    /** [first] is true for the list's first month (its header sits right under the pivot titles). */
    data class Month(val month: YearMonth, val first: Boolean) : CollectionRow {
        override val key get() = "m:$month"
    }

    /** [afterTiles] is true when tiles of an earlier day of the same month sit above it. */
    data class Day(val day: LocalDate, val count: Int, val afterTiles: Boolean) : CollectionRow {
        override val key get() = "d:$day"
    }

    data class Tiles(val items: List<MediaEntry>) : CollectionRow {
        override val key get() = "t:${items.first().id}"
    }
}

/** An album (Y1): one MediaStore bucket, its newest item as the tile's picture. */
data class Album(val bucketId: Long, val name: String, val count: Int, val cover: MediaEntry)

/** The collection's and the albums' grouping rules. */
object PhotoGroups {
    const val COLUMNS = 3

    /**
     * The time a row sorts and groups by: DATE_TAKEN where the file carries one, else the file's modified time
     * (MediaStore's DATE_MODIFIED, in seconds) — a PNG has no EXIF date, and a screenshot's order is its file's.
     */
    fun dateMs(dateTakenMs: Long?, dateModifiedS: Long): Long = dateTakenMs?.takeIf { it > 0 } ?: (dateModifiedS * 1000L)

    /** Newest first; rows of one time keep a fixed order (the higher id first), so "next" is defined. */
    fun sorted(items: List<MediaEntry>): List<MediaEntry> =
        items.sortedWith(compareByDescending<MediaEntry> { it.dateMs }.thenByDescending { it.id })

    /** [sortedItems] (newest first) as months, days inside each month, and rows of [COLUMNS] tiles inside each day. */
    fun collection(sortedItems: List<MediaEntry>, zone: ZoneId): List<CollectionRow> {
        val rows = mutableListOf<CollectionRow>()
        var month: YearMonth? = null
        var i = 0
        while (i < sortedItems.size) {
            val day = dayOf(sortedItems[i], zone)
            var j = i
            while (j < sortedItems.size && dayOf(sortedItems[j], zone) == day) j++
            val ym = YearMonth.from(day)
            val newMonth = ym != month
            if (newMonth) {
                rows += CollectionRow.Month(ym, first = month == null)
                month = ym
            }
            rows += CollectionRow.Day(day, j - i, afterTiles = !newMonth)
            sortedItems.subList(i, j).chunked(COLUMNS).forEach { rows += CollectionRow.Tiles(it) }
            i = j
        }
        return rows
    }

    /** The buckets of [sortedItems], the one holding the newest item first; each album's cover is its newest item. */
    fun albums(sortedItems: List<MediaEntry>): List<Album> {
        val byBucket = LinkedHashMap<Long, MutableList<MediaEntry>>()
        sortedItems.forEach { byBucket.getOrPut(it.bucketId) { mutableListOf() } += it }
        return byBucket.map { (id, list) -> Album(id, list.first().bucket, list.size, list.first()) }
    }

    fun dayOf(item: MediaEntry, zone: ZoneId): LocalDate = Instant.ofEpochMilli(item.dateMs).atZone(zone).toLocalDate()

    /** "1 item" / "N items": W10M's V-2016+ day row counts photos and videos together (r11/photos.md 1.3.6). */
    fun countText(n: Int): String = if (n == 1) "1 item" else "$n items"
}
