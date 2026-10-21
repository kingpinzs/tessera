package app.tileshell.clock

import app.tileshell.BuildConfig
import app.tileshell.brand.AlarmSounds
import app.tileshell.clock.AlarmApiRules.Request
import app.tileshell.files.FilesProvider
import app.tileshell.media.FakeUriAccess
import app.tileshell.media.Platform
import app.tileshell.media.ProviderFacts
import app.tileshell.media.UriAccessPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ledger L18-2, the SINK's side: what the ring may open as an alarm's sound whatever the store holds. (The API's side —
 * what a ringtone from another app becomes — is `AlarmApiRulesTest`, through `AlarmApiRules.parse`.)
 */
class AlarmRingtoneRulesTest {
    @Test fun `the ring never opens a file URI, a path, a web address or anything but a content URI`() {
        for (uri in listOf(
            "file:///storage/emulated/0/Documents/private-memo.m4a",
            "file:///data/user/0/app.tileshell/files/anything",
            "file:/storage/emulated/0/a.mp3",
            "FILE:///storage/emulated/0/a.mp3",
            "File:///storage/emulated/0/a.mp3",
            "/storage/emulated/0/a.mp3",
            "storage/emulated/0/a.mp3",
            "",
            "http://example.com/a.mp3",
            "https://example.com/a.mp3",
            "rtsp://example.com/a",
            "android.resource://app.tileshell/raw/x",
            "CONTENT://media/internal/audio/media/7",
            " content://media/internal/audio/media/7",
            "content://media/internal/audio/media/7\n",
            "content://media/internal/audio/media/7 x",
            "content:/media/internal/audio/media/7",
            "content:media/internal/audio/media/7",
        )) {
            assertEquals("\"$uri\"", AlarmRingtoneRules.WHY_NOT_CONTENT, AlarmRingtoneRules.sinkRefusal(uri))
        }
    }

    @Test fun `the ring never opens one of the shell's own providers, however the authority is written`() {
        for (uri in listOf(
            "content://app.tileshell.files/root/storage/emulated/0/QA-Files/hidden/qa-hidden.mp3",
            "content://${FilesProvider.AUTHORITY}/root/storage/emulated/0/a.mp3",
            "content://app.tileshell.livetile/tiles",
            "content://app.tileshell.keyboardconfig/x",
            "content://app.tileshell/x",
            "content://app.tileshell.anything.new/x",
            "content://APP.TILESHELL.FILES/root/a.mp3",
            "content://App.TileShell.files/root/a.mp3",
            "content://app.tileshell.files",
            "content://app.tileshell.files?x",
            "content://app.tileshell.files\\root",
        )) {
            assertEquals(uri, AlarmRingtoneRules.WHY_SHELL, AlarmRingtoneRules.sinkRefusal(uri))
        }
        // A user id, an escape, or no authority at all: not a provider plainly named, so not opened either.
        for (uri in listOf("content://0@app.tileshell.files/root/a.mp3", "content://10@media/external/audio/media/4", "content://app.tileshell%2Efiles/root/a.mp3", "content:///root/a.mp3", "content://")) {
            assertEquals(uri, AlarmRingtoneRules.WHY_AUTHORITY, AlarmRingtoneRules.sinkRefusal(uri))
        }
    }

    @Test fun `every sound the shell's own editor offers still rings`() {
        // "Pick from ringtones" (RingtoneManager's rows) and "Pick from my music" (MediaStore's external audio rows).
        for (uri in listOf(
            "content://media/internal/audio/media/7",
            "content://media/internal/audio/media/27?title=Argon&canonical=1",
            "content://media/external/audio/media/41",
            "content://media/external_primary/audio/media/41",
            "content://media/1a2b-3c4d/audio/media/41",
            "content://settings/system/alarm_alert",
        )) {
            assertNull(uri, AlarmRingtoneRules.sinkRefusal(uri))
        }
        // The shell's own sounds are ids, resolved to their file before the ring asks about a URI at all.
        for (sound in AlarmSounds.all) assertEquals(sound, AlarmSounds.byUri(AlarmSounds.uri(sound.id)))
        // An app whose name merely begins like the shell's is another app.
        assertNull(AlarmRingtoneRules.sinkRefusal("content://app.tileshellx.files/a"))
        assertFalse(AlarmRingtoneRules.isShellAuthority("app.tileshellx"))
        assertFalse(AlarmRingtoneRules.isShellAuthority("com.app.tileshell.files"))
        assertTrue(AlarmRingtoneRules.isShellAuthority("app.tileshell"))
    }

