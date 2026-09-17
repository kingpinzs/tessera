package app.tileshell.tiles

/**
 * Code-defined W10M-style default Start layout (Q14), in small-tile units on the 3-medium-column grid
 * (6 units across). The exact default arrangement has no R3 source: approximation recorded in the
 * INDEX change log with its own NEEDS-HUMAN row.
 */
object DefaultLayout {
    fun placements(): List<Placement> = listOf(
        Placement(TileKey.SlotTile(Slot.PEOPLE), 0, 0, TileSize.MEDIUM),
        Placement(TileKey.SlotTile(Slot.BROWSER), 2, 0, TileSize.MEDIUM),
        Placement(TileKey.SlotTile(Slot.MAIL), 4, 0, TileSize.MEDIUM),
        Placement(TileKey.SlotTile(Slot.CALENDAR), 0, 2, TileSize.WIDE),
        Placement(TileKey.SlotTile(Slot.PHOTOS), 4, 2, TileSize.MEDIUM),
        Placement(TileKey.ShellTile(ShellTiles.WEATHER), 0, 4, TileSize.WIDE),
        Placement(TileKey.SlotTile(Slot.STORE), 4, 4, TileSize.SMALL),
        Placement(TileKey.SlotTile(Slot.MAPS), 5, 4, TileSize.SMALL),
        Placement(TileKey.SlotTile(Slot.MUSIC), 4, 5, TileSize.SMALL),
        Placement(TileKey.ShellTile(ShellTiles.SETTINGS), 5, 5, TileSize.SMALL),
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
