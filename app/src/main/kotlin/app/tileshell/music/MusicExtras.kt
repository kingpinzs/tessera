package app.tileshell.music

import kotlin.math.ceil

/**
 * The sleep timer and the equaliser (phase 10 build task 9), and the small protocol that carries them
 * between the collection and the playback service.
 *
 * ### Both live in the SERVICE, and that is the point
 *
 * The music outlives Start and outlives this app's activity (E7), so anything that decides when the
 * music stops, or what it sounds like, has to outlive them too. A sleep timer held by the activity
 * would be cancelled by the very thing people do after setting one — putting the phone down and
 * letting Android reclaim the app. So the activity only SENDS a custom session command, the service
 * owns the timer and the effect, and the service publishes what it is doing in the session's extras,
 * which every connected controller receives.
 *
 * ### Where they are reached, which is a build-time call
 *
 * R8 measured the now-playing `•••` as present and could not establish what it holds (UNMEASURED-3).
 * It is the one control on that screen whose contents are unmeasured, and "more" is exactly what a
 * sleep timer and an equaliser are, so that is where they go — recorded as a P4 design call, not as
 * something R8 said.
 */
object MusicCommands {
    /** Arm or disarm the sleep timer. [ARG_MINUTES]: minutes (> 0), [SleepTimer.END_OF_TRACK], or 0 for off. */
    const val SLEEP = "app.tileshell.music.SLEEP"
    /** Choose an equaliser preset. [ARG_PRESET]: a preset index, or [Equaliser.OFF]. */
    const val EQUALISER = "app.tileshell.music.EQUALISER"
    /** Set the crossfade. [ARG_MS]: one of [Crossfade.Choice]'s lengths, 0 for off (E17). */
    const val CROSSFADE = "app.tileshell.music.CROSSFADE"
    const val ARG_MINUTES = "minutes"
    const val ARG_PRESET = "preset"
    const val ARG_MS = "ms"

    /** Session extras: the deadline in elapsedRealtime (0 = no timer), and the end-of-track flag. */
    const val X_SLEEP_AT = "sleepAt"
    const val X_SLEEP_END_OF_TRACK = "sleepEndOfTrack"
    /** Session extras: the chosen preset, the preset names, and whether the device has an equaliser. */
    const val X_EQ_PRESET = "eqPreset"
    const val X_EQ_PRESETS = "eqPresets"
    const val X_EQ_AVAILABLE = "eqAvailable"
    /** Session extras: the crossfade length in ms, 0 for off (gapless). */
    const val X_CROSSFADE_MS = "crossfadeMs"
}

object SleepTimer {
    const val OFF = 0
    const val END_OF_TRACK = -1

    /**
     * What the timer can be set to. No W10M original measured one, so these are the choices every
     * player that has a sleep timer offers — a quarter of an hour at a time, and the end of the track
     * that is playing, which is the one people actually use at night.
     */
    enum class Choice(val label: String, val minutes: Int, val tag: String) {
        MIN_15("In 15 minutes", 15, "15"),
        MIN_30("In 30 minutes", 30, "30"),
        MIN_45("In 45 minutes", 45, "45"),
        HOUR("In 1 hour", 60, "60"),
        END_OF_TRACK("At the end of this track", SleepTimer.END_OF_TRACK, "eot"),
    }

    /** The deadline a choice arms, in the same clock it is later compared against (elapsedRealtime). */
    fun deadline(nowElapsed: Long, minutes: Int): Long = if (minutes > 0) nowElapsed + minutes * 60_000L else 0L

    /**
     * Whole minutes left, rounded UP: a timer with 14 minutes 10 seconds to run says 15, and one with
     * 20 seconds to run says 1 — never "0 minutes" while it has not yet fired.
     */
    fun minutesLeft(nowElapsed: Long, deadline: Long): Int =
        if (deadline <= nowElapsed) 0 else ceil((deadline - nowElapsed) / 60_000.0).toInt()

    /** What the `•••` menu's entry says, so the timer's state is visible without a second surface. */
    fun menuLabel(nowElapsed: Long, deadline: Long, endOfTrack: Boolean): String = when {
        endOfTrack -> "Sleep timer: end of this track"
        deadline > nowElapsed -> minutesLeft(nowElapsed, deadline).let { m ->
            "Sleep timer: $m ${if (m == 1) "minute" else "minutes"} left"
        }
        else -> "Sleep timer"
    }

}

object Equaliser {
    const val OFF = -1

    /**
     * The equaliser's choices are the DEVICE's own presets (Normal, Classical, Dance, Flat, …, as the
     * platform effect names them) plus Off. Not a hand-drawn band editor: no W10M original was measured
     * for one, and the presets are what the phone's audio effect library actually implements, so every
     * one of them is guaranteed to do something on the phone it is offered on.
     */
    fun menuLabel(preset: Int, names: List<String>): String =
        "Equaliser: " + (names.getOrNull(preset) ?: "off")

    /**
     * A preset's name as it can be shown. The platform effect copies each name out of a fixed-size C
     * buffer, so what `getPresetName` returns can carry NUL characters after the name — MUSIC9's first
     * run drew them into the list, and uiautomator crashed serialising the node, which is also what
     * any accessibility service reading that row would have been handed. Everything from the first NUL
     * is dropped; a name that is empty after that is given its position, so the list stays aligned with
     * the preset indices the effect uses.
     */
    fun cleanName(raw: String?, index: Int): String =
        raw.orEmpty().substringBefore('\u0000').trim().ifEmpty { "Preset ${index + 1}" }
}
