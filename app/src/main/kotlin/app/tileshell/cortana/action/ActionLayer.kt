package app.tileshell.cortana.action

import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.telecom.TelecomManager
import android.telephony.SmsManager
import app.tileshell.apps.AppCatalog
import app.tileshell.apps.AppEntry
import app.tileshell.cortana.Card
import app.tileshell.cortana.CardAction
import app.tileshell.cortana.CardButton
import app.tileshell.cortana.CardField
import app.tileshell.cortana.CardKind
import app.tileshell.cortana.ContactChip
import app.tileshell.cortana.CortanaPrefs
import app.tileshell.cortana.match.CommandMatcher
import app.tileshell.cortana.match.Request
import app.tileshell.cortana.reminders.Place
import app.tileshell.cortana.reminders.Recurrence
import app.tileshell.cortana.reminders.Reminder
import app.tileshell.cortana.reminders.ReminderKind
import app.tileshell.cortana.reminders.ReminderStore
import app.tileshell.cortana.reminders.ReminderText
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.TileNotificationListener
import app.tileshell.tiles.LayoutStore
import app.tileshell.tiles.Slot
import app.tileshell.tiles.SlotResolver
import app.tileshell.weather.WeatherFeed
import app.tileshell.weather.WmoCodes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * What a request did, and what Cortana says and shows for it.
 *
 * @param close the session hides because an app took the screen
 */
data class Outcome(
    val spoken: String,
    val card: Card?,
    val pending: Pending? = null,
    val awaiting: CommandMatcher.Awaiting? = null,
    val close: Boolean = false,
    /** Cortana's Settings > Places, opened because a spoken place had no saved place. */
    val openPlaces: String? = null,
)

/** Activity starts the action layer cannot do for itself: only the session can start a voice activity. */
interface ActionHost {
    /**
     * `VoiceInteractionSession.startVoiceActivity`. Alarms and timers go through this with
     * `EXTRA_SKIP_UI` so they can be set over the keyguard without showing anything (E10).
     * @return false when the platform refuses it (which E10 records, and PQ3 re-asks Jeremy about)
     */
    fun startVoiceActivity(intent: Intent): Boolean

    /** A normal launch: the app takes the screen and the session hides. */
    fun launch(intent: Intent)

    fun launchApp(entry: AppEntry)

    /** `KeyguardManager.requestDismissKeyguard` behind the "Unlock" button (H12). */
    fun requestUnlock()
}

/**
 * The command actions (phase 03 build task 5). Commands act through real intents so later parts can
 * register as those intents' handlers (RV4); the two that do NOT are the text and the call, which the
 * action layer performs itself with `SmsManager` and `TelecomManager.placeCall`, because R6 §3.4.1's
 * "Message sent." is a reply Cortana gives, not a handoff to another app's compose screen.
 */
class ActionLayer(private val context: Context, private val host: ActionHost) {

    private val store get() = ReminderStore.get(context)
    private val prefs get() = CortanaPrefs.get(context)

    // ---------------- entry point ----------------

    fun run(request: Request): Outcome {
        // The gate is here, not in the matcher, so phase 08 inherits it (Decisions "Locked commands").
        if (LockGate.locked(context) && !LockGate.allowedWhileLocked(request)) {
            Diagnostics.add("cortana", "locked: $request gated")
            return unlockCard(request)
        }
        return when (request) {
            is Request.OpenApp -> openApp(request.name)
            is Request.CallContact -> call(request.name)
            is Request.TextContact -> text(request.name, request.message)
            is Request.SetAlarm -> alarm(request.hour, request.minute)
            is Request.SetTimer -> timer(request.seconds)
            is Request.SetReminder -> reminder(request)
            is Request.DeleteReminder -> deleteReminder(request.text)
            is Request.AddCalendarEvent -> addEvent(request.title, request.beginMs)
            is Request.DeleteCalendarEvent -> deleteEvent(request.title)
            is Request.WhatsOnMyCalendar -> calendarToday()
            is Request.TimeQuery -> answer("It's ${ReminderText.time(context, System.currentTimeMillis())}.")
            is Request.DateQuery -> answer("Today is ${DATE_FORMAT.format(System.currentTimeMillis())}.")
            is Request.PlayMusic -> playMusic(request.query)
            is Request.Directions -> directions(request.destination)
            is Request.TakePhoto -> takePhoto()
            is Request.TakeNote -> takeNote(request.text)
            is Request.Weather -> weather()
            is Request.SavePlaceHere -> Outcome(
                "Where would you like to save ${request.name}?", null, openPlaces = request.name,
            )
            is Request.NotUnderstood -> NotUnderstood.handle(context, request.text)
            is Request.Silence -> Outcome("I didn't catch that.", notUnderstoodCard("I didn't catch that."))
            // Answers with nothing pending are treated as speech that matched nothing.
            is Request.Answer -> NotUnderstood.handle(context, request.toString())
        }
    }

