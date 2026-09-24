package app.tileshell.cortana.action

import android.app.KeyguardManager
import android.content.Context
import app.tileshell.cortana.match.Request

/**
 * PQ3, Jeremy: "(a)". While the keyguard is locked Cortana runs time, weather, alarms, timers and music
 * directly; calls, texts, reading the calendar, reminders and opening apps show "Unlock to continue" and
 * finish the same request after unlock.
 *
 * The gate lives in the ACTION layer, not the matcher (Decisions "Locked commands"), so phase 08's
 * action calls inherit it exactly as they inherit the confirmation flow. A command is understood and
 * then refused — never quietly not understood because the phone is locked.
 */
object LockGate {

    fun locked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /**
     * Whether [request] may run with the keyguard up.
     *
     * The rule is what the command touches, not how harmless it sounds: anything that opens an app or
     * reads or writes personal data is gated. Alarms and timers are allowed because they are set
     * in-process in the shell's own Alarms & Clock (phase 15 Q1 A, T15-6), and music because it goes
     * to a media session, so neither has to show an activity over the keyguard.
     */
    fun allowedWhileLocked(request: Request): Boolean = when (request) {
        is Request.TimeQuery, is Request.DateQuery, is Request.Weather -> true
        is Request.SetAlarm, is Request.SetTimer -> true
        is Request.PlayMusic -> true
        // Phase 15 T15-2: deterministic, no personal data, opens nothing.
        is Request.Arithmetic -> true
        // Answers to a card that is already showing are not themselves requests to gate.
        is Request.Answer -> true
        is Request.NotUnderstood, is Request.Silence -> true
        is Request.CallContact, is Request.TextContact -> false
        is Request.WhatsOnMyCalendar, is Request.AddCalendarEvent, is Request.DeleteCalendarEvent -> false
        is Request.SetReminder, is Request.DeleteReminder -> false
        is Request.OpenApp, is Request.Directions, is Request.TakePhoto, is Request.TakeNote -> false
        is Request.SavePlaceHere -> false
    }

    /** The caption the "Unlock to continue" card restates the request with (H12). */
    fun restate(request: Request): String = when (request) {
        is Request.OpenApp -> "Open ${request.name}"
        is Request.CallContact -> "Call ${request.name}"
        is Request.TextContact -> "Text ${request.name}"
        is Request.WhatsOnMyCalendar -> "What's on your calendar"
        is Request.AddCalendarEvent -> "Add ${request.title} to your calendar"
        is Request.DeleteCalendarEvent -> "Delete ${request.title}"
        is Request.SetReminder -> "Remind you to ${request.text}"
        is Request.DeleteReminder -> "Delete the reminder to ${request.text}"
        is Request.Directions -> "Directions to ${request.destination}"
        is Request.TakePhoto -> "Take a photo"
        is Request.TakeNote -> "Take a note"
        is Request.SavePlaceHere -> "Save this place as ${request.name}"
        else -> "That"
    }
}
