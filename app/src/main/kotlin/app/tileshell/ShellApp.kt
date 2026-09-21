package app.tileshell

import android.app.Application
import android.os.Handler
import android.os.Looper
import app.tileshell.apps.AppCatalog
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.CalendarFeed
import app.tileshell.feeds.MusicFeed
import app.tileshell.feeds.PhotosFeed
import app.tileshell.tiles.LayoutStore
import app.tileshell.tiles.TileKey
import app.tileshell.tiles.engine.BadgeStore

/** Process entry: builds the app catalog and starts every live tile feed the granted permissions allow. */
class ShellApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.add("app", "process start")
        followPackageChanges(AppCatalog.get(this))
        startFeeds("process start")
        startBadgeExpirySweep()
    }

    /**
     * Package changes drive the Start layout (phase 02 build task 5, Decisions "App uninstall/update handling is
     * in (M12)"). The wiring lives in the process, not in StartActivity, so an uninstall is followed while the
     * shell is alive with no UI at all; [AppCatalog] owns the one LauncherApps callback and reports only
     * uninstalls, so an update (`adb install -r`), a disable and an unavailable package all keep their tiles.
     */
    private fun followPackageChanges(catalog: AppCatalog) {
        val layout = LayoutStore.get(this)
        catalog.addPackageRemovedListener { packages, _ -> layout.onPackagesRemoved(packages) }
        // An uninstall the shell slept through delivers no callback at all, so the layout is reconciled once per
        // process against what the package manager still knows. Only a package that is definitely gone loses its
        // tiles: disabled, unavailable and unresolvable packages keep them.
        val gone = layout.layout.value.allKeys().mapNotNull(::packageOf).distinct()
            .filter { catalog.packageState(it) == AppCatalog.PackageState.GONE }
        if (gone.isEmpty()) return
        Diagnostics.add("app", "packages gone while the shell was not running: $gone")
        layout.onPackagesRemoved(gone.toSet())
    }

    /** The package a tile belongs to; null for tiles no package owns (slots resolve at runtime, folders, shell tiles). */
    private fun packageOf(key: TileKey): String? = when (key) {
        is TileKey.AppTile -> key.component.packageName
        is TileKey.SecondaryTile -> key.owner
        else -> null
    }

    /**
     * A legacy badge expires three days after it arrived (R5 rule 4), but nothing recomputes the counts while the
     * device is idle, so the expiry is evaluated on a timer as well as on every badge change.
     */
    private fun startBadgeExpirySweep() {
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                BadgeStore.sweep()
                handler.postDelayed(this, BADGE_SWEEP_MS)
            }
        }
        handler.postDelayed(tick, BADGE_SWEEP_MS)
    }

    /** Called again by the onboarding checklist after a grant changes. */
    fun startFeeds(reason: String) {
        PhotosFeed.start(this)
        CalendarFeed.start(this)
        MusicFeed.start(this)
        app.tileshell.weather.WeatherFeed.start(this)
        Diagnostics.add("app", "feeds started ($reason)")
    }

    companion object {
        /** R5 rule 4: expiry is evaluated on a 15-minute timer as well as on every badge change. */
        const val BADGE_SWEEP_MS = 15L * 60 * 1000
    }
}
