package app.tileshell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Files' navigation rules (build task 2; Y3, r3 D12): the history, the breadcrumb, the extras, a row's kind. */
class FilesNavTest {
    private val root = "/storage/emulated/0"
    private val card = "/storage/3842-1609"
    private val labelOf: (String) -> String? = { if (it == FilePaths.PRIMARY) "This Device" else if (it == "3842-1609") "Virtual SD card" else null }
    private val rootOf: (String) -> String? = { if (it == FilePaths.PRIMARY) root else if (it == "3842-1609") card else null }
    private fun folder(path: String, uuid: String = FilePaths.PRIMARY) = FilesLocation.Folder(uuid, path)

    @Test
    fun `a straight descent goes back up one level at a time and then leaves`() {
        val h = FilesHistory(FilesLocation.Recent)
        h.go(folder(root))
        h.go(folder("$root/QA-Files"))
        h.go(folder("$root/QA-Files/sub"))
        assertEquals(folder("$root/QA-Files"), h.back())
        assertEquals(folder(root), h.back())
        assertEquals(FilesLocation.Recent, h.back())
        assertNull("an empty history: Back leaves the app", h.back())
    }

    @Test
    fun `after a breadcrumb jump Back returns to the folder left, not to its parent`() {
        val h = FilesHistory(folder(root))
        h.go(folder("$root/QA-Files"))
        h.go(folder("$root/QA-Files/sub"))
        h.go(folder("$root/QA-Files/sub/deep"))
        h.go(folder(root)) // files_crumb:0
        assertEquals(folder("$root/QA-Files/sub/deep"), h.back())
    }

    @Test
    fun `going to where one already is adds nothing to the history`() {
        val h = FilesHistory(FilesLocation.Recent)
        assertFalse(h.go(FilesLocation.Recent))
        assertEquals(0, h.depth)
        assertNull(h.back())
    }

    @Test
    fun `a page or path launch resets the history so one Back leaves`() {
        val h = FilesHistory(FilesLocation.Recent)
        h.go(folder(root))
        h.go(folder("$root/QA-Files/sub"))
        h.reset(folder("$root/Recordings"))
        assertEquals(folder("$root/Recordings"), h.current)
        assertNull(h.back())
    }

    @Test
    fun `the breadcrumb names the volume first and each folder after it`() {
        val crumbs = FilesNav.crumbs(folder("$root/QA-Files/sub"), labelOf, rootOf)
        assertEquals(listOf("This Device", "QA-Files", "sub"), crumbs.map { it.label })
        assertEquals(listOf(folder(root), folder("$root/QA-Files"), folder("$root/QA-Files/sub")), crumbs.map { it.location })
        assertEquals(listOf("Virtual SD card"), FilesNav.crumbs(folder(card, "3842-1609"), labelOf, rootOf).map { it.label })
        assertEquals(listOf("Recent"), FilesNav.crumbs(FilesLocation.Recent, labelOf, rootOf).map { it.label })
        assertEquals(listOf("Recycle Bin"), FilesNav.crumbs(FilesLocation.Bin, labelOf, rootOf).map { it.label })
    }

    @Test
    fun `a folder on a volume that is gone still has one crumb`() {
        assertEquals(listOf("dcim"), FilesNav.crumbs(folder("/storage/AAAA-0000/dcim", "AAAA-0000"), labelOf, rootOf).map { it.label })
    }

    @Test
    fun `up is the parent folder and nothing at a volume root or off a folder`() {
        assertEquals(folder("$root/QA-Files"), FilesNav.parent(folder("$root/QA-Files/sub"), rootOf))
        assertEquals(folder(root), FilesNav.parent(folder("$root/QA-Files"), rootOf))
        assertNull(FilesNav.parent(folder(root), rootOf))
        assertNull(FilesNav.parent(folder(card, "3842-1609"), rootOf))
        assertNull(FilesNav.parent(FilesLocation.Recent, rootOf))
        assertNull(FilesNav.parent(FilesLocation.Bin, rootOf))
    }

