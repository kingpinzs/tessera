package app.tileshell.clock

import app.tileshell.BuildConfig
import app.tileshell.brand.AlarmSounds
import app.tileshell.files.FilesProvider
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
            assertEquals(authority, AlarmSound.DEFAULT, AlarmRingtoneRules.fromApi("content://$authority/x", null).sound)
        }
    }

    @Test fun `what the editor is handed from the API is weighed as for a caller nobody can be asked about`() {
        assertEquals(AlarmSound(AlarmSound.Kind.TONE, "content://media/internal/audio/media/7", null), AlarmRingtoneRules.fromApi("content://media/internal/audio/media/7", null).sound)
        assertEquals(AlarmSound.DEFAULT, AlarmRingtoneRules.fromApi("content://media/external/audio/media/41", null).sound)
        assertEquals(AlarmSound.DEFAULT, AlarmRingtoneRules.fromApi("file:///storage/emulated/0/a.mp3", null).sound)
        assertEquals(AlarmSound.DEFAULT, AlarmRingtoneRules.fromApi(null, null).sound)
    }
}
