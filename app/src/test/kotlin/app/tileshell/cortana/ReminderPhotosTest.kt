package app.tileshell.cortana

import app.tileshell.cortana.reminders.ReminderPhotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * L13-1 (review/2026-09-25-L13-1-fix-plan.md, Q2 "(b)"): a reminder's photo is a private copy in one folder, and only
 * a copy in that folder is ever deleted — a gallery URI, a path outside the folder or a `..` walk out of it is left
 * alone, because the URI string comes from the reminder store and deleting whatever it names would be a file-deletion
 * primitive.
 */
class ReminderPhotosTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun folder() = File(tmp.root, "files/${ReminderPhotos.FOLDER}").apply { mkdirs() }

    private fun copy(folder: File, name: String) = File(folder, name).apply { writeText("jpeg bytes") }

    @Test
    fun `a copy's uri names its file in the folder`() {
        val folder = folder()
        val file = copy(folder, "a1")
        val uri = ReminderPhotos.uriFor(file)
        assertTrue(uri, uri.startsWith("file:///"))
        assertEquals(file.canonicalFile, ReminderPhotos.copyFile(folder, uri))
    }

    @Test
    fun `nothing outside the folder is a copy`() {
        val folder = folder()
        val store = File(folder.parentFile, "cortana_reminders.json").apply { writeText("{}") }
        val elsewhere = File(tmp.newFolder("other"), "a1").apply { writeText("x") }
        assertNull("a gallery uri", ReminderPhotos.copyFile(folder, "content://media/picker/0/com.android.providers.media.photopicker/media/12"))
        assertNull("a walk out of the folder", ReminderPhotos.copyFile(folder, "file://${folder.absolutePath}/../cortana_reminders.json"))
        assertNull("another folder", ReminderPhotos.copyFile(folder, ReminderPhotos.uriFor(elsewhere)))
        assertNull("the folder itself", ReminderPhotos.copyFile(folder, ReminderPhotos.uriFor(folder)))
        assertNull("no photo", ReminderPhotos.copyFile(folder, null))
        assertTrue(store.exists())
    }

    @Test
    fun `release deletes a copy and nothing else`() {
        val folder = folder()
        val file = copy(folder, "a1")
        val store = File(folder.parentFile, "cortana_reminders.json").apply { writeText("{}") }
        assertFalse(ReminderPhotos.release(folder, "file://${folder.absolutePath}/../cortana_reminders.json"))
        assertTrue(store.exists())
        assertTrue(ReminderPhotos.release(folder, ReminderPhotos.uriFor(file)))
        assertFalse(file.exists())
        assertFalse("a second release finds nothing", ReminderPhotos.release(folder, ReminderPhotos.uriFor(file)))
    }

    @Test
    fun `the sweep deletes the copies no reminder references and keeps the rest`() {
        val folder = folder()
        val kept = copy(folder, "kept")
        val orphan = copy(folder, "orphan")
        val deleted = ReminderPhotos.sweep(folder, listOf(ReminderPhotos.uriFor(kept), "content://media/external/images/media/3"))
        assertEquals(listOf("orphan"), deleted)
        assertTrue(kept.exists())
        assertFalse(orphan.exists())
    }

    @Test
    fun `a sweep with no folder yet does nothing`() {
        assertEquals(emptyList<String>(), ReminderPhotos.sweep(File(tmp.root, "missing"), emptyList()))
    }
}
