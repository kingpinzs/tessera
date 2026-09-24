package app.tileshell

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import app.tileshell.apps.AppCatalog
import app.tileshell.apps.ProfileKind
import app.tileshell.cortana.reminders.ReminderScheduler
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.CalendarFeed
import app.tileshell.feeds.MusicFeed
import app.tileshell.feeds.PhotosFeed
import android.content.ComponentName
import app.tileshell.tiles.CategoryFolders
import app.tileshell.tiles.Slot
import app.tileshell.tiles.LayoutStore
import app.tileshell.tiles.ShellTiles
import app.tileshell.tiles.TileKey
import app.tileshell.tiles.TileSize
import app.tileshell.tiles.engine.BadgeStore

/** Process entry: builds the app catalog and starts every live tile feed the granted permissions allow. */
class ShellApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Application.onCreate runs in EVERY process of the app. The launcher's start-up work — the
        // catalog, the layout-store ADDs, the feeds, the reminder alarms — belongs to the main process
        // alone: the keyboard's `:ime` process (phase 05) and the `:speech` process must not seed
        // start_layout.json or start a second set of feeds beside the launcher's.
        val process = getProcessName()
        if (process != packageName) {
            Diagnostics.add("app", "process start: $process (no launcher start-up work here)")
            return
        }
        // Direct boot (phase 15, T15-22): an alarm can start this process before the first unlock after a reboot,
        // when credential storage — the catalog, the layout, the theme, every feed's data — cannot be read. The
        // clock's receiver, ring service and ring activity work from device-protected storage on their own; the
        // launcher's start-up waits for the unlock.
        if (!getSystemService(UserManager::class.java).isUserUnlocked) {
            Diagnostics.add("app", "process start before the first unlock: launcher start-up waits for it")
            registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    runCatching { unregisterReceiver(this) }
                    Diagnostics.add("app", "user unlocked: launcher start-up")
                    startLauncher()
                }
            }, IntentFilter(Intent.ACTION_USER_UNLOCKED), RECEIVER_NOT_EXPORTED)
            return
        }
        Diagnostics.add("app", "process start")
        startLauncher()
    }

    private fun startLauncher() {
        followPackageChanges(AppCatalog.get(this))
        addCortanaTile()
        addCategoryFolders()
        claimMusicSlot()
        startFeeds("process start")
        // Phase 15 build task 4: the next-alarm face on the Alarms & Clock tile and the pinned timer / stopwatch faces.
        app.tileshell.clock.ClockTiles.start(this)
        startBadgeExpirySweep()
        // A reboot, an app update and `am force-stop` all cancel alarms and proximity alerts, so every
        // process start re-arms what the reminder store holds (phase 03 Decisions; E6).
        ReminderScheduler.rearm(this, "process start")
    }

    /**
     * Phase 03's Cortana tile. It is ADDed rather than put in the default layout, so it appears on a
     * phone that already has a persisted phase 01/02 layout — and the store records that the ADD ran, so
     * a Cortana tile unpinned in phase 02's edit mode does not come back after a restart, reboot or
     * update (Decisions "The Cortana slot ADD runs once"; E6).
     */
    private fun addCortanaTile() {
        LayoutStore.get(this).addOnce(
            ShellTiles.CORTANA_ADD,
            TileKey.ShellTile(ShellTiles.CORTANA),
            TileSize.MEDIUM,
        )
    }

    /**
     * The Games and Office folders (Jeremy, 2026-09-22: "there should be games and office folders").
     *
     * Seeded from what each app declares about itself — `android:appCategory`, which Android returns as
     * [android.content.pm.ApplicationInfo.category] — rather than from a list of package names this
     * shell would have to keep up to date. [CategoryFolders] holds the rules; this reads the catalog and
     * asks the package manager, and does neither when the ADD has already run, because the scan costs a
     * call per installed app and the answer would be thrown away.
     *
     * Both folders follow the same one-shot contract as the Cortana tile: seeded once, and a folder the
     * user deletes stays deleted. The contents are an ordinary folder afterwards — edit mode can add to
     * it, take from it and rename it, and nothing here touches it again.
     */
    private fun addCategoryFolders() {
        val store = LayoutStore.get(this)
        if (store.hasAdded(CategoryFolders.GAMES_ADD) && store.hasAdded(CategoryFolders.OFFICE_ADD)) return
        val catalog = AppCatalog.get(this)
        val candidates = catalog.apps.value
            // The main profile only. A work or private-space app belongs behind its own header in the
            // app list, not swept into a folder on Start where it is the first thing anyone sees.
            .filter { it.profile == ProfileKind.MAIN && it.component.packageName != packageName }
            .distinctBy { it.component.packageName }
            .mapNotNull { entry ->
                val category = try {
                    packageManager.getApplicationInfo(entry.component.packageName, 0).category
                } catch (e: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }
                CategoryFolders.Candidate(
                    key = TileKey.AppTile(entry.component, entry.user),
                    label = entry.label,
                    category = category,
                    installedAtMs = entry.firstInstallTime,
                )
            }
        Diagnostics.add("layout", "category folders: scanned ${candidates.size} apps")
        store.addFolderOnce(
            CategoryFolders.GAMES_ADD,
            CategoryFolders.GAMES_NAME,
            CategoryFolders.members(candidates, android.content.pm.ApplicationInfo.CATEGORY_GAME),
            TileSize.MEDIUM,
        )
        store.addFolderOnce(
            CategoryFolders.OFFICE_ADD,
            CategoryFolders.OFFICE_NAME,
            CategoryFolders.members(candidates, android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY),
            TileSize.MEDIUM,
        )
    }

    /**
     * Point the MUSIC slot at the shell's own player, once (phase 10 build task 1).
     *
     * Phase 10 Q5 said no carve-out would be needed because the player declares APP_MUSIC like any
     * music app. That was wrong, and the correction is recorded in the phase doc: [SlotResolver]
     * auto-assigns a category slot only when Android resolves exactly ONE handler or a user-set
     * default, so on any phone that also has another music app the slot would sit unassigned and the
     * tile would read "Tap to choose". Seeding the assignment is what the user would otherwise do by
     * hand, it runs once, and re-pointing the slot at another player still works and still sticks.
     */
    private fun claimMusicSlot() {
        LayoutStore.get(this).assignSlotOnce(
            MUSIC_SLOT_CLAIM,
            Slot.MUSIC,
            ComponentName(this, app.tileshell.music.MusicActivity::class.java),
        )
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

        /** The one-shot marker for phase 10's MUSIC slot claim; versioned like the folder ADDs. */
        const val MUSIC_SLOT_CLAIM = "slot:music:v1"
    }
}