    // ---------------- confirmation flow (R6 §3.4.1, §3.4.2) ----------------

    fun confirm(pending: Pending): Outcome = when (pending) {
        is Pending.SendText -> sendText(pending)
        is Pending.PlaceCall -> dial(pending)
        is Pending.SaveReminder -> storeReminder(pending)
        is Pending.AddEvent -> insertEvent(pending)
        is Pending.DeleteEvent -> removeEvent(pending)
        is Pending.DeleteReminder -> {
            store.delete(pending.reminderId)
            answer("Deleted.")
        }
        is Pending.Locked -> {
            host.requestUnlock()
            Outcome("Unlock your phone to continue.", null, pending = pending, awaiting = null)
        }
        // "add more" and "what do you want to say?" are not confirmable: the next utterance answers them.
        is Pending.AwaitMessage, is Pending.AwaitReminderTime, is AddingTo -> Outcome("", null, pending = pending)
    }

    fun cancel(pending: Pending): Outcome = when (pending) {
        is Pending.SendText, is Pending.AwaitMessage -> answer("Okay, I won't send it.")
        is Pending.PlaceCall -> answer("Okay, I won't call.")
        is Pending.SaveReminder, is Pending.AwaitReminderTime -> answer("Okay, nothing saved.")
        is Pending.AddEvent -> answer("Okay, nothing added.")
        is Pending.DeleteEvent, is Pending.DeleteReminder -> answer("Okay, nothing deleted.")
        is Pending.Locked -> answer("Okay.")
        is AddingTo -> answer("Okay, I won't send it.")
    }

    /** R6 §3.4.1: "add more" is offered on the text read-back and nowhere else. */
    fun addMore(pending: Pending.SendText): Outcome = Outcome(
        spoken = "Sure, what would you like to add?",
        card = readBackCard("What would you like to add?", pending.contact, pending.number, pending.message),
        pending = Pending.AwaitMessage(pending.contact, pending.number).let { AddingTo(it, pending.message) },
        awaiting = null,
    )

    fun tryAgain(pending: Pending): Outcome = when (pending) {
        is Pending.SendText -> askForMessage(pending.contact, pending.number)
        is Pending.AwaitMessage -> askForMessage(pending.contact, pending.number)
        is Pending.PlaceCall -> Outcome(
            "Who do you want to call?",
            Card(CardKind.CALL_CONFIRM, "Who do you want to call?", caption = "Call"),
        )
        is Pending.SaveReminder -> Outcome(
            "What would you like to be reminded about?",
            Card(CardKind.REMINDER_CONFIRM, "What would you like to be reminded about?", caption = "Reminder"),
        )
        else -> cancel(pending)
    }

    /** The utterance that answers "What do you want to say?" (or "What would you like to add?"). */
    fun supplyMessage(pending: Pending, spoken: String): Outcome {
        val (contact, number, existing) = when (pending) {
            is Pending.AwaitMessage -> Triple(pending.contact, pending.number, "")
            is AddingTo -> Triple(pending.await.contact, pending.await.number, pending.existing)
            else -> return cancel(pending)
        }
        val whole = listOf(existing, spoken).filter { it.isNotBlank() }.joinToString(" ")
        val next = Pending.SendText(contact, number, whole)
        // R6 §3.4.1: the first read-back says "Okay, I'll text <contact>: <message>"; after "add more" the
        // next one starts "Okay, now I've got:" and carries the WHOLE message, not just the addition.
        val spokenLine = if (existing.isBlank()) {
            "Okay, I'll text ${contact.displayName}: $whole. Send it, add more, or try again?"
        } else {
            "Okay, now I've got: $whole. Send it, add more, or try again?"
        }
        return Outcome(
            spokenLine,
            readBackCard("Send it, add more, or try again?", contact, number, whole),
            pending = next,
            awaiting = CommandMatcher.Awaiting.TEXT_READBACK,
        )
    }

