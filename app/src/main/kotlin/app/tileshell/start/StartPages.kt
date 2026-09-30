package app.tileshell.start

/**
 * The pages of Start's pager (phase 14 Decisions "Pager"): the pod bay left of Start, Start, the app list right of it.
 * The pager opens on [START]; a name is what the `[start] page=<name>` line and the home line print.
 */
object StartPages {
    const val POD_BAY = 0
    const val START = 1
    const val APP_LIST = 2
    const val COUNT = 3

    fun name(page: Int): String = when (page) {
        POD_BAY -> "POD_BAY"
        START -> "START"
        APP_LIST -> "APP_LIST"
        else -> "PAGE_$page"
    }
}
