package app.tileshell.cortana

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import app.tileshell.brand.Brand
import app.tileshell.cortana.reminders.PersonTriggers
import app.tileshell.cortana.reminders.PlaceTriggers
import app.tileshell.cortana.speech.SpeechClient
import app.tileshell.onboarding.RowState

/**
 * Cortana's own health rows (phase 03 build task 8; review R3T-m13). They live on Cortana's Settings
 * page rather than in phase 01's Setup checklist, because they are about the assistant, not about Start.
 *
 * Every row is a real read of the live state, never a remembered one — a permission revoked from
 * Android's own Settings has to turn the row red the next time the page is looked at.
 */
data class CortanaRow(
    val id: String,
    val title: String,
    val state: RowState,
    val detail: String,
    /** The permissions this row grants when it is tapped; empty when the row opens something else. */
    val permissions: List<String> = emptyList(),
)

object CortanaChecklist {

    fun rows(context: Context): List<CortanaRow> {
        val status = SpeechStatus.parse(SpeechClient.status())
        val alarms = context.getSystemService(AlarmManager::class.java)
        return listOf(
            CortanaRow(
                "assistant", "Default assistant",
                if (CortanaService.roleHeld(context)) RowState.GRANTED else RowState.MISSING,
                "The side key and the assist gesture open ${Brand.ASSISTANT_NAME}",
            ),
            permissionRow(context, "microphone", "Microphone", "Speak your requests", Manifest.permission.RECORD_AUDIO),
            permissionRow(context, "contacts", "Contacts", "Call and text people by name", Manifest.permission.READ_CONTACTS),
            CortanaRow(
                "calendar", "Calendar",
                when {
                    granted(context, Manifest.permission.READ_CALENDAR) && granted(context, Manifest.permission.WRITE_CALENDAR) -> RowState.GRANTED
                    granted(context, Manifest.permission.READ_CALENDAR) -> RowState.PARTIAL
                    else -> RowState.MISSING
                },
                "Read what's on your calendar and add to it",
                listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            ),
            permissionRow(context, "sms_send", "Send texts", "Send a text after reading it back", Manifest.permission.SEND_SMS),
            permissionRow(context, "call_phone", "Phone calls", "Place a call after confirming", Manifest.permission.CALL_PHONE),
            CortanaRow(
                "exact_alarms", "Exact alarms",
                if (alarms.canScheduleExactAlarms()) RowState.GRANTED else RowState.MISSING,
                // USE_EXACT_ALARM is granted at install for a sideloaded app; no Play policy applies (F1-m7).
                if (alarms.canScheduleExactAlarms()) "Granted at install" else "Reminders will be a few minutes late",
            ),
            CortanaRow(
                "background_location", "Location, all the time",
                when {
                    PlaceTriggers.granted(context) && PlaceTriggers.locationEnabled(context) -> RowState.GRANTED
                    granted(context, Manifest.permission.ACCESS_FINE_LOCATION) -> RowState.PARTIAL
                    else -> RowState.MISSING
                },
                "Place reminders fire when you arrive",
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            ),
            permissionRow(
                context, "call_log", "Call log",
                "Person reminders fire after you talk", Manifest.permission.READ_CALL_LOG,
            ),
            permissionRow(
                context, "sms_read", "Read texts",
                "Person reminders fire after you text", Manifest.permission.READ_SMS,
            ),
            CortanaRow(
                "models", "Speech files",
                when {
                    status.asrPresent && status.ttsPresent && status.espeakOk -> RowState.GRANTED
                    status.asrPresent || status.ttsPresent -> RowState.PARTIAL
                    else -> RowState.MISSING
                },
                buildString {
                    append(if (status.asrPresent) "Recognition ready" else "Recognition missing")
                    append(" · ")
                    append(if (status.ttsPresent) "Voice ready" else "Voice missing")
                    if (!status.espeakOk) append(" · pronunciation data needs re-extracting")
                },
            ),
            CortanaRow(
                "service", "Assistant service",
                if (CortanaService.serviceReady) RowState.GRANTED else RowState.MISSING,
                if (CortanaService.serviceReady) "Running" else "Not running",
            ),
            CortanaRow(
                "speech_process", "Speech engine",
                when (SpeechClient.connection.value) {
                    SpeechClient.Connection.BOUND -> RowState.GRANTED
                    SpeechClient.Connection.BINDING -> RowState.PARTIAL
                    SpeechClient.Connection.UNBOUND -> RowState.MISSING
                },
                when {
                    status.asrLoaded || status.ttsLoaded -> "Loaded, ${status.rssBytes / 1_048_576} MB"
                    SpeechClient.connection.value == SpeechClient.Connection.BOUND -> "Connected, models not loaded"
                    else -> "Not connected"
                },
            ),
            CortanaRow(
                "person_triggers", "Person reminders",
                if (PersonTriggers.granted(context)) RowState.GRANTED else RowState.MISSING,
                if (PersonTriggers.granted(context)) "Watching calls and texts"
                else "Needs the call log and text reading above",
            ),
        )
    }

    private fun permissionRow(context: Context, id: String, title: String, detail: String, permission: String) =
        CortanaRow(
            id, title,
            if (granted(context, permission)) RowState.GRANTED else RowState.MISSING,
            detail, listOf(permission),
        )

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Who currently holds the assistant role, so the notice can say so (H30).
     *
     * `RoleManager.getRoleHolders` is a system API an ordinary app cannot call, but the chosen assistant
     * is readable from Secure settings, which is what Android's own Settings screen shows. Null when
     * nothing is set.
     */
    fun assistantRoleHolder(context: Context): String? {
        val raw = Settings.Secure.getString(context.contentResolver, "assistant")?.takeIf { it.isNotBlank() }
            ?: Settings.Secure.getString(context.contentResolver, "voice_interaction_service")?.takeIf { it.isNotBlank() }
            ?: return null
        val packageName = ComponentName.unflattenFromString(raw)?.packageName ?: raw
        if (packageName == context.packageName) return null
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }
}

/** The `key=value` lines [SpeechClient.status] returns, as facts the checklist can read. */
data class SpeechStatus(
    val asrPresent: Boolean = false,
    val ttsPresent: Boolean = false,
    val espeakOk: Boolean = false,
    val asrLoaded: Boolean = false,
    val ttsLoaded: Boolean = false,
    val asrBytes: Long = 0,
    val ttsBytes: Long = 0,
    val rssBytes: Long = 0,
    val idleReleaseInMs: Long = -1,
    val lastError: String = "",
) {
    companion object {
        fun parse(raw: String): SpeechStatus {
            val map = raw.lineSequence()
                .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { it[0].trim() to it[1].trim() }
            fun flag(key: String) = map[key]?.equals("true", ignoreCase = true) == true
            fun number(key: String) = map[key]?.toLongOrNull() ?: 0L
            return SpeechStatus(
                asrPresent = flag("asr_present"),
                ttsPresent = flag("tts_present"),
                espeakOk = flag("espeak_ok"),
                asrLoaded = flag("asr_loaded"),
                ttsLoaded = flag("tts_loaded"),
                asrBytes = number("asr_bytes"),
                ttsBytes = number("tts_bytes"),
                rssBytes = number("rss_bytes"),
                idleReleaseInMs = map["idle_release_in_ms"]?.toLongOrNull() ?: -1L,
                lastError = map["last_error"].orEmpty(),
            )
        }
    }
}
