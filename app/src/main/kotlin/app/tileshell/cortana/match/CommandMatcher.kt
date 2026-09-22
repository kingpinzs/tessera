package app.tileshell.cortana.match

import app.tileshell.cortana.reminders.Recurrence
import app.tileshell.diag.Diagnostics

/**
 * Turns a transcript into a [Request] (phase 03 build task 5).
 *
 * Fixed commands now, an on-device LLM later as a permanent ADDED layer (Q2, A7): anything this matcher
 * does not recognise becomes [Request.NotUnderstood] carrying the open-vocabulary text, which is the
 * extension point phase 08 attaches to. The matcher is never the thing that decides a request is
 * impossible — it only decides what it recognises.
 *
 * Pure over its inputs, so every phrasing is pinned by a unit test with no device and no clock.
 */
object CommandMatcher {

    /** What is on screen waiting for an answer, which changes what a bare "yes" means. */
    enum class Awaiting {
        /** R6 §3.4.1's read-back: send it / add more / try again / cancel. */
        TEXT_READBACK,

        /** A confirm card (reminder, calendar, call, delete): yes / no / cancel — never "add more". */
        CARD,

        /** "When would you like to be reminded?" — a time, or "whenever" (H16). */
        MISSING_TIME,
    }

    data class Context(val awaiting: Awaiting? = null, val nowMs: Long = System.currentTimeMillis())

    private val CONFIRM = setOf("yes", "yeah", "yep", "yup", "sure", "ok", "okay", "confirm", "do it", "go ahead", "send it", "send", "call", "remind", "add", "delete")
    private val CANCEL = setOf("no", "nope", "cancel", "never mind", "nevermind", "stop", "forget it")
    private val ADD_MORE = setOf("add more", "add to it", "more")
    private val TRY_AGAIN = setOf("try again", "start over", "again")
    private val WHENEVER = setOf("whenever", "no time", "any time", "anytime", "sometime")

    fun match(raw: String, context: Context = Context()): Request {
        val text = normalise(raw)
        if (text.isBlank()) return Request.Silence
        val words = text.split(' ').filter { it.isNotEmpty() }

        context.awaiting?.let { awaiting ->
            answer(text, words, awaiting, context)?.let {
                Diagnostics.add("match", "\"$raw\" -> $it (awaiting=$awaiting)")
                return it
            }
        }

        val request = command(text, words, context)
            ?: Request.NotUnderstood(raw.trim())
        Diagnostics.add("match", "\"$raw\" -> $request")
        return request
    }

    // ---------------- answers ----------------

    private fun answer(text: String, words: List<String>, awaiting: Awaiting, context: Context): Request? {
        // "add more" and "try again" are two words; check the phrases before the single-word sets.
        if (awaiting == Awaiting.TEXT_READBACK && ADD_MORE.any { text == it || text.startsWith("$it ") }) return Request.AddMore
        if (TRY_AGAIN.any { text == it || text.startsWith("$it ") }) return Request.TryAgain
        if (awaiting == Awaiting.MISSING_TIME) {
            if (WHENEVER.any { text == it || text.contains(it) }) return Request.Whenever
            // At the missing-time card any time at all answers it; the model reads it off the request.
            TimeWords.parseTime(words, context.nowMs)?.let { return Request.SetReminder("", it.value) }
        }
        if (CANCEL.any { text == it }) return Request.Cancel
        if (CONFIRM.any { text == it }) return Request.Confirm
        return null
    }

    // ---------------- commands ----------------

