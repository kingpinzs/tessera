package app.tileshell.tiles

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Games and Office folders (Jeremy, 2026-09-22: "there should be games and office folders").
 *
 * Keys here are [TileKey.SecondaryTile], not [TileKey.AppTile], for the reason LayoutOpsTest gives:
 * ComponentName is an unmocked framework class in a JVM test, so every AppTile id would read back the
 * same and the distinctness these rules depend on could not be seen at all.
 */
class CategoryFoldersTest {

    private fun app(pkg: String) = TileKey.SecondaryTile(pkg, "main")
    private fun candidate(pkg: String, category: Int, installedAtMs: Long = 0L, label: String = pkg) =
        CategoryFolders.Candidate(app(pkg), label, category, installedAtMs)

    private val games = listOf(
        candidate("one", ApplicationInfo.CATEGORY_GAME, installedAtMs = 30),
        candidate("two", ApplicationInfo.CATEGORY_GAME, installedAtMs = 20),
        candidate("three", ApplicationInfo.CATEGORY_GAME, installedAtMs = 10),
    )
    private val office = listOf(
        candidate("docs", ApplicationInfo.CATEGORY_PRODUCTIVITY, installedAtMs = 5),
        candidate("sheets", ApplicationInfo.CATEGORY_PRODUCTIVITY, installedAtMs = 6),
    )
    private val undeclared = listOf(
        candidate("mystery", ApplicationInfo.CATEGORY_UNDEFINED),
        candidate("other", ApplicationInfo.CATEGORY_UNDEFINED),
        candidate("third", ApplicationInfo.CATEGORY_UNDEFINED),
    )

    @Test
    fun `a folder holds the apps that declare its category and nothing else`() {
        val members = CategoryFolders.members(games + office + undeclared, ApplicationInfo.CATEGORY_GAME)
        assertEquals(listOf(app("one"), app("two"), app("three")), members)
    }

    @Test
    fun `apps that declare nothing are never guessed into a folder`() {
        assertTrue(CategoryFolders.members(undeclared, ApplicationInfo.CATEGORY_GAME).isEmpty())
        assertTrue(CategoryFolders.members(undeclared, ApplicationInfo.CATEGORY_PRODUCTIVITY).isEmpty())
    }

    @Test
    fun `one app is not a folder, because a folder of one dissolves`() {
        val single = listOf(candidate("lonely", ApplicationInfo.CATEGORY_GAME))
        assertTrue(CategoryFolders.members(single, ApplicationInfo.CATEGORY_GAME).isEmpty())
        assertEquals(2, CategoryFolders.MIN_MEMBERS)
    }

    @Test
    fun `no apps at all makes no folder rather than an empty one`() {
        assertTrue(CategoryFolders.members(emptyList(), ApplicationInfo.CATEGORY_GAME).isEmpty())
    }

    @Test
    fun `the newest installed survive the cap`() {
        val many = (1..40).map { candidate("g$it", ApplicationInfo.CATEGORY_GAME, installedAtMs = it.toLong()) }
        val members = CategoryFolders.members(many, ApplicationInfo.CATEGORY_GAME)
        assertEquals(CategoryFolders.MAX_MEMBERS, members.size)
        assertEquals(app("g40"), members.first())
        assertTrue(app("g1") !in members)
    }

    @Test
    fun `apps installed in the same breath are ordered by name`() {
        val together = listOf(
            candidate("z", ApplicationInfo.CATEGORY_GAME, installedAtMs = 7, label = "Zebra"),
            candidate("a", ApplicationInfo.CATEGORY_GAME, installedAtMs = 7, label = "Aardvark"),
        )
        assertEquals(listOf(app("a"), app("z")), CategoryFolders.members(together, ApplicationInfo.CATEGORY_GAME))
    }

    @Test
    fun `the two markers are different, so one folder's ADD cannot silence the other`() {
        assertTrue(CategoryFolders.GAMES_ADD != CategoryFolders.OFFICE_ADD)
    }

