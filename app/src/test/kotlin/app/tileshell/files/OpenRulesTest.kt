package app.tileshell.files

import app.tileshell.files.OpenRules.MediaRow
import app.tileshell.files.OpenRules.Route
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Open-with's pure rules (phase 18 build task 4): the route, the type an opener is given, and the chooser pick's path. */
class OpenRulesTest {
    private val volumes = listOf(FileVolume(FilePaths.PRIMARY, "/storage/emulated/0"), FileVolume("3842-1609", "/storage/3842-1609"))
    private val lexical: (String) -> String? = { Paths.get(it).normalize().toString() }

    @Test
    fun `an image goes to the viewer and a video to the player, with the row when there is one`() {
        assertEquals(Route.Viewer(7), OpenRules.route(FileKind.IMAGE, MediaRow(7), musicReadsLibrary = true))
        assertEquals(Route.Viewer(null), OpenRules.route(FileKind.IMAGE, null, musicReadsLibrary = true))
        assertEquals(Route.Player(9), OpenRules.route(FileKind.VIDEO, MediaRow(9), musicReadsLibrary = false))
        assertEquals(Route.Player(null), OpenRules.route(FileKind.VIDEO, null, musicReadsLibrary = false))
    }

    @Test
    fun `audio goes to Music by id only when Music's library holds the row`() {
        assertEquals(Route.MusicId(4), OpenRules.route(FileKind.AUDIO, MediaRow(4), musicReadsLibrary = true))
        // No row (a nomedia folder, an unscanned file): the URI form.
        assertEquals(Route.MusicUri, OpenRules.route(FileKind.AUDIO, null, musicReadsLibrary = true))
        // A recording has a row, and Music skips it.
        assertEquals(Route.MusicUri, OpenRules.route(FileKind.AUDIO, MediaRow(4, recording = true), musicReadsLibrary = true))
        // Without its permission Music's library is empty.
        assertEquals(Route.MusicUri, OpenRules.route(FileKind.AUDIO, MediaRow(4), musicReadsLibrary = false))
    }

    @Test
    fun `every other kind goes to the chooser, whatever a query found`() {
        for (kind in listOf(FileKind.TEXT, FileKind.ARCHIVE, FileKind.OTHER, FileKind.FOLDER)) {
            assertEquals("$kind", Route.Chooser, OpenRules.route(kind, null, musicReadsLibrary = true))
            assertEquals("$kind", Route.Chooser, OpenRules.route(kind, MediaRow(1), musicReadsLibrary = true))
        }
    }

    @Test
    fun `the viewer and the player get a type of their own family`() {
        assertEquals("image/png", OpenRules.mime(FileKind.IMAGE, "image/png"))
        assertEquals("image/*", OpenRules.mime(FileKind.IMAGE, null))
        assertEquals("image/*", OpenRules.mime(FileKind.IMAGE, "application/octet-stream"))
        assertEquals("video/mp4", OpenRules.mime(FileKind.VIDEO, "video/mp4"))
        // `.ts` is a video by its kind and something else to the platform's map.
        assertEquals("video/*", OpenRules.mime(FileKind.VIDEO, "application/typescript"))
        assertEquals("audio/mpeg", OpenRules.mime(FileKind.AUDIO, "audio/mpeg"))
        assertEquals("audio/*", OpenRules.mime(FileKind.AUDIO, null))
    }

    @Test
    fun `any other file gets the platform's type, and the unknown type when it has none`() {
        assertEquals("text/plain", OpenRules.mime(FileKind.TEXT, "text/plain"))
        assertEquals("application/pdf", OpenRules.mime(FileKind.TEXT, "application/pdf"))
        assertEquals("application/octet-stream", OpenRules.mime(FileKind.OTHER, null))
        assertEquals("application/octet-stream", OpenRules.mime(FileKind.OTHER, " "))
    }

    @Test
    fun `MediaStore's volume name is external_primary or the uuid in lower case`() {
        assertEquals("external_primary", OpenRules.mediaVolumeName(FilePaths.PRIMARY))
        assertEquals("3842-1609", OpenRules.mediaVolumeName("3842-1609"))
        assertEquals("ab12-cd34", OpenRules.mediaVolumeName("AB12-CD34"))
    }

    @Test
    fun `a chooser pick names a canonical path under a mounted volume, with its volume`() {
        assertEquals("/storage/emulated/0/QA-Files/a.txt" to volumes[0], OpenRules.pickTarget("/storage/emulated/0/QA-Files/a.txt", volumes, lexical))
        assertEquals("/storage/3842-1609/a.txt" to volumes[1], OpenRules.pickTarget("/storage/3842-1609/a.txt", volumes, lexical))
    }

    @Test
    fun `a pick outside every volume, not canonical, a root, in the shell's folder or unresolvable adds nothing`() {
        assertNull(OpenRules.pickTarget(null, volumes, lexical))
        assertNull(OpenRules.pickTarget("", volumes, lexical))
        assertNull(OpenRules.pickTarget("/data/data/app.tileshell/files/files-recent.json", volumes, lexical))
        assertNull(OpenRules.pickTarget("/storage/emulated/0/../../data/data/app.tileshell/files/x", volumes, lexical))
        // Inside the volume, but not the form Files would have written.
        assertNull(OpenRules.pickTarget("/storage/emulated/0/QA-Files/../QA-Files/a.txt", volumes, lexical))
        assertNull(OpenRules.pickTarget("/storage/emulated/0", volumes, lexical))
        assertNull(OpenRules.pickTarget("/storage/emulated/0/.Tessera/bin/1-0-a.txt", volumes, lexical))
        assertNull(OpenRules.pickTarget("/storage/emulated/0/a.txt", volumes) { null })
        assertNull(OpenRules.pickTarget("/storage/emulated/0/a.txt", volumes) { throw java.io.IOException("no") })
        assertNull(OpenRules.pickTarget("/storage/9999-0000/a.txt", volumes, lexical))
    }
}