    @Test fun `the shell's package and every provider authority its manifest declares are the rule's`() {
        assertEquals(BuildConfig.APPLICATION_ID, AlarmRingtoneRules.SHELL_PACKAGE)
        assertTrue(AlarmRingtoneRules.isShellAuthority(FilesProvider.AUTHORITY))
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.isFile }.readText()
        val authorities = Regex("android:authorities=\"([^\"]*)\"").findAll(manifest).flatMap { it.groupValues[1].split(';') }
            .map { it.replace("\${applicationId}", BuildConfig.APPLICATION_ID) }.toList()
        assertTrue("the manifest's providers were read: $authorities", authorities.size >= 3 && FilesProvider.AUTHORITY in authorities)
        for (authority in authorities) {
            assertTrue(authority, AlarmRingtoneRules.isShellAuthority(authority))
            assertEquals(authority, AlarmRingtoneRules.WHY_SHELL, AlarmRingtoneRules.sinkRefusal("content://$authority/x"))
            for (caller in listOf(AlarmRingtoneRules.Caller.Ask(null), AlarmRingtoneRules.Caller.AskedByHandler)) {
                assertEquals(authority, AlarmSound.DEFAULT, AlarmRingtoneRules.fromApi("content://$authority/x", caller).sound)
            }
        }
    }

    /**
     * Ledger L18-3: the handler kept a caller-readable external ringtone and the editor then dropped it when SKIP_UI was
     * absent — two rules. Phase 15's Decision (T15-37): "without `EXTRA_SKIP_UI` it opens the editor filled in, with it
     * the item is created". So the alarm the editor opens is the one SKIP_UI would have made, sound included, whatever
     * the platform answers about the caller: ONE rule weighs the ringtone, asked once, by the handler.
     */
    @Test fun `L18-3 a ringtone from the API is the same sound with SKIP_UI and in the editor without it`() {
        val row = "content://media/external/audio/media/41"
        val ports = listOf(
            "no port" to null,
            "nobody named, denied" to FakeUriAccess(),
            "the launch answer says it could read the row" to FakeUriAccess(launchAnswer = { uri, mode -> if (uri == row && mode == FakeUriAccess.READ) FakeUriAccess.YES else FakeUriAccess.NO }),
            "a named starter the provider lets read" to FakeUriAccess(launchedFrom = Platform.Said(FakeUriAccess.CALLER), providerAnswer = { uri, uid, _ -> if (uri == row && uid == FakeUriAccess.CALLER) FakeUriAccess.YES else FakeUriAccess.NO }),
            "the launch answer throws" to FakeUriAccess(launchAnswer = { _, _ -> Platform.Threw("IllegalArgumentException") }),
        )
        val ringtones = listOf(
            null, "", "silent", row, "content://media/external/audio/media/42", "content://media/1a2b-3c4d/audio/media/9", "content://media/internal/audio/media/7",
            "content://settings/system/alarm_alert", "content://settings/system/screen_brightness", "file:///storage/emulated/0/a.mp3",
            "content://app.tileshell.files/root/storage/emulated/0/a.mp3", "content://com.other.files/a.mp3", "content://media/external/images/media/41",
        )
        var keptExternal = 0
        for ((who, access) in ports) for (ringtone in ringtones) {
            val created = (parse(ringtone, skipUi = true, access) as Request.CreateAlarm).fields.sound
            val edit = (parse(ringtone, skipUi = false, access) as Request.EditAlarm).fields
            assertEquals("$ringtone, $who: the handler's two requests", created, edit.sound)
            // The bundle as AlarmApiActivity fills it (AlarmSoundWiringScanTest holds that form), sent by the shell itself.
            val bundle = mapOf(
                ClockIntents.API_KIND to "alarm", ClockIntents.API_HOUR to edit.hour, ClockIntents.API_MINUTE to edit.minute, ClockIntents.API_DAYS to IntArray(0),
                ClockIntents.API_MESSAGE to edit.message, ClockIntents.API_SOUND_KIND to edit.sound.kind.name, ClockIntents.API_SOUND_URI to edit.sound.uri,
            )
            val intent = mapOf(ClockIntents.EXTRA_API_EDIT to ExtrasPort { bundle[it] })
            val editor = ClockIntents.open(ExtrasPort { intent[it] }, FakeUriAccess.SHELL, FakeUriAccess.SHELL).alarm!!.sound
            assertEquals("$ringtone, $who: SKIP_UI made one sound, the editor shows another", created, editor)
            if (ringtone == row && editor.kind == AlarmSound.Kind.TONE) keptExternal++
        }
        // The row is kept, by both, exactly where the platform said the caller could read it.
        assertEquals(2, keptExternal)
    }

    @Test fun `L18-3 the editor's answer is the handler's - a row of the user's volume the handler kept, every other check made again`() {
        val row = "content://media/external/audio/media/41"
        val handler = AlarmRingtoneRules.Caller.AskedByHandler
        assertEquals(AlarmRingtoneRules.FromApi(AlarmSound(AlarmSound.Kind.TONE, row, null)), AlarmRingtoneRules.fromApi(row, handler))
        // The handler with nobody to ask keeps no such row.
        assertEquals(AlarmRingtoneRules.FromApi(AlarmSound.DEFAULT, AlarmRingtoneRules.WHY_CALLER), AlarmRingtoneRules.fromApi(row, AlarmRingtoneRules.Caller.Ask(null)))
        // The handler's answer stands in for the caller's question only: no other form becomes a sound in the editor.
        for (bad in listOf(
            "file:///storage/emulated/0/a.mp3", "/storage/emulated/0/a.mp3", "content://app.tileshell.files/root/storage/emulated/0/a.mp3", "content://0@media/external/audio/media/41",
            "content://com.other.files/a.mp3", "content://media/external/images/media/41", "content://media/external/audio/media/41#x", "content://settings/secure/android_id",
            "content://settings/system/screen_brightness", "http://example.com/a.mp3", "tessera-sound:classic", "content://media/internal/audio/media/7/../../../external/audio/media/9",
        )) {
            assertEquals(bad, AlarmSound.DEFAULT, AlarmRingtoneRules.fromApi(bad, handler).sound)
            assertEquals(bad, AlarmRingtoneRules.fromApi(bad, AlarmRingtoneRules.Caller.Ask(null)), AlarmRingtoneRules.fromApi(bad, handler))
        }
        for (system in listOf("content://media/internal/audio/media/7", "content://settings/system/alarm_alert")) {
            assertEquals(system, AlarmSound(AlarmSound.Kind.TONE, system, null), AlarmRingtoneRules.fromApi(system, handler).sound)
        }
        assertEquals(AlarmSound.DEFAULT, AlarmRingtoneRules.fromApi(null, handler).sound)
    }

    /**
     * The adversarial review's A6 (ledger L18-3): a guard broadened to keep any `content://settings/system/<name>` went
     * unnoticed (GATE-adversarial-pass2/mut.py's A6, not red). Only the three named default sounds are system sounds;
     * every other row of the system table is a setting, not a sound — the default, whoever asks and whatever the
     * platform says, with SKIP_UI and in the editor alike.
     */
    @Test fun `A6 only the three named system default sounds are kept from settings - never another system row`() {
        val yesToAll = FakeUriAccess(
            launchedFrom = Platform.Said(FakeUriAccess.CALLER), providers = mapOf("settings" to ProviderFacts(FakeUriAccess.CALLER, true)),
            providerAnswer = { _, _, _ -> FakeUriAccess.YES }, launchAnswer = { _, _ -> FakeUriAccess.YES },
        )
        val callers = listOf(AlarmRingtoneRules.Caller.Ask(null), AlarmRingtoneRules.Caller.Ask(FakeUriAccess()), AlarmRingtoneRules.Caller.Ask(yesToAll), AlarmRingtoneRules.Caller.AskedByHandler)
        for (name in listOf(
            "screen_brightness", "volume_alarm", "next_alarm_formatted", "dtmf_tone", "alarm_alert_cache", "ringtone_cache", "notification_sound_cache",
            "alarm_alert2", "ringtones", "ALARM_ALERT", "Ringtone", "alarm_alert/", "alarm_alert/7", "alarm_alert?x=1", "alarm_alert#x", "x/../alarm_alert", "",
        )) {
            val uri = "content://settings/system/$name"
            for (caller in callers) {
                assertEquals("$uri, $caller", AlarmRingtoneRules.FromApi(AlarmSound.DEFAULT, AlarmRingtoneRules.WHY_NOT_SOUND), AlarmRingtoneRules.fromApi(uri, caller))
            }
            for (skipUi in listOf(true, false)) {
                val r = parse(uri, skipUi, yesToAll)
                val sound = if (r is Request.CreateAlarm) r.fields.sound else (r as Request.EditAlarm).fields.sound
                assertEquals("$uri through the API", AlarmSound.DEFAULT, sound)
            }
        }
        for (name in listOf("alarm_alert", "ringtone", "notification_sound")) {
            val uri = "content://settings/system/$name"
            for (caller in callers) assertEquals("$uri, $caller", AlarmRingtoneRules.FromApi(AlarmSound(AlarmSound.Kind.TONE, uri, null)), AlarmRingtoneRules.fromApi(uri, caller))
        }
    }

    private fun parse(ringtone: String?, skipUi: Boolean, access: UriAccessPort?) =
        AlarmApiRules.parse(AlarmApiRules.ACTION_SET_ALARM, 6, 45, null, "Gym", skipUi, null, ringtone, null, access)
}