    /** The answer to "When would you like to be reminded?" (H16). A null [timeMs] is a Whenever reminder. */
    fun supplyReminderTime(pending: Pending.AwaitReminderTime, timeMs: Long?): Outcome =
        confirmReminderCard(pending.draft.copy(timeMs = timeMs), null)

    // ---------------- the commands ----------------

    private fun openApp(name: String): Outcome {
        val catalog = AppCatalog.get(context)
        val apps = catalog.apps.value
        val exact = apps.filter { it.label.equals(name, ignoreCase = true) }
        val partial = apps.filter { it.label.contains(name, ignoreCase = true) }
        val matches = exact.ifEmpty { partial }
        return when {
            matches.isEmpty() -> answer("I don't see an app called $name.")
            // "two apps with similar names" (edge case): W10M asked; here the closest label wins and Cortana
            // says which one it opened, so the user can say the other name.
            else -> {
                val entry = matches.minByOrNull { it.label.length }!!
                host.launchApp(entry)
                Outcome("Opening ${entry.label}.", null, close = true)
            }
        }
    }

    private fun call(name: String): Outcome {
        val matches = Contacts.byName(context, name)
        if (matches.isEmpty()) return answer("I couldn't find $name in your contacts.")
        if (matches.size > 1) return answer("I found more than one $name. Which one?")
        val contact = matches.first()
        val number = contact.preferred
            ?: return answer("I don't have a phone number for ${contact.displayName}.")
        val label = number.label.ifBlank { "phone" }
        return Outcome(
            "Okay, I'll call ${contact.displayName}, $label. Call, or try again?",
            Card(
                CardKind.CALL_CONFIRM,
                title = "Call ${contact.displayName}",
                caption = "Call",
                contact = ContactChip(contact.displayName, label, contact.lookupKey),
                callout = "you can say Call, or Cancel",
                buttons = listOf(CardButton("Call", CardAction.CONFIRM), CardButton("Cancel", CardAction.CANCEL)),
            ),
            pending = Pending.PlaceCall(contact, number),
            awaiting = CommandMatcher.Awaiting.CARD,
        )
    }

    private fun text(name: String, message: String?): Outcome {
        val matches = Contacts.byName(context, name)
        if (matches.isEmpty()) return answer("I couldn't find $name in your contacts.")
        if (matches.size > 1) return answer("I found more than one $name. Which one?")
        val contact = matches.first()
        val number = contact.preferred
            ?: return answer("I don't have a phone number for ${contact.displayName}.")
        return if (message.isNullOrBlank()) askForMessage(contact, number)
        else supplyMessage(Pending.AwaitMessage(contact, number), message)
    }

    private fun askForMessage(contact: Contacts.Match, number: Contacts.Number): Outcome = Outcome(
        "Send a text to ${contact.displayName}. What do you want to say?",
        readBackCard("Send a text to ${contact.displayName}. What do you want to say?", contact, number, null),
        pending = Pending.AwaitMessage(contact, number),
    )

    private fun readBackCard(title: String, contact: Contacts.Match, number: Contacts.Number, message: String?) = Card(
        kind = CardKind.TEXT_READBACK,
        title = title,
        caption = "Message",
        contact = ContactChip(contact.displayName, number.label.ifBlank { "phone" }, contact.lookupKey),
        message = message,
        callout = "you can say Send it, Add more, or Try again",
        buttons = listOf(
            // R6 §3.4.1: Send on the left, disabled until there is a message; the right label was not
            // legible in the footage, so "Cancel" is the agent's pick (H5).
            CardButton("Send", CardAction.CONFIRM, enabled = !message.isNullOrBlank()),
            CardButton("Cancel", CardAction.CANCEL),
        ),
    )

    private fun sendText(pending: Pending.SendText): Outcome {
        return runCatching {
            // The platform writes it to the SMS provider, where the default SMS app shows it (Decisions).
            context.getSystemService(SmsManager::class.java)
                .sendTextMessage(pending.number.number, null, pending.message, null, null)
            Diagnostics.add("cortana", "sent text to ${pending.number.number} (${pending.message.length} chars)")
            answer("Message sent.")
        }.getOrElse {
            Diagnostics.add("cortana", "send failed: $it")
            answer("I couldn't send that.")
        }
    }

