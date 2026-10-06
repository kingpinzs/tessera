package app.tileshell.files

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 18, Q-18-1 ("below Q-18-1"): Recent's store — the files opened in Files. */
class RecentStoreTest {
    private val dir: File = Files.createTempDirectory("recent").toFile()
    private val lines = mutableListOf<String>()
    private var now = 1_000L
    private val store = RecentStore(dir, { now }, { lines += it })
    private val both = setOf("primary", "3842-1609")

    @After fun tearDown() { dir.deleteRecursively() }

    private fun open(path: String, volume: String = "primary") {
        now += 1_000
        store.add(path, volume)
    }

    private fun paths(mounted: Set<String> = both) = store.read(mounted) { true }.map { it.path }

    @Test
    fun `empty - nothing opened reads as no rows and recent 0`() {
        assertEquals(emptyList<String>(), paths())
        assertEquals(listOf("recent: 0"), lines)
    }

    @Test
    fun `newest first, one entry per path - re-opening moves a file to the top`() {
        open("/s/r1.png"); open("/s/r2.png"); open("/s/r3.png")
        assertEquals(listOf("/s/r3.png", "/s/r2.png", "/s/r1.png"), paths())
        open("/s/r1.png")
        assertEquals(listOf("/s/r1.png", "/s/r3.png", "/s/r2.png"), paths())
        assertEquals(5_000L, store.all().first().openedAt)
        assertEquals(listOf("recent add /s/r1.png", "recent add /s/r2.png", "recent add /s/r3.png", "recent: 3", "recent add /s/r1.png", "recent: 3"), lines)
    }

    @Test
    fun `cap 100 - the 101st open drops the oldest`() {
        for (i in 1..101) open("/s/f$i")
        val all = paths()
        assertEquals(100, all.size)
        assertEquals("/s/f101", all.first())
        assertEquals("/s/f2", all.last())
        assertEquals("recent: 100", lines.last())
    }

    @Test
    fun `the store is a file in the given dir, written temp-and-rename, and survives a restart`() {
        open("/s/r1.png", "3842-1609")
        assertEquals(listOf("files-recent.json"), dir.list()!!.toList())
        assertEquals("""{"recent":[{"path":"/s/r1.png","volume":"3842-1609","openedAt":2000}]}""", File(dir, "files-recent.json").readText())
        val restarted = RecentStore(dir, { now }, {})
        assertEquals(listOf(RecentStore.Entry("/s/r1.png", "3842-1609", 2_000)), restarted.all())
    }

    @Test
    fun `remove from recent - the entry goes with its line, and nothing else does`() {
        open("/s/r1.png"); open("/s/r2.png")
        lines.clear()
        store.remove("/s/r2.png")
        store.remove("/s/never-opened.png")
        assertEquals(listOf("/s/r1.png"), paths())
        assertEquals(listOf("recent remove /s/r2.png", "recent: 1"), lines)
    }

    @Test
    fun `rename-path - a renamed file keeps its place under its new name`() {
        open("/s/r1.png"); open("/s/r2.png"); open("/s/r3.png")
        store.renamePath("/s/r2.png", "/s/r2b.png")
        assertEquals(listOf("/s/r3.png", "/s/r2b.png", "/s/r1.png"), paths())
        assertEquals(3_000L, store.all()[1].openedAt)
    }

    @Test
    fun `rename-path - a moved folder carries every entry inside it, onto its new volume, and a sibling is untouched`() {
        open("/s/album/a.png"); open("/s/album/deep/b.png"); open("/s/album2/c.png")
        store.renamePath("/s/album", "/card/album", "3842-1609")
        assertEquals(listOf("/s/album2/c.png", "/card/album/deep/b.png", "/card/album/a.png"), paths())
        assertEquals(listOf("primary", "3842-1609", "3842-1609"), store.all().map { it.volumeUuid })
    }

    @Test
    fun `rename-path - a file renamed onto a path that has an entry leaves one entry, the renamed one's`() {
        open("/s/old.png"); open("/s/new.png"); open("/s/other.png")
        store.renamePath("/s/old.png", "/s/new.png")
        assertEquals(listOf("/s/other.png", "/s/new.png"), paths())
        assertEquals(2_000L, store.all().last().openedAt)
    }

    @Test
    fun `drop-gone - a file found gone on a read is dropped for good`() {
        open("/s/r1.png"); open("/s/r2.png")
        assertEquals(listOf("/s/r1.png"), store.read(both) { it != "/s/r2.png" }.map { it.path })
        // Dropped, not hidden: it does not come back when the path exists again.
        assertEquals(listOf("/s/r1.png"), paths())
        assertEquals(1, store.all().size)
    }

    @Test
    fun `hide-unmounted - an entry on an unmounted volume is hidden, kept, and back when the volume mounts`() {
        open("/s/r1.png"); open("/card/r2.png", "3842-1609")
        // The card is out: its file cannot be seen, and that is not "gone".
        assertEquals(listOf("/s/r1.png"), store.read(setOf("primary")) { it.startsWith("/s/") }.map { it.path })
        assertEquals("recent: 1", lines.last())
        assertEquals(2, store.all().size)
        assertEquals(listOf("/card/r2.png", "/s/r1.png"), paths())
    }

    @Test
    fun `an unreadable store is an empty list, and the next open starts it again`() {
        File(dir, "files-recent.json").writeText("{ not json")
        assertTrue(store.all().isEmpty())
        open("/s/r1.png")
        assertEquals(listOf("/s/r1.png"), paths())
    }

    @Test
    fun `a path with a newline cannot forge a diagnostics line`() {
        open("/s/a\n[files] bin empty /sdcard: ok")
        assertEquals("recent add /s/a?[files] bin empty /sdcard: ok", lines.single())
    }
}