    @Test
    fun `a breadcrumb that fits is drawn whole and one that does not keeps its first and last segments`() {
        assertEquals(listOf(0, 1, 2), FilesNav.shownCrumbs(listOf(80f, 60f, 30f), separator = 16f, ellipsis = 12f, available = 252f))
        assertEquals(listOf(0, 4), FilesNav.shownCrumbs(listOf(80f, 60f, 30f, 36f, 50f), 16f, 12f, 252f))
        assertEquals("two segments never collapse", listOf(0, 1), FilesNav.shownCrumbs(listOf(200f, 200f), 16f, 12f, 252f))
    }

    @Test
    fun `the pane row of each location`() {
        assertEquals("recent", FilesNav.paneId(FilesLocation.Recent))
        assertEquals("device", FilesNav.paneId(folder("$root/QA-Files")))
        assertEquals("3842-1609", FilesNav.paneId(folder(card, "3842-1609")))
        assertEquals("bin", FilesNav.paneId(FilesLocation.Bin))
        assertNull(FilesNav.paneId(FilesLocation.Settings))
    }

    @Test
    fun `the extras open a folder only under a mounted volume`() {
        val bed = FilesBed()
        val volumes = bed.mounted.toList()
        val isDir: (String) -> Boolean = { File(it).isDirectory }
        val sub = bed.dir("QA-Files/sub")
        bed.file("QA-Files/a.txt")
        fun resolve(page: String?, path: String?) = FilesNav.resolve(page, path, volumes, bed.canonical, isDirectory = isDir)

        assertEquals(FilesOpen.None, resolve(null, null))
        assertEquals(FilesOpen.Go(FilesLocation.Folder(FilePaths.PRIMARY, sub.path)), resolve(null, sub.path))
        assertEquals("a path wins over a page", FilesOpen.Go(FilesLocation.Folder(FilePaths.PRIMARY, sub.path)), resolve("bin", sub.path))
        assertEquals("the volume's own root", FilesOpen.Go(FilesLocation.Folder(bed.card.uuid, bed.card.root)), resolve(null, bed.card.root))
        assertEquals(FilesOpen.Go(FilesLocation.Folder(FilePaths.PRIMARY, sub.path)), resolve(null, "${bed.primary.root}/QA-Files/../QA-Files/sub/"))

        assertTrue("the app's private files", resolve(null, bed.privateDir.path) is FilesOpen.Ignored)
        assertTrue("a traversal out of the volume", resolve(null, "${bed.primary.root}/../../data/data/app.tileshell/files") is FilesOpen.Ignored)
        assertTrue("a file is not a folder", resolve(null, "${bed.primary.root}/QA-Files/a.txt") is FilesOpen.Ignored)
        assertTrue("a folder that is not there", resolve(null, "${bed.primary.root}/nope") is FilesOpen.Ignored)
        val bin = bed.dir(".Tessera/bin")
        assertTrue("the shell's own folder is never browsable", resolve(null, bin.path) is FilesOpen.Ignored)
        val link = File(bed.root, "QA-Files/out")
        java.nio.file.Files.createSymbolicLink(link.toPath(), bed.privateDir.toPath())
        assertTrue("a symlink out of the volume", resolve(null, link.path) is FilesOpen.Ignored)
    }

    @Test
    fun `the page extra names Recent, the bin, This Device or a mounted volume`() {
        val bed = FilesBed()
        val isDir: (String) -> Boolean = { File(it).isDirectory }
        fun resolve(page: String?) = FilesNav.resolve(page, null, bed.mounted.toList(), bed.canonical, isDirectory = isDir)
        assertEquals(FilesOpen.Go(FilesLocation.Recent), resolve("recent"))
        assertEquals(FilesOpen.Go(FilesLocation.Bin), resolve("bin"))
        assertEquals(FilesOpen.Go(FilesLocation.Folder(FilePaths.PRIMARY, bed.primary.root)), resolve("device"))
        assertEquals(FilesOpen.Go(FilesLocation.Folder(bed.card.uuid, bed.card.root)), resolve(FilesIntents.volumePage(bed.card.uuid)))
        assertTrue(resolve("volume:AAAA-0000") is FilesOpen.Ignored)
        assertTrue(resolve("root") is FilesOpen.Ignored)
        bed.mounted.remove(bed.card)
        assertTrue("a card that was pulled", resolve(FilesIntents.volumePage(bed.card.uuid)) is FilesOpen.Ignored)
    }

