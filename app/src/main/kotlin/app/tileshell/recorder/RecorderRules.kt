package app.tileshell.recorder

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Voice Recorder's rules, free of the platform so the host JVM proves them (phase 15 build task 7).
 * The files that talk to MediaStore, MediaCodec and Compose read these and decide nothing themselves.
 */

/**
 * One row of the list: a MediaStore audio row marked `IS_RECORDING`, whichever app made it (Q2 A).
 *
 * [ownerPackage] is MediaStore's `OWNER_PACKAGE_NAME` — null for a file whose owner Android forgot (an
 * uninstall or a Clear storage orphans the shell's own takes, T15-26). [recordedAtMs] is when the take was
 * made where the shell knows it (its own recordings.json), else when MediaStore added the file.
 */
data class Recording(
    val id: Long,
    val displayName: String,
    val durationMs: Long,
    val recordedAtMs: Long,
    val ownerPackage: String?,
    val relativePath: String?,
    val sizeBytes: Long,
) {
    /** The name as the list shows it: the display name without `.m4a`. */
    val name: String get() = RecordingNames.stem(displayName)

    fun isOwn(shellPackage: String): Boolean = ownerPackage == shellPackage
}

/**
 * New recordings' names: "Recording", then "Recording (2)", "Recording (3)" … (r11/voice-recorder.md U2 —
 * 10166 named its first take "Tallenne" = "Recording"; the numbering is Windows' own duplicate form, an
 * approximation, H12). The smallest free name wins, so deleting "Recording" frees it again.
 */
object RecordingNames {
    const val BASE = "Recording"
    const val EXTENSION = ".m4a"

    /** The next free name given every display name already in Recordings/ (with or without `.m4a`, any case). */
    fun next(existing: Collection<String>): String {
        val taken = existing.mapTo(HashSet()) { stem(it).lowercase(Locale.ROOT) }
        if (BASE.lowercase(Locale.ROOT) !in taken) return BASE
        var n = 2
        while ("$BASE ($n)".lowercase(Locale.ROOT) in taken) n++
        return "$BASE ($n)"
    }

    fun displayName(name: String): String = name + EXTENSION

    /** [displayName] without a trailing `.m4a`, whatever its case. */
    fun stem(displayName: String): String =
        if (displayName.length > EXTENSION.length && displayName.endsWith(EXTENSION, ignoreCase = true)) {
            displayName.dropLast(EXTENSION.length)
        } else {
            displayName
        }
}

/**
 * What a row may do (T15-3, T15-26): every recording plays and shares; rename, delete, trim and markers only
 * where `OWNER_PACKAGE_NAME` is the shell's own package. An orphan (owner null) is another app's by this rule,
 * so no Android consent dialog is ever raised for a file the shell does not own.
 */
data class RecordingCaps(
    val share: Boolean,
    val rename: Boolean,
    val delete: Boolean,
    val trim: Boolean,
    val markers: Boolean,
) {
    companion object {
        fun of(ownerPackage: String?, shellPackage: String): RecordingCaps {
            val own = ownerPackage != null && ownerPackage == shellPackage
            return RecordingCaps(share = true, rename = own, delete = own, trim = own, markers = own)
        }
    }
}

/**
 * The "Showing …" filter's kinds (r11/voice-recorder.md 3.2; the choices are U6's approximation, adapted:
 * W10M's kinds were voice and call recordings, the shell's are whose they are).
 */
enum class ShowingKind(val id: String, val label: String) {
    ALL("all", "All recordings"),
    MINE("mine", "My recordings"),
    OTHERS("others", "Other apps' recordings");

    companion object {
        fun byId(id: String?): ShowingKind = entries.firstOrNull { it.id == id } ?: ALL
    }
}

/** The list's search box and "Showing" filter together (T15-16, E30). */
object RecordingFilter {
    fun apply(items: List<Recording>, kind: ShowingKind, query: String, shellPackage: String): List<Recording> {
        val q = query.trim()
        return items.filter { r ->
            val kindOk = when (kind) {
                ShowingKind.ALL -> true
                ShowingKind.MINE -> r.isOwn(shellPackage)
                ShowingKind.OTHERS -> !r.isOwn(shellPackage)
            }
            kindOk && (q.isEmpty() || r.name.contains(q, ignoreCase = true))
        }
    }