    private fun dial(pending: Pending.PlaceCall): Outcome = runCatching {
        context.getSystemService(TelecomManager::class.java)
            .placeCall(Uri.fromParts("tel", pending.number.number, null), null)
        Diagnostics.add("cortana", "placed call to ${pending.number.number}")
        Outcome("Calling ${pending.contact.displayName}.", null, close = true)
    }.getOrElse {
        Diagnostics.add("cortana", "call failed: $it")
        answer("I couldn't place that call.")
    }

    private fun alarm(hour: Int, minute: Int): Outcome {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            // EXTRA_SKIP_UI is what lets this run over the keyguard with nothing shown (E10).
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val time = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        }.timeInMillis
        return startClock(intent, "Alarm set for ${ReminderText.time(context, time)}.")
    }

    private fun timer(seconds: Int): Outcome {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return startClock(intent, "Timer set for ${durationWords(seconds)}.")
    }

    private fun startClock(intent: Intent, spoken: String): Outcome {
        val started = host.startVoiceActivity(intent)
        if (started) return answer(spoken)
        // If the platform refuses a voice activity here, the fallback is a normal start, and over the
        // keyguard that is exactly the case PQ3 says to re-ask Jeremy about rather than gate silently.
        Diagnostics.add("cortana", "startVoiceActivity refused for ${intent.action}; falling back to a normal start")
        return runCatching {
            host.launch(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Outcome(spoken, null, close = true)
        }.getOrElse { answer("I couldn't set that.") }
    }

    private fun reminder(request: Request.SetReminder): Outcome {
        if (request.personName != null) {
            val matches = Contacts.byName(context, request.personName)
            if (matches.isEmpty()) return answer("I couldn't find ${request.personName} in your contacts.")
            val contact = matches.first()
            return confirmReminderCard(
                Reminder(
                    id = "", text = request.text, kind = ReminderKind.PERSON,
                    contactLookupKey = contact.lookupKey, contactName = contact.displayName,
                ),
                null,
            )
        }
        if (request.placeName != null) {
            val place = store.placeNamed(request.placeName)
                ?: return Outcome(
                    "I don't know where ${request.placeName} is yet. Want to save it?",
                    Card(
                        CardKind.ANSWER,
                        "I don't know where ${request.placeName} is yet. Want to save it?",
                        caption = "Reminder",
                    ),
                    openPlaces = request.placeName,
                )
            return confirmReminderCard(
                Reminder(id = "", text = request.text, kind = ReminderKind.PLACE, placeId = place.id),
                place.name,
            )
        }
        val draft = Reminder(id = "", text = request.text, timeMs = request.timeMs, recurrence = request.recurrence)
        if (request.timeMs == null) {
            // R6 §3.4.3 (LOW, H16): the card asks for the time, and "whenever" there stores it as a
            // Whenever reminder.
            return Outcome(
                "When would you like to be reminded?",
                Card(
                    CardKind.REMINDER_CONFIRM,
                    title = "When would you like to be reminded?",
                    caption = "Reminder",
                    fields = listOf(
                        CardField("reminder_text", draft.text),
                        CardField("reminder_time", "", "Time"),
                        CardField("reminder_day", "", "Day"),
                    ),
                    recurrence = Recurrence.ONCE,
                    photoRow = true,
                    callout = "try 10th of every month at 5 PM",
                    buttons = listOf(CardButton("Remind", CardAction.CONFIRM), CardButton("Cancel", CardAction.CANCEL)),
                ),
                pending = Pending.AwaitReminderTime(draft),
                awaiting = CommandMatcher.Awaiting.MISSING_TIME,
            )
        }
        return confirmReminderCard(draft, null)
    }

    /** R6 §3.4.2's card, and the P4 place / person variants (H22, H29). */
    fun confirmReminderCard(draft: Reminder, placeNameOverride: String?): Outcome {
        val placeName = placeNameOverride ?: draft.placeId?.let { store.place(it)?.name }
        val fields = when (draft.kind) {
            // A place or person card replaces the time and day fields with one field, and shows no
            // recurrence dropdown, because it fires once (E13, E14).
            ReminderKind.PLACE -> listOf(CardField("reminder_text", draft.text), CardField("reminder_place", placeName.orEmpty()))
            ReminderKind.PERSON -> listOf(CardField("reminder_text", draft.text), CardField("reminder_contact", draft.contactName.orEmpty()))
            ReminderKind.TIME -> listOf(
                CardField("reminder_text", draft.text),
                CardField("reminder_time", draft.timeMs?.let { ReminderText.time(context, it) }.orEmpty(), "Time"),
                CardField("reminder_day", draft.timeMs?.let { ReminderText.dayWord(it) }.orEmpty(), "Day"),
            )
        }
        return Outcome(
            ReminderText.confirmSpoken(draft, placeName, context),
            Card(
                kind = CardKind.REMINDER_CONFIRM,
                title = "Remind you about this?",
                caption = "Reminder",
                contact = if (draft.kind == ReminderKind.PERSON)
                    ContactChip(draft.contactName.orEmpty(), null, draft.contactLookupKey) else null,
                fields = fields,
                recurrence = if (draft.kind == ReminderKind.TIME) draft.recurrence else null,
                photoRow = true,
                callout = "you can say Yes, No, or Cancel",
                buttons = listOf(CardButton("Remind", CardAction.CONFIRM), CardButton("Cancel", CardAction.CANCEL)),
            ),
            pending = Pending.SaveReminder(draft, placeName),
            awaiting = CommandMatcher.Awaiting.CARD,
        )
    }

    private fun storeReminder(pending: Pending.SaveReminder): Outcome {
        val stored = store.add(pending.draft)
        val subline = ReminderText.savedCardSubline(context, stored, store)
        val notice = if (app.tileshell.cortana.reminders.ReminderScheduler.exactAlarmsDenied &&
            stored.kind == ReminderKind.TIME && stored.timeMs != null
        ) " Exact alarms are off, so it may be a few minutes late." else ""
        return Outcome(
            "I'll remind you.$notice",
            Card(
                kind = CardKind.REMINDER_SAVED,
                title = "I'll remind you.",
                caption = "Reminder",
                savedText = stored.text,
                savedSubline = subline,
            ),
        )
    }

    private fun deleteReminder(text: String): Outcome {
        val match = store.active().firstOrNull { it.text.equals(text, ignoreCase = true) }
            ?: store.active().firstOrNull { it.text.contains(text, ignoreCase = true) }
            ?: return answer("I don't have a reminder to $text.")
        return Outcome(
            "Delete this reminder?",
            Card(
                CardKind.DELETE_CONFIRM, "Delete this reminder?", caption = "Reminder",
                fields = listOf(CardField("reminder_text", match.text)),
                buttons = listOf(CardButton("Delete", CardAction.CONFIRM), CardButton("Cancel", CardAction.CANCEL)),
                callout = "you can say Yes, No, or Cancel",
            ),
            pending = Pending.DeleteReminder(match.id, match.text),
            awaiting = CommandMatcher.Awaiting.CARD,
        )
    }

    private fun addEvent(title: String, beginMs: Long): Outcome {
        val endMs = beginMs + 3_600_000L
        return Outcome(
            "Add this to your calendar?",
            Card(
                CardKind.CALENDAR_CONFIRM, "Add this to your calendar?", caption = "Event",
                fields = listOf(
                    CardField("event_title", title),
                    CardField("event_time", ReminderText.time(context, beginMs), "Time"),
                    CardField("event_day", ReminderText.dayWord(beginMs), "Day"),
                ),
                buttons = listOf(CardButton("Add", CardAction.CONFIRM), CardButton("Cancel", CardAction.CANCEL)),
                callout = "you can say Yes, No, or Cancel",
            ),
            pending = Pending.AddEvent(title, beginMs, endMs),
            awaiting = CommandMatcher.Awaiting.CARD,
        )
    }

    private fun insertEvent(pending: Pending.AddEvent): Outcome {
        val calendarId = writableCalendarId() ?: return answer("I don't have a calendar to add that to.")
        return runCatching {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, pending.title)
                put(CalendarContract.Events.DTSTART, pending.beginMs)
                put(CalendarContract.Events.DTEND, pending.endMs)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            Diagnostics.add("cortana", "calendar event inserted: $uri")
            answer("Added to your calendar.")
        }.getOrElse {
            Diagnostics.add("cortana", "calendar insert failed: $it")
            answer("I couldn't add that.")
        }
    }

    private fun deleteEvent(title: String): Outcome {
        val event = calendarEvents(System.currentTimeMillis(), 30L * 86_400_000L)
            .firstOrNull { it.second.equals(title, ignoreCase = true) }
            ?: calendarEvents(System.currentTimeMillis(), 30L * 86_400_000L)
                .firstOrNull { it.second.contains(title, ignoreCase = true) }
            ?: return answer("I don't see $title on your calendar.")
        return Outcome(
            "Delete this event?",
            Card(
                CardKind.DELETE_CONFIRM, "Delete this event?", caption = "Event",
                fields = listOf(CardField("event_title", event.second)),
                buttons = listOf(CardButton("Delete", CardAction.CONFIRM), CardButton("Cancel", CardAction.CANCEL)),
                callout = "you can say Yes, No, or Cancel",
            ),
            pending = Pending.DeleteEvent(event.first, event.second),
            awaiting = CommandMatcher.Awaiting.CARD,
        )
    }

    private fun removeEvent(pending: Pending.DeleteEvent): Outcome = runCatching {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, pending.eventId)
        val rows = context.contentResolver.delete(uri, null, null)
        Diagnostics.add("cortana", "calendar event ${pending.eventId} deleted rows=$rows")
        answer("Deleted.")
    }.getOrElse { answer("I couldn't delete that.") }

    private fun calendarToday(): Outcome {
        val events = calendarEvents(System.currentTimeMillis(), 86_400_000L)
        if (events.isEmpty()) return answer("You have nothing on your calendar today.")
        val names = events.map { it.second }
        val spoken = "You have " + when (names.size) {
            1 -> names.first()
            2 -> "${names[0]} and ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        } + "."
        return Outcome(spoken, Card(CardKind.ANSWER, spoken, caption = "Calendar", body = names))
    }

    private fun playMusic(query: String?): Outcome {
        // Over the keyguard nothing may show, so music goes to a media session, never to an activity.
        val controller = activeMusicController()
        if (query == null) {
            if (controller != null) {
                controller.transportControls.play()
                return answer("Playing.")
            }
            if (LockGate.locked(context)) return unlockCard(Request.PlayMusic(null))
            return launchSlot(Slot.MUSIC, "Playing music.")
        }
        if (controller != null) {
            controller.transportControls.playFromSearch(query, null)
            return answer("Playing $query.")
        }
        // "A Music slot app offering neither shows Unlock to continue" (Decisions).
        if (LockGate.locked(context)) return unlockCard(Request.PlayMusic(query))
        val entry = slotApp(Slot.MUSIC) ?: return answer("Choose a Music app in Start settings first.")
        val intent = context.packageManager.getLaunchIntentForPackage(entry.component.packageName)
            ?: return answer("I couldn't open ${entry.label}.")
        host.launch(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return Outcome("Playing $query.", null, close = true)
    }

    /**
     * The session the notification listener already authorises us to see (phase 01's listener component
     * is what `MediaSessionManager.getActiveSessions` needs).
     */
    private fun activeMusicController(): MediaController? = runCatching {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, TileNotificationListener::class.java))
            .firstOrNull { it.playbackState != null || it.metadata != null }
    }.onFailure { Diagnostics.add("cortana", "media sessions unreadable: $it") }.getOrNull()

    private fun directions(destination: String): Outcome {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        slotApp(Slot.MAPS)?.let { intent.setPackage(it.component.packageName) }
        if (context.packageManager.resolveActivity(intent, 0) == null) {
            return answer("Choose a Maps app in Start settings first.")
        }
        host.launch(intent)
        return Outcome("Here are directions to $destination.", null, close = true)
    }

    private fun takePhoto(): Outcome = launchSlot(Slot.CAMERA, "Opening the camera.")

    private fun takeNote(text: String?): Outcome {
        val notes = prefs.settings.value.notesApp
            ?: return answer("Choose a Notes app in Cortana's settings first.")
        val intent = context.packageManager.getLaunchIntentForPackage(notes.packageName)
            ?: return answer("I couldn't open your notes app.")
        // A note with words goes through ACTION_SEND so the app receives the text; a bare "take a note"
        // just opens it, because there is nothing to hand over.
        val send = if (text.isNullOrBlank()) intent else Intent(Intent.ACTION_SEND)
            .setPackage(notes.packageName).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val usable = if (context.packageManager.resolveActivity(send, 0) != null) send else intent
        host.launch(usable.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return Outcome(if (text.isNullOrBlank()) "Opening your notes." else "Noted.", null, close = true)
    }

    private fun weather(): Outcome {
        val report = WeatherFeed.state.value.report
            ?: return answer("I don't have any weather yet.")
        val word = WmoCodes.word(report.current.code, report.current.isDay)
        val degrees = Math.round(report.current.temperature)
        val place = report.place?.let { " in $it" } ?: ""
        val ageMs = System.currentTimeMillis() - report.fetchedAtMs
        // "says how old it is when offline" (Decisions, A11): the age is spoken only once it is stale, so
        // a fresh reading is not cluttered with a timestamp.
        val age = if (ageMs >= WeatherFeed.STALE_AFTER_MS) " That's from ${ageWords(ageMs)}." else ""
        val spoken = "It's $degrees degrees and ${word.lowercase()}$place.$age"
        return Outcome(spoken, Card(CardKind.ANSWER, spoken, caption = "Weather"))
    }

    // ---------------- locked ----------------

    private fun unlockCard(request: Request): Outcome = Outcome(
        "Unlock your phone to continue.",
        Card(
            kind = CardKind.UNLOCK,
            title = "Unlock to continue",
            caption = LockGate.restate(request),
            buttons = listOf(CardButton("Unlock", CardAction.UNLOCK)),
        ),
        pending = Pending.Locked(request),
    )

    // ---------------- helpers ----------------

    private fun answer(spoken: String) = Outcome(spoken, Card(CardKind.ANSWER, spoken))

    private fun notUnderstoodCard(title: String) = Card(CardKind.NOT_UNDERSTOOD, title)

    private fun slotApp(slot: Slot): AppEntry? {
        val catalog = AppCatalog.get(context)
        return SlotResolver(context, catalog).resolve(slot, LayoutStore.get(context).layout.value.explicitSlots)
    }

    private fun launchSlot(slot: Slot, spoken: String): Outcome {
        val entry = slotApp(slot) ?: return answer("Choose a ${slot.label} app in Start settings first.")
        host.launchApp(entry)
        return Outcome(spoken, null, close = true)
    }

    private fun writableCalendarId(): Long? = runCatching {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { cursor ->
            var first: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                if (first == null) first = id
                if (cursor.getInt(1) == 1) return@use id
            }
            first
        }
    }.onFailure { Diagnostics.add("cortana", "calendar query failed: $it") }.getOrNull()

    /** (event id, title) for events starting inside [windowMs] from [fromMs]. */
    private fun calendarEvents(fromMs: Long, windowMs: Long): List<Pair<Long, String>> = runCatching {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(fromMs.toString()).appendPath((fromMs + windowMs).toString()).build()
        context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val title = cursor.getString(1) ?: continue
                    add(cursor.getLong(0) to title)
                }
            }
        }.orEmpty()
    }.onFailure { Diagnostics.add("cortana", "calendar instances failed: $it") }.getOrDefault(emptyList())

    private fun ageWords(ageMs: Long): String {
        val hours = ageMs / 3_600_000L
        return when {
            hours < 1 -> "${ageMs / 60_000L} minutes ago"
            hours < 24 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
            else -> "${hours / 24} day${if (hours / 24 == 1L) "" else "s"} ago"
        }
    }

    private fun durationWords(seconds: Int): String = when {
        seconds % 3600 == 0 && seconds >= 3600 -> "${seconds / 3600} hour${if (seconds == 3600) "" else "s"}"
        seconds % 60 == 0 -> "${seconds / 60} minute${if (seconds == 60) "" else "s"}"
        else -> "$seconds seconds"
    }

    /**
     * "Add more" carries the message so far while the next utterance is captured. It is a [Pending] so
     * the session's one pending slot keeps holding exactly one thing (H12's rule).
     */
    data class AddingTo(val await: Pending.AwaitMessage, val existing: String) : Pending

    private companion object {
        val DATE_FORMAT = SimpleDateFormat("EEEE, MMMM d", Locale.US)
    }
}
