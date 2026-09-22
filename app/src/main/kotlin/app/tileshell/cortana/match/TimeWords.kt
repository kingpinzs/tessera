package app.tileshell.cortana.match

import java.util.Calendar

/**
 * The spoken time and duration forms the ruled commands need ("at 8", "tomorrow at 7:30 am",
 * "in five minutes"). The recognizer emits words, not digits, so every number word is handled too.
 *
 * This is pure logic over a millisecond `now`, so the unit tests can pin every form without a clock.
 */
object TimeWords {

    /** What a phrase parsed to, and the part of the phrase that was consumed. */
    data class Parsed<T>(val value: T, val consumed: IntRange)

    private val NUMBER_WORDS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "ninety" to 90, "a" to 1, "an" to 1, "half" to 30, "quarter" to 15,
    )

    fun numberOf(word: String): Int? = word.toIntOrNull() ?: NUMBER_WORDS[word]

    /**
     * "in five minutes", "for 10 seconds", "for an hour and a half".
     * @return the duration in seconds and the range of [words] it used
     */
    fun parseDuration(words: List<String>): Parsed<Int>? {
        var total = 0
        var start = -1
        var end = -1
        var pendingNumber: Int? = null
        words.forEachIndexed { index, word ->
            val number = numberOf(word)
            if (number != null) {
                pendingNumber = (pendingNumber ?: 0) + number
                if (start < 0) start = index
                end = index
                return@forEachIndexed
            }
            val unitSeconds = when (word.trimEnd('s')) {
                "second", "sec" -> 1
                "minute", "min" -> 60
                "hour", "hr" -> 3600
                else -> null
            }
            if (unitSeconds != null && pendingNumber != null) {
                total += pendingNumber!! * unitSeconds
                pendingNumber = null
                end = index
            } else if (word != "and") {
                // A word that is neither a number nor a unit ends the duration phrase.
                if (total > 0) return Parsed(total, start..end)
                if (pendingNumber != null) { pendingNumber = null; start = -1; end = -1 }
            }
        }
        return if (total > 0 && start >= 0) Parsed(total, start..end) else null
    }

    /**
     * "at 8", "at 8:30", "at eight thirty pm", "tomorrow at 7", "at noon", "in 20 minutes",
     * "on monday at 9". Returns the absolute wall-clock time, always in the future.
     */
    fun parseTime(words: List<String>, nowMs: Long): Parsed<Long>? {
        // "in <duration>" is a time too: "remind me to stretch in 20 minutes".
        val inIndex = words.indexOf("in")
        if (inIndex >= 0) {
            parseDuration(words.drop(inIndex + 1))?.let { duration ->
                val shifted = (duration.consumed.first + inIndex + 1)..(duration.consumed.last + inIndex + 1)
                return Parsed(nowMs + duration.value * 1000L, inIndex..shifted.last)
            }
        }

        var dayOffset = 0
        var dayWordIndex: Int? = null
        var weekday: Int? = null
        words.forEachIndexed { index, word ->
            when (word) {
                "today", "tonight" -> { dayOffset = 0; dayWordIndex = index }
                "tomorrow" -> { dayOffset = 1; dayWordIndex = index }
                else -> WEEKDAYS[word]?.let { weekday = it; dayWordIndex = index }
            }
        }

        val atIndex = words.indexOfFirst { it == "at" }
        var hour: Int? = null
        var minute = 0
        var meridiem: String? = null
        var timeStart = -1
        var timeEnd = -1

        fun readClockAt(index: Int): Boolean {
            if (index !in words.indices) return false
            val word = words[index]
            if (word == "noon") { hour = 12; minute = 0; timeStart = index; timeEnd = index; return true }
            if (word == "midnight") { hour = 0; minute = 0; timeStart = index; timeEnd = index; return true }
            // "8:30" comes through as one token when the recognizer wrote digits.
            if (word.contains(':')) {
                val parts = word.split(':')
                val h = parts[0].toIntOrNull() ?: return false
                hour = h; minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                timeStart = index; timeEnd = index
                return true
            }
            val h = numberOf(word) ?: return false
            if (h > 24) return false
            hour = h
            timeStart = index; timeEnd = index
            // "eight thirty", "eight oh five", "eight fifteen"
            val next = words.getOrNull(index + 1)
            if (next != null) {
                if (next == "o'clock" || next == "oclock") {
                    timeEnd = index + 1
                } else if (next == "oh" || next == "o") {
                    numberOf(words.getOrNull(index + 2).orEmpty())?.let { minute = it; timeEnd = index + 2 }
                } else {
                    numberOf(next)?.let { if (it in 1..59) { minute = it; timeEnd = index + 1 } }
                }
            }
            return true
        }

        if (atIndex >= 0) readClockAt(atIndex + 1)
        if (hour == null) {
            // "remind me at 8" is the common form, but "wake me up 7 am" happens too.
            val meridiemIndex = words.indexOfFirst { it == "am" || it == "pm" }
            if (meridiemIndex > 0) readClockAt(meridiemIndex - 1)
        }
        if (hour == null) return null

        words.getOrNull(timeEnd + 1)?.let { if (it == "am" || it == "pm") { meridiem = it; timeEnd += 1 } }

        val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
        var h = hour!!
        when {
            meridiem == "pm" && h < 12 -> h += 12
            meridiem == "am" && h == 12 -> h = 0
        }
        calendar.set(Calendar.HOUR_OF_DAY, h)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        when {
            weekday != null -> {
                // The next occurrence of that weekday, today included only if the time is still ahead.
                while (calendar.get(Calendar.DAY_OF_WEEK) != weekday || calendar.timeInMillis <= nowMs) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            dayOffset > 0 -> calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
            calendar.timeInMillis <= nowMs && meridiem == null && h < 12 -> {
                // "at 8" said at 9 in the morning means 8 in the evening, the way a phone assistant reads it;
                // if that is past too, tomorrow.
                calendar.add(Calendar.HOUR_OF_DAY, 12)
                if (calendar.timeInMillis <= nowMs) calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            calendar.timeInMillis <= nowMs -> calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val start = minOf(dayWordIndex ?: timeStart, if (atIndex >= 0) atIndex else timeStart)
        val end = maxOf(timeEnd, dayWordIndex ?: timeEnd)
        return Parsed(calendar.timeInMillis, start..end)
    }

    /** "every day", "every week", "every month", "every year" (the card's dropdown, H19). */
    fun parseRecurrence(words: List<String>): Parsed<app.tileshell.cortana.reminders.Recurrence>? {
        val index = words.indexOfFirst { it == "every" }
        if (index < 0) return null
        val unit = words.getOrNull(index + 1)?.trimEnd('s') ?: return null
        val recurrence = when (unit) {
            "day" -> app.tileshell.cortana.reminders.Recurrence.DAY
            "week" -> app.tileshell.cortana.reminders.Recurrence.WEEK
            "month" -> app.tileshell.cortana.reminders.Recurrence.MONTH
            "year" -> app.tileshell.cortana.reminders.Recurrence.YEAR
            else -> return null
        }
        return Parsed(recurrence, index..(index + 1))
    }

    private val WEEKDAYS = mapOf(
        "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY,
    )
}