    /** How many of [items] another app made (orphans included) — the `[recorder] list` line's m. */
    fun othersCount(items: List<Recording>, shellPackage: String): Int = items.count { !it.isOwn(shellPackage) }
}

/**
 * The list's date groups: "Today", "Yesterday", "This week", "Last week", then month names (r11 3.3 saw
 * Today / This week / Last week; the rest is U7's approximation). Newest first, rows newest first inside a
 * group. Weeks start on the locale's first day.
 */
object RecordingGroups {
    fun label(recordedAtMs: Long, nowMs: Long, zone: ZoneId, locale: Locale): String {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(recordedAtMs).atZone(zone).toLocalDate()
        if (!day.isBefore(today)) return "Today"
        if (day == today.minusDays(1)) return "Yesterday"
        val firstDay: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(firstDay))
        if (!day.isBefore(thisWeek)) return "This week"
        if (!day.isBefore(thisWeek.minusWeeks(1))) return "Last week"
        val month = day.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        return if (day.year == today.year) month else "$month ${day.year}"
    }

    fun group(items: List<Recording>, nowMs: Long, zone: ZoneId, locale: Locale): List<Pair<String, List<Recording>>> {
        val sorted = items.sortedWith(compareByDescending<Recording> { it.recordedAtMs }.thenByDescending { it.id })
        val out = ArrayList<Pair<String, MutableList<Recording>>>()
        for (r in sorted) {
            val label = label(r.recordedAtMs, nowMs, zone, locale)
            if (out.isEmpty() || out.last().first != label) out += label to mutableListOf(r) else out.last().second += r
        }
        return out
    }

    /** Midnight at the start of [nowMs]'s day, for callers that group again when the day turns. */
    fun startOfDay(nowMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}

/** The texts the pages show numbers in (r11/voice-recorder.md "Formats"). */
object RecorderFormat {
    /** Row and scrubber durations: m:ss, and h:mm:ss from one hour ("0:13", "4:24", "1:07:03"; 3.7). */
    fun duration(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) + 500L) / 1000L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
    }

    /** The scrubber's elapsed label: like [duration] but never rounded up past what has played. */
    fun position(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
    }

    /**
     * The record state's timer: always hh:mm:ss, eight characters, with its LEADING ZERO FIELDS dimmed (2.8:
     * "00:00:" dim and "03" live). Returned as the dim prefix and the live rest; seconds are never dim.
     */
    fun timer(ms: Long): Pair<String, String> {
        val total = ms.coerceAtLeast(0L) / 1000L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val text = "${two(h)}:${two(m)}:${two(s)}"
        val dimChars = when {
            h > 0 -> 0
            m > 0 -> 3
            else -> 6
        }
        return text.take(dimChars) to text.drop(dimChars)
    }

    /** A marker while recording: "00:07" (Y, 10166). */
    fun recordingMarker(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000L
        return if (total >= 3600) "${total / 3600}:${two((total % 3600) / 60)}:${two(total % 60)}" else "${two(total / 60)}:${two(total % 60)}"
    }

    /** A marker on playback: "1:08" (D1). */
    fun playbackMarker(ms: Long): String = position(ms)

    /** Whole mebibytes, for `[recorder] start <name> free=<MB>`. */
    fun mebibytes(bytes: Long): Long = bytes.coerceAtLeast(0L) / (1024L * 1024L)

    private fun two(v: Long): String = v.toString().padStart(2, '0')
}

/**
 * The storage floor (T15-27): `StorageManager.getAllocatableBytes`, which already leaves out Android's own
 * low-storage reserve, is checked before a take and every [CHECK_EVERY_MS] during it; at 50 MB or less a take
 * is refused, or stopped and saved with "Not enough space" (approximation, H12).
 */
object StorageFloor {
    const val FLOOR_BYTES = 50L * 1024L * 1024L
    const val CHECK_EVERY_MS = 5_000L
    const val NOTICE = "Not enough space"

    fun canStart(allocatableBytes: Long): Boolean = allocatableBytes > FLOOR_BYTES

    fun mustStop(allocatableBytes: Long): Boolean = allocatableBytes <= FLOOR_BYTES
}

