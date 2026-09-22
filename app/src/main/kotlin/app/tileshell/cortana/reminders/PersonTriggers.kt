package app.tileshell.cortana.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.CallLog
import android.provider.Telephony
import app.tileshell.cortana.action.Contacts
import app.tileshell.diag.Diagnostics

/**
 * Person reminders — "remind me next time I talk to Mom" (R2D-13 ruling A, direction ruled A 2026-09-17).
 *
 * Any real contact counts, in either direction: an outgoing call, an **answered** incoming call, or a
 * text sent or received. A missed or declined call does not. Reading the call log and the SMS provider
 * needs `READ_CALL_LOG` and `READ_SMS`, hard-restricted permissions an app with no dialer or SMS role
 * gets only because `adb install` allow-lists them (the shell is never installed with
 * `--restrict-permissions`); the checklist has a row for each.
 *
 * A call counts only once it ends, because Telecom writes the call-log entry at disconnect — which is
 * exactly why this watches the providers rather than the call state.
 *
 * These are ordinary content observers, not an "any change" trigger: each pass reads only the rows newer
 * than the watermark it recorded last time, so re-registering after a reboot cannot re-fire the history.
 */
object PersonTriggers {

    private const val PREFS = "cortana_person_triggers"
    private const val KEY_CALL_WATERMARK = "call_date"
    private const val KEY_SMS_WATERMARK = "sms_date"

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var callObserver: ContentObserver? = null
    private var smsObserver: ContentObserver? = null

    /** Call-log types that count as talking (ruling A): an answered incoming call, and any outgoing call. */
    private val COUNTING_CALL_TYPES = setOf(CallLog.Calls.INCOMING_TYPE, CallLog.Calls.OUTGOING_TYPE)

    @Synchronized
    fun rearm(context: Context) {
        val app = context.applicationContext
        val wanted = ReminderStore.get(app).reminders.value.any { !it.completed && it.kind == ReminderKind.PERSON }
        if (!wanted) {
            stop(app)
            Diagnostics.add("person", "rearm: no person reminders, observers off")
            return
        }
        if (!granted(app)) {
            stop(app)
            Diagnostics.add("person", "rearm: person reminders present but READ_CALL_LOG/READ_SMS missing; they cannot fire")
            return
        }
        if (callObserver != null) {
            Diagnostics.add("person", "rearm: observers already running")
            return
        }
        val looperThread = HandlerThread("cortana-person").also { it.start() }
        thread = looperThread
        val h = Handler(looperThread.looper)
        handler = h
        // The watermarks start at "now" the first time, so reminders never fire on calls and texts that
        // happened before the reminder existed.
        primeWatermarks(app)
        callObserver = object : ContentObserver(h) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = checkCalls(app)
        }.also { app.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, it) }
        smsObserver = object : ContentObserver(h) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = checkSms(app)
        }.also { app.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, it) }
        Diagnostics.add("person", "rearm: call-log and SMS observers registered")
    }

    @Synchronized
    fun stop(context: Context) {
        callObserver?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        smsObserver?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        callObserver = null
        smsObserver = null
        thread?.quitSafely()
        thread = null
        handler = null
    }

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun primeWatermarks(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (!p.contains(KEY_CALL_WATERMARK)) p.edit().putLong(KEY_CALL_WATERMARK, now).apply()
        if (!p.contains(KEY_SMS_WATERMARK)) p.edit().putLong(KEY_SMS_WATERMARK, now).apply()
    }

    private fun checkCalls(context: Context) {
        val p = prefs(context)
        val since = p.getLong(KEY_CALL_WATERMARK, System.currentTimeMillis())
        var newest = since
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                "${CallLog.Calls.DATE} > ?", arrayOf(since.toString()),
                "${CallLog.Calls.DATE} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val number = cursor.getString(0).orEmpty()
                    val type = cursor.getInt(1)
                    newest = maxOf(newest, cursor.getLong(2))
                    if (type !in COUNTING_CALL_TYPES) {
                        Diagnostics.add("person", "call from $number type=$type does not count as talking")
                        continue
                    }
                    fireFor(context, number, "call type=$type")
                }
            }
        }.onFailure { Diagnostics.add("person", "call-log read failed: $it") }
        if (newest > since) p.edit().putLong(KEY_CALL_WATERMARK, newest).apply()
    }

    private fun checkSms(context: Context) {
        val p = prefs(context)
        val since = p.getLong(KEY_SMS_WATERMARK, System.currentTimeMillis())
        var newest = since
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.TYPE, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val address = cursor.getString(0).orEmpty()
                    val type = cursor.getInt(1)
                    newest = maxOf(newest, cursor.getLong(2))
                    // Both directions count (ruling A): inbox is received, sent is sent. A draft or a
                    // failed outbox row is neither.
                    if (type != Telephony.Sms.MESSAGE_TYPE_INBOX && type != Telephony.Sms.MESSAGE_TYPE_SENT) continue
                    fireFor(context, address, "sms type=$type")
                }
            }
        }.onFailure { Diagnostics.add("person", "sms read failed: $it") }
        if (newest > since) p.edit().putLong(KEY_SMS_WATERMARK, newest).apply()
    }

    /**
     * Any of the contact's numbers matches (edge case "contact with several numbers"), which is what
     * PhoneLookup does for us: it resolves the number to the contact that owns it, whichever number it was.
     * A number belonging to nobody resolves to nothing and fires nothing.
     */
    private fun fireFor(context: Context, number: String, why: String) {
        if (number.isBlank()) return
        val lookupKey = Contacts.lookupKeyForNumber(context, number)
        if (lookupKey == null) {
            Diagnostics.add("person", "$why from $number is not a contact")
            return
        }
        val store = ReminderStore.get(context)
        val due = store.reminders.value.filter {
            !it.completed && it.kind == ReminderKind.PERSON && it.contactLookupKey == lookupKey
        }
        if (due.isEmpty()) return
        due.forEach { ReminderScheduler.fire(context, it.id, why) }
    }
}