    private fun command(text: String, words: List<String>, context: Context): Request? {
        reminder(text, words, context)?.let { return it }
        deleteReminder(text)?.let { return it }
        calendar(text, words, context)?.let { return it }
        alarmOrTimer(text, words, context)?.let { return it }
        message(text)?.let { return it }
        call(text)?.let { return it }
        savePlace(text)?.let { return it }

        after(text, "open ", "launch ", "run ")?.let { return Request.OpenApp(it) }
        after(text, "directions to ", "navigate to ", "how do i get to ", "take me to ")?.let { return Request.Directions(it) }
        after(text, "play ")?.let { rest ->
            return if (rest == "music" || rest == "some music" || rest == "my music") Request.PlayMusic(null)
            else Request.PlayMusic(rest)
        }
        if (text == "play" || text == "play music") return Request.PlayMusic(null)

        if (matchesAny(text, "take a photo", "take a picture", "take a selfie", "open the camera")) return Request.TakePhoto
        after(text, "take a note ", "make a note ", "note that ")?.let { return Request.TakeNote(it) }
        if (matchesAny(text, "take a note", "make a note", "new note")) return Request.TakeNote(null)

        if (matchesAny(text, "what's on my calendar", "what is on my calendar", "whats on my calendar",
                "what do i have today", "what's my day look like", "what's next on my calendar")) return Request.WhatsOnMyCalendar

        if (matchesAny(text, "what time is it", "what's the time", "whats the time", "what is the time",
                "tell me the time", "time")) return Request.TimeQuery
        if (matchesAny(text, "what's today's date", "whats todays date", "what is today's date", "what day is it",
                "what's the date", "whats the date", "what is the date", "today's date", "date")) return Request.DateQuery

        // Weather is matched last, after every command that could contain one of these words (a
        // reminder to bring an umbrella is already a reminder by the time this line runs).
        if (WEATHER_WORDS.any { text.contains(it) }) return Request.Weather

        return null
    }

    /** "remind me to <task> at <time> / when I get to <place> / next time I talk to <name>". */
    private fun reminder(text: String, words: List<String>, context: Context): Request? {
        val body = after(text, "remind me to ", "remind me ", "set a reminder to ", "set a reminder ",
            "add a reminder to ", "create a reminder to ") ?: return null

        val bodyWords = body.split(' ').filter { it.isNotEmpty() }

        // Person first: "next time I talk to Mom" is unambiguous and would otherwise lose "time" to the clock.
        PERSON_PATTERNS.firstNotNullOfOrNull { pattern ->
            Regex(pattern).find(body)?.let { it.groupValues[1].trim() to it.range }
        }?.let { (name, range) ->
            val task = body.removeRange(range).trim().trim(',').trim()
            if (name.isNotBlank()) return Request.SetReminder(cleanTask(task), null, personName = personName(name))
        }

        PLACE_PATTERNS.firstNotNullOfOrNull { pattern ->
            Regex(pattern).find(body)?.let { it.groupValues[1].trim() to it.range }
        }?.let { (place, range) ->
            val task = body.removeRange(range).trim().trim(',').trim()
            if (place.isNotBlank()) return Request.SetReminder(cleanTask(task), null, placeName = normalisePlace(place))
        }

        val recurrence = TimeWords.parseRecurrence(bodyWords)
        val time = TimeWords.parseTime(bodyWords, context.nowMs)
        val consumed = listOfNotNull(recurrence?.consumed, time?.consumed)
        val task = cleanTask(bodyWords.filterIndexed { index, _ -> consumed.none { index in it } }.joinToString(" "))
        return Request.SetReminder(task, time?.value, recurrence?.value ?: Recurrence.ONCE)
    }

    private fun deleteReminder(text: String): Request? =
        after(text, "delete the reminder to ", "delete my reminder to ", "cancel the reminder to ",
            "cancel my reminder to ", "remove the reminder to ")?.let { Request.DeleteReminder(cleanTask(it)) }

