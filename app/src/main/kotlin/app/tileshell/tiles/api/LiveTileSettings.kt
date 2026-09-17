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
    private const val KEY_API = "api_enabled"
    private const val KEY_LEGACY = "trust_unverified_legacy_badges"

    /** Master switch for the whole API (Settings > Live tile access). Off refuses every caller. */
    fun isApiEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_API, true)

    fun setApiEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_API, enabled).apply()
        Diagnostics.add("livetile", "API master switch enabled=$enabled")
        LiveTileStore.get(context).republishAll()
    }

    /**
     * The legacy badge broadcast carries the badge's package as an extra; Android only tells the receiver who sent it
     * when the sender opted into sharing its identity, which the ShortcutBadger senders do not. Off (the default), an
     * unverified broadcast is refused, because otherwise any app could set or clear any other app's badge. On, they
     * are accepted as before and marked unverified in diagnostics.
     */
    fun trustsUnverifiedLegacyBadges(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LEGACY, false)

    fun setTrustUnverifiedLegacyBadges(context: Context, trusted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LEGACY, trusted).apply()
        Diagnostics.add("livetile", "unverified legacy badge broadcasts trusted=$trusted")
    }

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
