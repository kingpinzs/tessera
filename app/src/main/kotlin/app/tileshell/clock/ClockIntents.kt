package app.tileshell.clock

import app.tileshell.files.FilesIntents
import app.tileshell.media.UriAccessPort
import app.tileshell.media.UriAccessRules
import app.tileshell.tiles.api.LiveTileProtocol
import app.tileshell.tiles.engine.TileRouting
import java.time.DayOfWeek

/**
 * An intent's extras as the platform holds them (ledger L18-3). [value] is ONE raw read and MAY THROW — another app's
 * bundle can hold a class only that app has, and unpacking it throws in the reader's process. A bundle inside the
 * extras comes back as another port. The real port is `AndroidExtras`; the tests' is a map, or one that throws.
 */
fun interface ExtrasPort {
    fun value(key: String): Any?
}

/**
 * The ONE reader of another app's extras in the clock's two exported activities (ledger L18-3; the adversarial
 * review's N1 and N4). It cannot throw: whatever the port throws, and whatever type or size the value has, the answer
 * is the value in the type asked for or null — and a value that was there but could not be taken is noted in
 * [refused] as one line that carries the key (as a token) and a fixed reason, never the value.
 */
class SafeExtras(private val port: ExtrasPort?, val refused: MutableList<String> = mutableListOf()) {
    fun string(key: String, max: Int): String? = read(key) { v ->
        when {
            v !is String -> no(key, "not text")
            v.length > max -> no(key, "too long")
            else -> v
        }
    }

    fun int(key: String): Int? = read(key) { v -> v as? Int ?: no(key, "not a whole number") }

    fun long(key: String): Long? = read(key) { v -> v as? Long ?: no(key, "not a long number") }

    fun boolean(key: String): Boolean? = read(key) { v -> v as? Boolean ?: no(key, "not a yes or no") }

    /** An `int[]` or a list whose every element is an `Int`, of at most [max] numbers. */
    fun ints(key: String, max: Int): List<Int>? = read(key) { v ->
        val list: List<*> = when (v) {
            is IntArray -> v.asList()
            is List<*> -> v
            else -> return@read no(key, "not a list of whole numbers")
        }
        when {
            list.size > max -> no(key, "too long")
            list.any { it !is Int } -> no(key, "not a list of whole numbers")
            else -> list.map { it as Int }
        }
    }

    /** A bundle inside the extras, read by the same rule into the same [refused] list. */
    fun nested(key: String): SafeExtras? = read(key) { v -> if (v is ExtrasPort) SafeExtras(v, refused) else no(key, "not a bundle") }

    private fun <T> read(key: String, take: (Any) -> T?): T? = try {
        port?.value(key)?.let(take)
    } catch (e: Throwable) {
        no(key, "threw ${UriAccessRules.className(e.javaClass.simpleName.orEmpty())}")
    }

    private fun no(key: String, why: String): Nothing? {
        refused += "extra ${ClockIntents.token(key)} refused ($why)"
        return null
    }
}

/**
 * What an intent makes Alarms & Clock do: the tab to show, the timer to bring forward, an editor to open filled in —
 * each null when the intent does not ask for it or may not — and the diagnostics lines to write.
 */
data class ClockOpen(
    val tab: ClockTab? = null,
    val focusedTimer: String? = null,
    val alarm: AlarmDraft? = null,
    val timer: TimerDraft? = null,
    val lines: List<String> = emptyList(),
)

/**
 * What ClockActivity's intent may do (ledger L18-3; a trust rule). The activity is exported with no permission, in the
 * launcher's own process, so ANY app can start it with any extras:
 *
 *  - `page` (a tab's id) and a secondary tile's id only choose which tab shows; anything that is not exactly one of
 *    the known forms is ignored. Any app may send them; they change nothing that is stored.
 *  - `api_edit`, the bundle [AlarmApiActivity] hands over for a request without SKIP_UI, opens an editor FILLED IN. It
 *    is honoured only when the uid that sent this very intent is the shell's own — for anyone else it is not even
 *    read — and, whoever sent it, each field must pass the checks the API handler applies ([AlarmApiRules]: hour
 *    0..23, minute 0..59, days 1..7, the message's length and control characters, a timer length within the ceiling).
 *    A bundle with one bad field is ignored whole. Nothing is stored until the user saves.
 *
 * Every extra is read through [SafeExtras], so no intent can throw here. Pure: `ClockIntentsTest`.
 */
object ClockIntents {
    const val EXTRA_PAGE = "page"
    const val EXTRA_API_EDIT = "api_edit"
    const val API_KIND = "kind"
    const val API_HOUR = "hour"
    const val API_MINUTE = "minute"
    const val API_DAYS = "days"
    const val API_MESSAGE = "message"
    const val API_SOUND_KIND = "sound_kind"
    const val API_SOUND_URI = "sound_uri"
    const val API_TIMER_LENGTH_MS = "timer_length_ms"

    /** No caller the platform names: never the shell's own uid. */
    const val NO_CALLER = -1

    const val WHY_NOT_SHELL = "not the shell"

    private const val MAX_ID = 128
    private const val MAX_URI = 2048
    private val TIMER_ID = Regex("[A-Za-z0-9_-]{1,40}")

    /** Who started the activity, asked of the one platform port; [NO_CALLER] when the platform names nobody or throws. */
    fun launchCaller(access: UriAccessPort): Int = try { UriAccessRules.starterUid(access) ?: NO_CALLER } catch (e: Throwable) { NO_CALLER }