    // ---- the layout side: building a folder out of apps that are not on Start ----

    private fun layout(vararg items: Sized) =
        LayoutStore.Layout(LayoutStore.VERSION, items.toList(), emptyMap(), emptyList(), emptyMap())

    @Test
    fun `a seeded folder lands on Start holding its members`() {
        val start = layout(Sized(TileKey.ShellTile("weather"), TileSize.WIDE))
        val made = LayoutOps.folderOf(start, "Games", listOf(app("one"), app("two")), TileSize.MEDIUM)
        assertNotNull(made)
        val (next, id) = made!!
        assertEquals("Games", next.folders.getValue(id).name)
        assertEquals(listOf(app("one"), app("two")), next.folders.getValue(id).members.map { it.key })
        assertTrue(TileKey.FolderTile(id) in next.order.map { it.key })
        // The tile that was already there is untouched.
        assertTrue(TileKey.ShellTile("weather") in next.order.map { it.key })
    }

    @Test
    fun `seeding refuses to make a folder of one`() {
        assertNull(LayoutOps.folderOf(layout(), "Games", listOf(app("only")), TileSize.MEDIUM))
        assertNull(LayoutOps.folderOf(layout(), "Games", emptyList(), TileSize.MEDIUM))
    }

    @Test
    fun `a member already on Start is moved into the folder, not duplicated`() {
        val start = layout(Sized(app("one"), TileSize.WIDE), Sized(TileKey.ShellTile("weather"), TileSize.MEDIUM))
        val (next, id) = LayoutOps.folderOf(start, "Games", listOf(app("one"), app("two")), TileSize.MEDIUM)!!
        assertTrue("the app is no longer loose on Start", app("one") !in next.order.map { it.key })
        assertTrue(app("one") in next.folders.getValue(id).members.map { it.key })
        assertEquals(1, next.order.count { it.key == TileKey.FolderTile(id) })
    }

    @Test
    fun `an app the user already filed in a folder is left alone`() {
        // Found on the emulator 2026-09-22: seeding Office took a member out of an existing folder, which
        // left that folder holding one tile, which H19 then dissolved. A seeded folder collects loose
        // apps only.
        val mine = Folder("f1", "Mine", listOf(Sized(app("one"), TileSize.MEDIUM), Sized(app("two"), TileSize.MEDIUM)))
        val start = LayoutStore.Layout(
            LayoutStore.VERSION,
            listOf(Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM), Sized(app("three"), TileSize.MEDIUM)),
            emptyMap(), emptyList(), mapOf("f1" to mine),
        )
        val made = LayoutOps.folderOf(start, "Games", listOf(app("one"), app("three"), app("four")), TileSize.MEDIUM)
        assertNotNull(made)
        val (next, id) = made!!
        assertEquals(listOf(app("three"), app("four")), next.folders.getValue(id).members.map { it.key })
        assertEquals("the user's folder is untouched", listOf(app("one"), app("two")), next.folders.getValue("f1").members.map { it.key })
    }

    @Test
    fun `nothing is seeded when only filed apps match`() {
        val mine = Folder("f1", "Mine", listOf(Sized(app("one"), TileSize.MEDIUM), Sized(app("two"), TileSize.MEDIUM)))
        val start = LayoutStore.Layout(
            LayoutStore.VERSION, listOf(Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM)),
            emptyMap(), emptyList(), mapOf("f1" to mine),
        )
        assertNull(LayoutOps.folderOf(start, "Games", listOf(app("one"), app("two")), TileSize.MEDIUM))
    }

    @Test
    fun `folders never nest`() {
        val start = layout(Sized(TileKey.ShellTile("weather"), TileSize.MEDIUM), folders())
        assertNull(LayoutOps.folderOf(start, "Games", listOf(TileKey.FolderTile("f1"), app("one")), TileSize.MEDIUM))
    }

    private fun folders() = Sized(TileKey.FolderTile("f1"), TileSize.MEDIUM)
}