    private fun calendar(text: String, words: List<String>, context: Context): Request? {
        after(text, "delete the event ", "delete the appointment ", "cancel the event ",
            "cancel the appointment ", "remove the event ")?.let { return Request.DeleteCalendarEvent(it) }

        val body = after(text, "add ", "create ", "schedule ", "put ") ?: return null
        // Only a calendar phrase makes this a calendar add; "add milk to my list" is not one.
        if (!body.contains("calendar") && !body.contains("appointment") && !body.contains("meeting") &&
            !body.contains("event")
        ) return null
        val bodyWords = body.split(' ').filter { it.isNotEmpty() }
        val time = TimeWords.parseTime(bodyWords, context.nowMs) ?: return null
        val title = bodyWords
            .filterIndexed { index, _ -> index !in time.consumed }
            .joinToString(" ")
            .replace(Regex("\\b(to|on|in)? ?my calendar\\b"), "")
            .replace(Regex("\\ban? (appointment|meeting|event)( (called|named|for))?\\b"), "")
            .trim()
        return Request.AddCalendarEvent(title.ifBlank { "Appointment" }, time.value)
    }

    private fun alarmOrTimer(text: String, words: List<String>, context: Context): Request? {
        if (text.contains("timer")) {
            val duration = TimeWords.parseDuration(words) ?: return null
            return Request.SetTimer(duration.value)
        }
        if (!text.contains("alarm") && !text.startsWith("wake me")) return null
        val time = TimeWords.parseTime(words, context.nowMs) ?: return null
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = time.value }
        return Request.SetAlarm(calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE))
    }

    /** "text Mom I'm on my way", "send a text to Mom", "message Mom". */
    private fun message(text: String): Request? {
        val body = after(text, "text ", "send a text to ", "send a message to ", "message ", "send a text ") ?: return null
        // "text Mom saying I'm late" / "text Mom that I'm late" — the message starts after the connector.
        SAYING.firstNotNullOfOrNull { word ->
            Regex("^(.+?) $word (.+)$").find(body)?.let { it.groupValues[1].trim() to it.groupValues[2].trim() }
        }?.let { (name, message) -> return Request.TextContact(personName(name), message) }

        val words = body.split(' ').filter { it.isNotEmpty() }
        // One or two words is a name; anything longer is "<name> <message>" with a one-word name, which is
        // what W10M's one-shot form was. A name alone leaves the message null and Cortana asks for it.
        return when {
            words.size <= 2 -> Request.TextContact(personName(body), null)
            else -> Request.TextContact(personName(words.first()), words.drop(1).joinToString(" "))
        }
    }

    private fun call(text: String): Request? =
        after(text, "call ", "phone ", "dial ", "ring ")?.let { body ->
            // "call Mom on mobile" — the number label is read back from the contact, not taken from here.
            val name = body.replace(Regex("\\bon (mobile|home|work|cell)\\b"), "").trim()
            if (name.isBlank()) null else Request.CallContact(personName(name))
        }

    /** Place source C: "this is home" / "save this as work" saves the spot the phone is standing on. */
    private fun savePlace(text: String): Request? {
        Regex("^this is (?:my )?(.+)$").find(text)?.let { return Request.SavePlaceHere(normalisePlace(it.groupValues[1])) }
        Regex("^save (?:this|here|my location) as (?:my )?(.+)$").find(text)?.let {
            return Request.SavePlaceHere(normalisePlace(it.groupValues[1]))
        }
        return null
    }

    // ---------------- the grammar pass ----------------

    /**
     * The phrases the grammar pass boosts (Decisions: "grammar pass + open pass on one runtime").
     * [contactNames] and [appNames] come from the device, so the boost covers the words that actually
     * follow a command here — a generic model has no reason to prefer "Ilkka" over "ill car".
     */
    fun hotwords(contactNames: List<String> = emptyList(), appNames: List<String> = emptyList()): String {
        val phrases = COMMAND_PHRASES + contactNames.flatMap { listOf("call $it", "text $it") } + appNames.map { "open $it" }
        return phrases.filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    private val COMMAND_PHRASES = listOf(
        "open", "call", "text", "send a text to", "message",
        "set an alarm for", "set a timer for", "wake me up at",
        "remind me to", "when i get to", "when i get home", "next time i talk to",
        "delete the reminder to", "add to my calendar", "what's on my calendar",
        "what time is it", "what's today's date", "what's the weather",
        "play music", "play", "directions to", "navigate to",
        "take a photo", "take a picture", "take a note",
        "send it", "add more", "try again", "cancel", "yes", "no", "whenever",
    )

    // ---------------- helpers ----------------

    private val WEATHER_WORDS = listOf(
        "weather", "forecast", "how hot", "how cold", "how warm", "rain", "raining", "snow", "snowing",
        "temperature", "umbrella", "degrees outside",
    )

    private val SAYING = listOf("saying", "that says", "that", "and say", "say")

    private val PERSON_PATTERNS = listOf(
        "\\bnext time i (?:talk|speak) to (.+)$",
        "\\bwhen i (?:talk|speak) to (.+)$",
        "\\bthe next time i (?:talk|speak) to (.+)$",
    )

    private val PLACE_PATTERNS = listOf(
        "\\bwhen i get to (.+)$",
        "\\bwhen i (?:get|arrive) (?:at|to) (.+)$",
        "\\bwhen i arrive at (.+)$",
        "\\bwhen i get (home|work)\\b",
        "\\bat (home|work)$",
    )

    /**
     * "when I get home" names the place "Home". The place names stay title-cased because the Places
     * page, the card and the subline all show them to the user.
     */
    private fun normalisePlace(place: String): String =
        place.trim().removePrefix("the ").trim().replaceFirstChar { it.uppercase() }

    /**
     * A person's name is read back — on the card, in the subline, and in "I couldn't find <name> in
     * your contacts" — so it is title-cased here rather than left in the recogniser's lower case.
     * The Contacts lookup is case-insensitive, so this changes what is SHOWN and nothing else.
     */
    private fun personName(name: String): String =
        name.trim().split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    private fun cleanTask(task: String): String =
        task.trim().removePrefix("to ").removeSuffix(" at").removeSuffix(" on").removeSuffix(" in").trim()

    private fun after(text: String, vararg prefixes: String): String? =
        prefixes.firstOrNull { text.startsWith(it) }?.let { text.removePrefix(it).trim().ifBlank { null } }

    /**
     * A fixed query phrase, matched as a PHRASE rather than as the whole utterance.
     *
     * Real recognition puts stray words at the edges — the device produced "AT WHAT TIME IS IT" for
     * "What time is it?" — and an exact-equality match turns that into "Sorry, I can't do that yet."
     * A multi-word form therefore only has to be CONTAINED in the utterance. A single-word form
     * ("time", "date") still has to be the whole utterance, because "set a timer" contains "time".
     *
     * Order does the rest of the work: every command that could contain one of these phrases —
     * a reminder, a calendar add, an alarm — is matched before this is ever reached.
     */
    private fun matchesAny(text: String, vararg forms: String): Boolean = forms.any { form ->
        if (' ' in form) text == form || text.contains(form) else text == form
    }

    /**
     * The recognizer emits lower-case words with no punctuation, but a typed request (the text box) can
     * carry anything, and both run the same matcher (fidelity A4). Normalising here is what makes that true.
     */
    fun normalise(raw: String): String = raw.lowercase()
        .replace('’', '\'')
        .replace(Regex("[^a-z0-9':\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        // The recogniser spells the meridiem out as two letters: the device produced
        // "SET IN ALARM FOR SEVEN TWENTY A M", and without this the phrase carries no am/pm at all,
        // the time does not parse, and the whole alarm falls through to "Sorry, I can't do that yet."
        .replace(Regex("\\ba m\\b"), "am")
        .replace(Regex("\\bp m\\b"), "pm")
        .replace(Regex("\\bo clock\\b"), "o'clock")
        // A command verb does not always arrive first. The device produced "THE TEXT MA'AM ON MY WAY"
        // for "Text Mom I'm on my way" and "AT WHAT TIME IS IT" for "What time is it?", and a command
        // prefix is matched from the START of the utterance, so one stray leading word loses the whole
        // request. None of the ruled commands begins with any of these, so dropping them costs nothing.
        .replace(Regex("^(?:the|a|uh|um|er|so|ok|okay|hey|please|and|now|just)\\s+"), "")
        .trim()
}