    /** Caller-supplied text as one token of a diagnostics line (`FilesIntents.lineSafe`'s rule): no line break can come through. */
    fun token(text: String?): String = FilesIntents.lineSafe(text, 80, token = true)

    /**
     * @param extras the intent's extras, or null when there is no intent
     * @param callerUid the uid that sent THIS intent: [launchCaller] for the launch, the platform's own caller for a new
     *   intent on API 35+, [NO_CALLER] when the platform does not say
     * @param myUid `Process.myUid()`
     */
    fun open(extras: ExtrasPort?, callerUid: Int, myUid: Int): ClockOpen {
        if (extras == null) return ClockOpen()
        val safe = SafeExtras(extras)
        val lines = mutableListOf<String>()
        val tile = safe.string(LiveTileProtocol.EXTRA_LAUNCH_TILE_ID, MAX_ID)
        val page = safe.string(EXTRA_PAGE, MAX_ID)
        var tab: ClockTab? = null
        var focused: String? = null
        val timerId = tile?.takeIf { it.startsWith(TileRouting.TIMER_TILE_PREFIX) }?.removePrefix(TileRouting.TIMER_TILE_PREFIX)
        when {
            timerId != null -> if (TIMER_ID.matches(timerId)) { focused = timerId; tab = ClockTab.TIMER }
            tile == TileRouting.STOPWATCH_TILE_ID -> tab = ClockTab.STOPWATCH
            page != null -> tab = ClockTab.byId(page)
        }
        var alarm: AlarmDraft? = null
        var timer: TimerDraft? = null
        var api = "none"
        // The caller first: for anyone but the shell the bundle is not even unpacked.
        val sent = try { extras.value(EXTRA_API_EDIT) != null } catch (e: Throwable) { true }
        if (sent) {
            if (myUid < 0 || callerUid != myUid) {
                api = "ignored: $WHY_NOT_SHELL"
            } else {
                when (val edit = edit(safe.nested(EXTRA_API_EDIT))) {
                    is Edit.Alarm -> { alarm = edit.draft; tab = ClockTab.ALARM; api = "alarm" }
                    is Edit.Timer -> { timer = edit.draft; tab = ClockTab.TIMER; api = "timer" }
                    is Edit.Ignored -> api = "ignored: ${edit.why}"
                }
            }
        }
        safe.refused.forEach { lines += it }
        if (api.startsWith("ignored")) lines += "api edit $api"
        lines += "open page=${page?.let { token(it) } ?: "none"} tile=${tile?.let { token(it) } ?: "none"} api=$api -> tab ${tab?.id ?: "unchanged"}"
        return ClockOpen(tab, focused, alarm, timer, lines)
    }

    sealed interface Edit {
        data class Alarm(val draft: AlarmDraft) : Edit
        data class Timer(val draft: TimerDraft) : Edit
        data class Ignored(val why: String) : Edit
    }

    /** The handler's bundle, every field through the API's own checks; one bad field and the whole bundle is ignored. */
    fun edit(api: SafeExtras?): Edit {
        api ?: return Edit.Ignored("unreadable")
        val before = api.refused.size
        val kind = api.string(API_KIND, 16)
        val message = api.string(API_MESSAGE, AlarmApiRules.MAX_EXTRA_TEXT).orEmpty()
        val hour = api.int(API_HOUR)
        val minute = api.int(API_MINUTE)
        val days = api.ints(API_DAYS, AlarmApiRules.MAX_DAYS).orEmpty()
        val soundKind = api.string(API_SOUND_KIND, 16)
        val soundUri = api.string(API_SOUND_URI, MAX_URI)
        val length = api.long(API_TIMER_LENGTH_MS)
        if (api.refused.size != before) return Edit.Ignored("unreadable")
        AlarmApiRules.messageRefusal(message)?.let { return Edit.Ignored(it) }
        return when (kind) {
            "timer" -> {
                if (length != null && !AlarmApiRules.timerLengthOk(length)) return Edit.Ignored("length out of range")
                val base = TimerDraft.new().copy(name = message)
                Edit.Timer(length?.let { ClockText.hmsParts(it) }?.let { (h, m, s) -> base.copy(hours = h, minutes = m, seconds = s) } ?: base)
            }
            "alarm" -> {
                if (hour != null && !AlarmApiRules.hourOk(hour)) return Edit.Ignored("hour out of range")
                if (minute != null && !AlarmApiRules.minuteOk(minute)) return Edit.Ignored("minutes out of range")
                if (days.any { !AlarmApiRules.dayOk(it) }) return Edit.Ignored("days out of range")
                val sound = when (soundKind) {
                    AlarmSound.Kind.VIBRATE.name -> AlarmSound(AlarmSound.Kind.VIBRATE)
                    // L18-2: the sound the bundle names is weighed again, as for a caller nobody can be asked about: a
                    // sound every app can read, else the default.
                    AlarmSound.Kind.TONE.name -> AlarmRingtoneRules.fromApi(soundUri, null).sound
                    else -> AlarmSound.DEFAULT
                }
                val base = AlarmDraft.new()
                Edit.Alarm(base.copy(hour = hour ?: base.hour, minute = minute ?: base.minute, name = message, days = days.map { DayOfWeek.of(it) }.toSet(), sound = sound))
            }
            else -> Edit.Ignored("unknown kind")
        }
    }
}
