package app.tileshell.files

/** The extras FilesActivity reads (r3 D12): they only choose what is shown. */
object FilesIntents {
    /** The page: `device`, `recent`, `bin` or `volume:<uuid>`. */
    const val EXTRA_PAGE = "page"

    /** [EXTRA_PAGE]'s values: This Device (the primary shared storage), Recent, the Recycle Bin, a mounted volume's root. */
    const val PAGE_DEVICE = "device"
    const val PAGE_RECENT = "recent"
    const val PAGE_BIN = "bin"
    const val PAGE_VOLUME_PREFIX = "volume:"

    /** [EXTRA_PAGE] for the volume with that `StorageVolume.getUuid()` (the dynamic "SD card" shortcut's page). */
    fun volumePage(uuid: String): String = PAGE_VOLUME_PREFIX + uuid

    /** A folder to open, canonical under a mounted volume. */
    const val EXTRA_PATH = "path"

    /** The row to show in that folder. */
    const val EXTRA_NAME = "name"

    /** Who asked (`recorder`), for `[files] open at <path> (from <caller>)`. */
    const val EXTRA_FROM = "from"
}
