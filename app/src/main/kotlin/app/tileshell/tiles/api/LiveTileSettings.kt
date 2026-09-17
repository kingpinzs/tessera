package app.tileshell.tiles.api

import android.content.Context
import app.tileshell.diag.Diagnostics

/**
 * Per-app kill switch for the Live Tile API (Settings > Live tiles; tile.setting answers DISABLED_FOR_APPLICATION).
 * Stored in app-private SharedPreferences. While an app is disabled its API calls are refused with error "disabled"
 * and its stored API content and badge are hidden; re-enabling shows what it had stored.
 */
object LiveTileSettings {
    private const val PREFS = "livetile_api"
    private const val KEY_PREFIX = "disabled:"

    fun isDisabled(context: Context, pkg: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PREFIX + pkg, false)

    fun disabledPackages(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .filter { (k, v) -> k.startsWith(KEY_PREFIX) && v == true }
            .keys.map { it.removePrefix(KEY_PREFIX) }.toSet()

    fun setDisabled(context: Context, pkg: String, disabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (disabled) putBoolean(KEY_PREFIX + pkg, true) else remove(KEY_PREFIX + pkg)
        }.apply()
        Diagnostics.add("livetile", "kill switch $pkg disabled=$disabled")
        LiveTileStore.get(context).republish(pkg)
    }
}
