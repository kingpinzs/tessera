package app.tileshell.tiles

/**
 * Code-defined W10M-style default Start layout (Q14), in small-tile units on the 3-medium-column grid
 * (6 units across). The exact default arrangement has no R3 source: approximation recorded in the
 * INDEX change log with its own NEEDS-HUMAN row.
 */
object DefaultLayout {
    fun placements(): List<Placement> = listOf(
        Placement(TileKey.SlotTile(Slot.PHONE), 0, 0, TileSize.MEDIUM),
        Placement(TileKey.SlotTile(Slot.PEOPLE), 2, 0, TileSize.MEDIUM),
        Placement(TileKey.SlotTile(Slot.MESSAGING), 4, 0, TileSize.MEDIUM),
        Placement(TileKey.SlotTile(Slot.BROWSER), 0, 2, TileSize.MEDIUM),
        Placement(TileKey.SlotTile(Slot.MAIL), 2, 2, TileSize.MEDIUM),
        Placement(TileKey.SlotTile(Slot.CAMERA), 4, 2, TileSize.SMALL),
        Placement(TileKey.SlotTile(Slot.STORE), 5, 2, TileSize.SMALL),
        Placement(TileKey.SlotTile(Slot.MAPS), 4, 3, TileSize.SMALL),
        Placement(TileKey.SlotTile(Slot.MUSIC), 5, 3, TileSize.SMALL),
        Placement(TileKey.SlotTile(Slot.CALENDAR), 0, 4, TileSize.WIDE),
        Placement(TileKey.SlotTile(Slot.PHOTOS), 4, 4, TileSize.MEDIUM),
        Placement(TileKey.ShellTile(ShellTiles.WEATHER), 0, 6, TileSize.WIDE),
        Placement(TileKey.ShellTile(ShellTiles.SETTINGS), 4, 6, TileSize.MEDIUM),
    )
}

object ShellTiles {
    const val WEATHER = "weather"
    const val SETTINGS = "settings"
}
