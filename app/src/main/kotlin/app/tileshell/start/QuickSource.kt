package app.tileshell.start

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.graphics.asImageBitmap
import app.tileshell.apps.AppCatalog
import app.tileshell.apps.AppEntry
import app.tileshell.tiles.ShellTiles
import app.tileshell.tiles.Slot
import app.tileshell.tiles.TileKey

/** What a tile asks for shortcuts (Decisions, T11-22): one activity under one user, or why it asks nothing. */
sealed interface QuickTarget {
    data class Query(val activity: ComponentName, val user: UserHandle) : QuickTarget
    data class None(val reason: String) : QuickTarget
}

/** One satellite's content: which shortcut it runs and what it shows. */
class SatelliteSpec(
    val index: Int,
    val id: String,
    val pkg: String,
    val user: UserHandle,
    val label: String,
    val icon: TileIcons.Icon?,
)

/**
 * A load's answer. [lines] are its `[quick]` diagnostics, held back and written only if the press becomes a
 * hold: a load starts at every DOWN, and a plain tap must leave nothing in the ring.
 */
sealed interface QuickLoad {
    val lines: List<String>
    data class Ready(val satellites: List<SatelliteSpec>, override val lines: List<String>) : QuickLoad
    data class None(val reason: String, override val lines: List<String> = emptyList()) : QuickLoad
}

/**
 * The shortcut source (build task 1). Everything here runs OFF the main thread (T11-32): it is started at the
 * DOWN of every press on a tile outside edit mode, so by the 783-ms hold the answer is already in hand and
 * nothing is added to the frame phase 02 E7 measures.
 */
class QuickSource(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val catalog = AppCatalog.get(context)

    /**
     * The tile's own activity and user. A slot resolves to its app first; a shell tile asks for the activity
     * its launch opens (`StartActivity.launchApp`), under the shell's own user; Tess opens a voice session, not
     * an activity, so she asks nothing. Never a package-wide fallback: Music and Start settings are one package,
     * and Tess would burst with Music's shortcuts.
     */
    fun targetOf(key: TileKey, resolveSlot: (Slot) -> AppEntry?): QuickTarget = when (key) {
        is TileKey.FolderTile -> QuickTarget.None(NoBurstReason.FOLDER)
        is TileKey.SecondaryTile -> QuickTarget.None(NoBurstReason.SECONDARY)
        is TileKey.SlotTile -> resolveSlot(key.slot)?.let { QuickTarget.Query(it.component, it.user) }
            ?: QuickTarget.None(NoBurstReason.NO_APP)
        is TileKey.AppTile -> catalog.find(key.component, key.user)?.let { QuickTarget.Query(it.component, it.user) }
            // A quiet profile's (or a locked private space's) activities are hidden from the catalog, but the tile
            // still names its app and user: ask anyway, so the decision says `profile quiet`, not `no app`.
            ?: key.user?.takeIf { catalog.isQuietMode(it) }?.let { QuickTarget.Query(key.component, it) }
            ?: QuickTarget.None(NoBurstReason.NO_APP)
        is TileKey.ShellTile -> when (key.name) {
            ShellTiles.WEATHER -> QuickTarget.Query(ComponentName(context, app.tileshell.weather.WeatherActivity::class.java), Process.myUserHandle())
            ShellTiles.SETTINGS -> QuickTarget.Query(ComponentName(context, app.tileshell.settings.SettingsActivity::class.java), Process.myUserHandle())
            else -> QuickTarget.None(NoBurstReason.NO_SHORTCUTS)
        }
    }

    /** The whole load for one tile: the decision, then each shown shortcut's label and icon. */
    fun load(tileId: String, target: QuickTarget, iconPx: Int): QuickLoad {
        val query = when (target) {
            is QuickTarget.None -> return QuickLoad.None(target.reason)
            is QuickTarget.Query -> target
        }
        val platform = Platform()
        val lines = ArrayList<String>()
        return when (val d = QuickRule.decide(platform, query) { lines += it }) {
            is QuickDecision.None -> QuickLoad.None(d.reason, lines)
            is QuickDecision.Show -> {
                val sats = d.shown.mapIndexed { i, c ->
                    val info = platform.infos.getValue(c.id)
                    SatelliteSpec(i, c.id, info.`package`, query.user, info.shortLabel?.toString().orEmpty(), icon(i, info, iconPx, lines))
                }
                QuickLoad.Ready(sats, lines)
            }
        }
    }

    /**
     * The satellite's glyph by phase 01's rule extended to shortcuts (H38): the adaptive icon's monochrome layer
     * tinted white where there is one, else the full-colour icon. A null drawable — what LauncherApps returns
     * for an icon URI that resolves to nothing — is a failed icon too (T11-33): the satellite draws its fill with
     * no glyph and still runs.
     */
    private fun icon(i: Int, info: ShortcutInfo, sizePx: Int, lines: MutableList<String>): TileIcons.Icon? {
        val drawable = try {
            launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)
        } catch (e: Exception) {
            lines += "satellite $i icon failed ${info.`package`}/${info.id}: $e"
            return null
        }
        if (drawable == null) {
            lines += "satellite $i icon failed ${info.`package`}/${info.id}: null drawable"
            return null
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val mono = (drawable as? AdaptiveIconDrawable)?.monochrome
        return if (mono != null) {
            val inset = (sizePx * 0.25f).toInt()
            mono.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
            mono.colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
            mono.draw(canvas)
            TileIcons.Icon(bmp.asImageBitmap(), true)
        } else {
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            TileIcons.Icon(bmp.asImageBitmap(), false)
        }
    }

    /** Runs a satellite's shortcut as its own user (Decisions "Satellite tap"). */
    fun start(sat: SatelliteSpec, bounds: Rect, options: Bundle?) {
        launcherApps.startShortcut(sat.pkg, sat.id, bounds, options, sat.user)
    }

    /** LauncherApps behind [ShortcutPlatform]; keeps the returned ShortcutInfos for labels and icons. */
    private inner class Platform : ShortcutPlatform<QuickTarget.Query> {
        val infos = HashMap<String, ShortcutInfo>()

        override fun isShortcutHost(): Boolean = launcherApps.hasShortcutHostPermission()

        // A quiet work profile and a locked private space alike (Decisions "No burst": `profile quiet`).
        override fun isQuiet(query: QuickTarget.Query): Boolean = catalog.isQuietMode(query.user)

        override fun shortcuts(query: QuickTarget.Query): List<ShortcutCandidate> {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(query.activity.packageName)
                .setActivity(query.activity)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
            val list = launcherApps.getShortcuts(q, query.user).orEmpty()
            list.forEach { infos[it.id] = it }
            return list.map { ShortcutCandidate(it.id, it.isDeclaredInManifest, it.rank, it.isEnabled) }
        }

        override fun describe(query: QuickTarget.Query): String =
            "${query.activity.flattenToShortString()}/${query.user.hashCode()}"
    }
}