/**
 * Rename (U4): the new name becomes `_display_name` (`<name>.m4a`). A name MediaStore would mangle or hide is
 * refused here with a notice rather than silently changed (edge case: "rename with characters MediaStore
 * refuses"): the FAT-invalid characters MediaProvider replaces, control characters, a leading dot (a hidden
 * file the scanner skips) and a name too long for one path segment. A clash with an existing name is NOT
 * refused — MediaProvider appends " (1)" itself.
 */
object RenameRule {
    sealed interface Result {
        data class Ok(val name: String) : Result
        data class Refused(val notice: String) : Result
    }

    const val FORBIDDEN = "\"*/:<>?\\|"

    /** 255 bytes per path segment, less `.m4a`. */
    private const val MAX_BYTES = 255 - 4

    fun check(input: String): Result {
        val name = input.trim()
        if (name.isEmpty()) return Result.Refused("Type a name for the recording.")
        if (name.any { it < ' ' || it == '\u007F' || it in FORBIDDEN }) {
            return Result.Refused("A name can't contain any of these: \" * / : < > ? \\ |")
        }
        if (name.startsWith(".")) return Result.Refused("A name can't start with a dot.")
        if (name.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return Result.Refused("That name is too long.")
        return Result.Ok(name)
    }
}

/**
 * Trim (U3): keep [startMs, endMs) of the recording as a NEW file; the original stays until Save. A cut shorter
 * than [MIN_MS] is refused (edge case: "trim to zero length (refused)"); keeping the whole recording is no cut.
 */
object TrimRule {
    const val MIN_MS = 100L

    sealed interface Result {
        data class Cut(val startMs: Long, val endMs: Long) : Result
        data object Whole : Result
        data class Refused(val notice: String) : Result
    }

    fun check(startMs: Long, endMs: Long, durationMs: Long): Result {
        val start = startMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        val end = endMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        if (end - start < MIN_MS) return Result.Refused("Choose a longer part of the recording to keep.")
        if (start == 0L && end >= durationMs) return Result.Whole
        return Result.Cut(start, end)
    }

    /** Markers inside the kept part, moved to the new file's clock; the ones cut away go with the cut. */
    fun shiftMarkers(markers: List<Long>, startMs: Long, endMs: Long): List<Long> =
        markers.filter { it in startMs until endMs }.map { it - startMs }
}

/**
 * The take's own clock (T15-16): time counts only while samples reach the encoder, so a pause — the user's, a
 * call's or Android silencing the capture — is not in the take, and a marker's time is take time with every
 * pause left out (E30: flagged 1 s after resuming a take paused for 4 s after 3 s reads 4 s).
 */
class TakeClock(private val sampleRate: Int = RecorderAudio.SAMPLE_RATE) {

    enum class Pause(val word: String) { USER("user"), CALL("call"), SILENCED("silenced") }

    /** Why the take is paused, or null while it records. The first reason stands until it resumes. */
    var paused: Pause? = null
        private set

    /** PCM samples (per channel) that have gone into the take. */
    var samples: Long = 0
        private set

    private val markerList = ArrayList<Long>()

    val markers: List<Long> get() = markerList.toList()

    val elapsedMs: Long get() = samples * 1000L / sampleRate

    /** Counts [n] samples into the take; while paused none count. @return whether they did. */
    fun onSamples(n: Int): Boolean {
        if (paused != null || n <= 0) return false
        samples += n
        return true
    }

    /** @return true when this paused the take (and a `paused:` line is due), false when it already was. */
    fun pause(reason: Pause): Boolean {
        if (paused != null) return false
        paused = reason
        return true
    }

    /**
     * Resume. Refused while a call is on ([inCall]): the call has the microphone, and the take resumes on the
     * user's Resume after it (approximation, H12). @return true when the take records again.
     */
    fun resume(inCall: Boolean): Boolean {
        if (paused == null || inCall) return false
        paused = null
        return true
    }

    /** A marker at the take's time now (pause excluded). */
    fun flag(): Long {
        val at = elapsedMs
        markerList += at
        return at
    }

    /** Markers recovered from a take's sidecar after a death. */
    fun restoreMarkers(list: List<Long>) {
        markerList.clear()
        markerList += list
    }
}
