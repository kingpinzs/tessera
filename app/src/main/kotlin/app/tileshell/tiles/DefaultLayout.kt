package app.tileshell.tiles

/**
 * Code-defined W10M-style default Start layout (Q14), in small-tile units on the 3-medium-column grid
 * (6 units across). The exact default arrangement has no R3 source: approximation recorded in the
 * INDEX change log with its own NEEDS-HUMAN row.
 */
object DefaultLayout {
    /**
     * Reading order of the default Start (phase 02: positions are packed from the order by [GridPack], which
     * reproduces the phase 01 coordinates on the 6-unit grid and re-packs losslessly on the 4-unit one).
     */
    fun order(): List<Sized> = listOf(
        Sized(TileKey.SlotTile(Slot.PEOPLE), TileSize.MEDIUM),
        Sized(TileKey.SlotTile(Slot.BROWSER), TileSize.MEDIUM),
        Sized(TileKey.SlotTile(Slot.MAIL), TileSize.MEDIUM),
        Sized(TileKey.SlotTile(Slot.CALENDAR), TileSize.WIDE),
        Sized(TileKey.SlotTile(Slot.PHOTOS), TileSize.MEDIUM),
        Sized(TileKey.ShellTile(ShellTiles.WEATHER), TileSize.WIDE),
        Sized(TileKey.SlotTile(Slot.STORE), TileSize.SMALL),
        Sized(TileKey.SlotTile(Slot.MAPS), TileSize.SMALL),
        Sized(TileKey.SlotTile(Slot.MUSIC), TileSize.SMALL),
        Sized(TileKey.ShellTile(ShellTiles.SETTINGS), TileSize.SMALL),
    )

    /** Bottom tile row default (INDEX Change Log 2026-09-17, Jeremy): Phone, Messaging, Camera. */
    fun dock(): List<TileKey> = listOf(
        TileKey.SlotTile(Slot.PHONE),
        TileKey.SlotTile(Slot.MESSAGING),
        TileKey.SlotTile(Slot.CAMERA),
    )
}

object ShellTiles {
    const val WEATHER = "weather"
    const val SETTINGS = "settings"
}
