package app.tileshell

import android.app.Application
import android.os.Handler
import android.os.Looper
import app.tileshell.apps.AppCatalog
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.CalendarFeed
import app.tileshell.feeds.MusicFeed
import app.tileshell.feeds.PhotosFeed
import app.tileshell.tiles.engine.BadgeStore

/** Process entry: builds the app catalog and starts every live tile feed the granted permissions allow. */
class ShellApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.add("app", "process start")
        AppCatalog.get(this)
        startFeeds("process start")
        startBadgeExpirySweep()
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
