package app.tileshell.tiles

import android.content.ComponentName
import android.os.UserHandle

/** Tile sizes W10M phones rendered (R1 §1.1; R3 A25: no large tile on phones). Spans are in small-tile units. */
enum class TileSize(val spanX: Int, val spanY: Int) {
    SMALL(1, 1), MEDIUM(2, 2), WIDE(4, 2);

    /** W10M's resize cycle, the one the resize glyph's arrow points along (R6 §1.2.6). */
    fun next(): TileSize = when (this) {
        MEDIUM -> SMALL
        SMALL -> WIDE
        WIDE -> MEDIUM
    }
}

/**
 * App slots of the W10M default layout (phase 01 Decisions, interview Q1).
 * Role slots follow Android's role holder; category slots start unassigned unless Android resolves a
 * single or preferred default for the category intent.
 */
enum class Slot(val label: String, val role: String? = null, val category: String? = null, val stillImageCamera: Boolean = false) {
    PHONE("Phone", role = "android.app.role.DIALER"),
    MESSAGING("Messaging", role = "android.app.role.SMS"),
    BROWSER("Browser", role = "android.app.role.BROWSER"),
    MAIL("Mail", category = "android.intent.category.APP_EMAIL"),
    MUSIC("Music", category = "android.intent.category.APP_MUSIC"),
    MAPS("Maps", category = "android.intent.category.APP_MAPS"),
    STORE("Store", category = "android.intent.category.APP_MARKET"),
    PHOTOS("Photos", category = "android.intent.category.APP_GALLERY"),
    PEOPLE("People", category = "android.intent.category.APP_CONTACTS"),
    CAMERA("Camera", stillImageCamera = true),
    CALENDAR("Calendar", category = "android.intent.category.APP_CALENDAR"),
}

/** What a tile stands for. */
sealed interface TileKey {
    val id: String

    /** An app slot (resolved to a component at runtime). */
    data class SlotTile(val slot: Slot) : TileKey { override val id = "slot:${slot.name}" }

    /** A specific app component (phase 02 pins these; the model supports it from phase 01). */
    data class AppTile(val component: ComponentName, val user: UserHandle?) : TileKey {
        override val id = "app:${component.flattenToString()}:${user?.hashCode() ?: 0}"
    }

    /** A shell part with its own tile (Weather; later phases ADD more, e.g. Cortana in phase 03). */
    data class ShellTile(val name: String) : TileKey { override val id = "shell:$name" }

    /** A live folder (phase 02): the members live in [LayoutStore.Layout.folders] under [folderId]. */
    data class FolderTile(val folderId: String) : TileKey { override val id = "folder:$folderId" }

    /** An app's secondary tile, keyed (owner, tileId) as R5 §4b requires (phase 02 build task 6). */
    data class SecondaryTile(val owner: String, val tileId: String) : TileKey { override val id = "secondary:$owner:$tileId" }
}

/** A tile and its size, in the order Start reads them (phase 02: position is derived by [GridPack]). */
data class Sized(val key: TileKey, val size: TileSize)

/** A placed tile in small-tile units on the Start grid. */
data class Placement(val key: TileKey, val x: Int, val y: Int, val size: TileSize)

/**
 * A live folder (phase 02, R6 §1.6): a tile on Start whose members open in a full-width band below it.
 * Members keep their own sizes and pack in the band exactly as the grid packs (R6 §1.6.5: "member tiles
 * … at full size on the same column grid"). Folders never nest (H23).
 */
data class Folder(val id: String, val name: String?, val members: List<Sized>)

/** How a slot is assigned: explicit choices always stick; auto choices follow Android's default. */
data class SlotAssignment(val component: ComponentName?, val explicit: Boolean)
