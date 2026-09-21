package app.tileshell.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phase 02 layout rules, proved without a device (build task 1; the drag itself is E1/E3/E8 on the emulator). */
class LayoutOpsTest {
    private fun shell(n: String) = TileKey.ShellTile(n)
    /**
     * Package-owned tiles are keyed by SecondaryTile here, not AppTile: android.content.ComponentName is an
     * unmocked framework class in a JVM test, so an AppTile's packageName reads back null. Both keys go through
     * the same branch of [LayoutOps.removePackages]; the AppTile half is proven on the device in E6.
     */
    private fun app(pkg: String) = TileKey.SecondaryTile(pkg, "main")
    private fun layout(vararg items: Sized, dock: List<TileKey> = emptyList(), folders: Map<String, Folder> = emptyMap()) =
        LayoutStore.Layout(LayoutStore.VERSION, items.toList(), emptyMap(), dock, folders)

    private val a = Sized(shell("a"), TileSize.MEDIUM)
    private val b = Sized(shell("b"), TileSize.MEDIUM)
    private val c = Sized(shell("c"), TileSize.SMALL)

    @Test
    fun `pinning an app already on Start adds nothing`() {
        val start = layout(a, b)
        assertNotNull(LayoutOps.pin(start, shell("c"), TileSize.MEDIUM))
        assertNull(LayoutOps.pin(start, shell("a"), TileSize.MEDIUM))
        // also when it is in the bottom row or inside a folder
        assertNull(LayoutOps.pin(layout(a, dock = listOf(shell("d"))), shell("d"), TileSize.MEDIUM))
        val withFolder = layout(Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM), folders = mapOf("f1" to Folder("f1", null, listOf(a, b))))
        assertNull(LayoutOps.pin(withFolder, shell("a"), TileSize.MEDIUM))
    }

    @Test
    fun `a pin lands at the end of the grid`() {
        val next = LayoutOps.pin(layout(a, b), shell("z"), TileSize.MEDIUM)!!
        assertEquals("shell:z", next.order.last().key.id)
    }

    @Test
    fun `unpinning one of a two-tile folder leaves the other tile in the folder's place`() {
        val folder = Folder("f1", "Mine", listOf(a, b))
        val start = layout(c, Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM), folders = mapOf("f1" to folder))
        val next = LayoutOps.without(start, shell("a"))
        assertTrue(next.folders.isEmpty())
        assertEquals(listOf("shell:c", "shell:b"), next.order.map { it.key.id })
        // H19: the survivor keeps ITS own size, and the folder's name is dropped with the folder
        assertEquals(TileSize.MEDIUM, next.order[1].size)
    }

    @Test
    fun `uninstalling an app inside a folder dissolves a folder left with one tile`() {
        val folder = Folder("f1", null, listOf(Sized(app("com.one"), TileSize.MEDIUM), Sized(app("com.two"), TileSize.SMALL)))
        val start = layout(Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM), folders = mapOf("f1" to folder))
        val next = LayoutOps.removePackages(start, setOf("com.one"))
        assertTrue(next.folders.isEmpty())
        assertEquals(listOf("secondary:com.two:main"), next.order.map { it.key.id })
        assertEquals(TileSize.SMALL, next.order[0].size)
    }

    @Test
    fun `uninstalling takes the app's tiles out of the grid, the row and its secondary tiles`() {
        val start = layout(
            Sized(TileKey.SecondaryTile("com.one", "t1"), TileSize.MEDIUM),
            Sized(TileKey.SecondaryTile("com.one", "t2"), TileSize.SMALL),
            b,
            dock = listOf(TileKey.SecondaryTile("com.one", "t3"), shell("d")),
        )
        val next = LayoutOps.removePackages(start, setOf("com.one"))
        assertEquals(listOf("shell:b"), next.order.map { it.key.id })
        assertEquals(listOf("shell:d"), next.dock.map { it.id })
    }

    @Test
    fun `an update keeps the tiles - only a removal drops them`() {
        val start = layout(Sized(app("com.one"), TileSize.MEDIUM))
        assertEquals(start, LayoutOps.removePackages(start, setOf("com.other")))
    }

    @Test
    fun `a folder dropped on a folder never nests`() {
        val folders = mapOf("f1" to Folder("f1", null, listOf(a, b)), "f2" to Folder("f2", null, listOf(c, Sized(shell("d"), TileSize.SMALL))))
        val start = layout(Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM), Sized(TileKey.FolderTile("f2"), TileSize.MEDIUM), folders = folders)
        assertNull(LayoutOps.createFolder(start, TileKey.FolderTile("f1"), TileKey.FolderTile("f2")))
        assertNull(LayoutOps.createFolder(start, shell("x"), TileKey.FolderTile("f2")))
        assertNull(LayoutOps.addToFolder(start, "f1", TileKey.FolderTile("f2")))
    }

    @Test
    fun `a folder is created in the target's place at the target's size`() {
        val start = layout(a, Sized(shell("wide"), TileSize.WIDE), c)
        val (next, id) = LayoutOps.createFolder(start, shell("wide"), shell("c"))!!
        assertEquals(listOf("shell:a", "folder:$id"), next.order.map { it.key.id })
        assertEquals(TileSize.WIDE, next.order[1].size)
        assertEquals(listOf("shell:wide", "shell:c"), next.folders[id]!!.members.map { it.key.id })
        // the members keep their own sizes (the band draws them at full size)
        assertEquals(listOf(TileSize.WIDE, TileSize.SMALL), next.folders[id]!!.members.map { it.size })
    }

    @Test
    fun `adding a fourth member appends it`() {
        val folder = Folder("f1", null, listOf(a, b, c))
        val start = layout(Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM), Sized(shell("d"), TileSize.SMALL), folders = mapOf("f1" to folder))
        val next = LayoutOps.addToFolder(start, "f1", shell("d"))!!
        assertEquals(4, next.folders["f1"]!!.members.size)
        assertEquals("shell:d", next.folders["f1"]!!.members.last().key.id)
        assertEquals(listOf("folder:f1"), next.order.map { it.key.id })
    }

    @Test
    fun `a drop onto a full bottom tile row is refused`() {
        val dock = (1..6).map { shell("d$it") }
        val start = layout(a, dock = dock)
        assertNull(LayoutOps.moveToDock(start, shell("a"), 0, capacity = 6))
        assertNotNull(LayoutOps.moveToDock(start, shell("a"), 0, capacity = 7))
        // moving a tile that is already in the row is never a refusal
        assertNotNull(LayoutOps.moveToDock(start, shell("d1"), 3, capacity = 6))
    }

    @Test
    fun `turning show more tiles off moves the row's overflow to the end of the grid`() {
        val dock = (1..6).map { shell("d$it") }
        val start = layout(a, b, dock = dock)
        val next = LayoutOps.applyRowCapacity(start, 4)
        assertEquals(listOf("shell:d1", "shell:d2", "shell:d3", "shell:d4"), next.dock.map { it.id })
        assertEquals(listOf("shell:a", "shell:b", "shell:d5", "shell:d6"), next.order.map { it.key.id })
        assertEquals(TileSize.SMALL, next.order.last().size)
        // and turning it back on does not move them back by itself (they are grid tiles now)
        assertEquals(next, LayoutOps.applyRowCapacity(next, 6))
    }

    @Test
    fun `a tile dragged into the row leaves the grid, and back out leaves the row`() {
        val start = layout(a, b, dock = listOf(shell("d")))
        val inRow = LayoutOps.moveToDock(start, shell("a"), 0, capacity = 6)!!
        assertEquals(listOf("shell:a", "shell:d"), inRow.dock.map { it.id })
        assertEquals(listOf("shell:b"), inRow.order.map { it.key.id })
        val back = LayoutOps.moveInGrid(inRow, shell("a"), 1)
        assertEquals(listOf("shell:d"), back.dock.map { it.id })
        assertEquals(listOf("shell:b", "shell:a"), back.order.map { it.key.id })
        // it keeps the small size the row gave it until it is resized
        assertEquals(TileSize.SMALL, back.order.last().size)
    }

    @Test
    fun `renaming a folder trims, and a blank name clears it back to the placeholder`() {
        val start = layout(Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM), folders = mapOf("f1" to Folder("f1", null, listOf(a, b))))
        assertEquals("Work", LayoutOps.renameFolder(start, "f1", "  Work ").folders["f1"]!!.name)
        assertNull(LayoutOps.renameFolder(start, "f1", "   ").folders["f1"]!!.name)
    }

    @Test
    fun `a tile taken out of a folder into the grid keeps the folder alive when three members remain`() {
        val folder = Folder("f1", null, listOf(a, b, c, Sized(shell("d"), TileSize.SMALL)))
        val start = layout(Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM), folders = mapOf("f1" to folder))
        val next = LayoutOps.moveInGrid(start, shell("a"), 0)
        assertEquals(3, next.folders["f1"]!!.members.size)
        assertEquals(listOf("shell:a", "folder:f1"), next.order.map { it.key.id })
    }

    @Test
    fun `unpinning a folder tile takes the folder with it`() {
        val start = layout(Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM), a, folders = mapOf("f1" to Folder("f1", null, listOf(b, c))))
        val next = LayoutOps.without(start, TileKey.FolderTile("f1"))
        assertTrue(next.folders.isEmpty())
        assertEquals(listOf("shell:a"), next.order.map { it.key.id })
        assertFalse(next.contains(shell("b")))
    }

    @Test
    fun `resize reaches a tile inside a folder too`() {
        val start = layout(Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM), folders = mapOf("f1" to Folder("f1", null, listOf(a, b))))
        val next = LayoutOps.resize(start, shell("a"), TileSize.WIDE)
        assertEquals(TileSize.WIDE, next.folders["f1"]!!.members[0].size)
    }

    @Test
    fun `the resize cycle is medium then small then wide`() {
        assertEquals(TileSize.SMALL, TileSize.MEDIUM.next())
        assertEquals(TileSize.WIDE, TileSize.SMALL.next())
        assertEquals(TileSize.MEDIUM, TileSize.WIDE.next())
    }
}