    @Test
    fun `a row's kind comes from its name`() {
        assertEquals(FileKind.FOLDER, FileKind.of("photos.png", isDirectory = true))
        assertEquals(FileKind.IMAGE, FileKind.of("IMG_0001.JPG", false))
        assertEquals(FileKind.VIDEO, FileKind.of("qa-steps.mp4", false))
        assertEquals(FileKind.AUDIO, FileKind.of("03.mp3", false))
        assertEquals(FileKind.ARCHIVE, FileKind.of("qa.zip", false))
        assertEquals(FileKind.TEXT, FileKind.of("a.txt", false))
        assertEquals(FileKind.OTHER, FileKind.of("b.bin", false))
        assertEquals(FileKind.OTHER, FileKind.of(".hidden", false))
        assertTrue(FileKind.isZip("QA.ZIP", false))
        assertFalse(FileKind.isZip("qa.zip", true))
        assertFalse(FileKind.isZip("a.apk", false))
    }

    @Test
    fun `a zip's crumbs are its folder's, the zip, each nested zip, then its own folders`() {
        val zip = "$root/QA-Files/zips/qa-nested.zip"
        val at = FilesLocation.Zip(FilePaths.PRIMARY, zip, listOf("inner/qa.zip"), "dir/deep")
        val crumbs = FilesNav.crumbs(at, labelOf, rootOf)
        assertEquals(listOf("This Device", "QA-Files", "zips", "qa-nested.zip", "qa.zip", "dir", "deep"), crumbs.map { it.label })
        assertEquals(folder("$root/QA-Files/zips"), crumbs[2].location)
        assertEquals(FilesLocation.Zip(FilePaths.PRIMARY, zip), crumbs[3].location)
        assertEquals(FilesLocation.Zip(FilePaths.PRIMARY, zip, listOf("inner/qa.zip")), crumbs[4].location)
        assertEquals(at.copy(dir = "dir"), crumbs[5].location)
        assertEquals(at, crumbs.last().location)
        assertEquals(FilesNav.PANE_DEVICE, FilesNav.paneId(at))
    }

    @Test
    fun `up inside a zip walks its folders, then the zip around it, then the folder it lies in`() {
        val zip = "$root/QA-Files/zips/qa-nested.zip"
        val deep = FilesLocation.Zip(FilePaths.PRIMARY, zip, listOf("inner/qa.zip"), "dir/deep")
        assertEquals(deep.copy(dir = "dir"), FilesNav.parent(deep, rootOf))
        assertEquals(deep.copy(dir = ""), FilesNav.parent(deep.copy(dir = "dir"), rootOf))
        assertEquals(FilesLocation.Zip(FilePaths.PRIMARY, zip, emptyList(), "inner"), FilesNav.parent(deep.copy(dir = ""), rootOf))
        assertEquals(folder("$root/QA-Files/zips"), FilesNav.parent(FilesLocation.Zip(FilePaths.PRIMARY, zip), rootOf))
        assertNull("a zip on a volume that is gone", FilesNav.parent(FilesLocation.Zip("AAAA-0000", "/storage/AAAA-0000/a.zip"), rootOf))
    }

    @Test
    fun `Properties' breadcrumb ends in the item's name and up is its folder`() {
        val at = FilesLocation.Properties(FilePaths.PRIMARY, "$root/QA-Files/b.bin", "b.bin", isDirectory = false)
        val crumbs = FilesNav.crumbs(at, labelOf, rootOf)
        assertEquals(listOf("This Device", "QA-Files", "b.bin"), crumbs.map { it.label })
        assertEquals(folder("$root/QA-Files"), crumbs[1].location)
        assertEquals(folder("$root/QA-Files"), FilesNav.parent(at, rootOf))
        // A volume's own root: its one segment is the volume's name, and there is nothing above it.
        val whole = FilesLocation.Properties(FilePaths.PRIMARY, root, "This Device", isDirectory = true)
        assertEquals(listOf("This Device"), FilesNav.crumbs(whole, labelOf, rootOf).map { it.label })
        assertNull(FilesNav.parent(whole, rootOf))
    }
}
