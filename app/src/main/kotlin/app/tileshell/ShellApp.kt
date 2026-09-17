package app.tileshell

import android.app.Application
import app.tileshell.apps.AppCatalog
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.CalendarFeed
import app.tileshell.feeds.MusicFeed
import app.tileshell.feeds.PhotosFeed

/** Process entry: builds the app catalog and starts every live tile feed the granted permissions allow. */
class ShellApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.add("app", "process start")
        AppCatalog.get(this)
        startFeeds("process start")
    }

    /** Called again by the onboarding checklist after a grant changes. */
    fun startFeeds(reason: String) {
        PhotosFeed.start(this)
        CalendarFeed.start(this)
        MusicFeed.start(this)
        Diagnostics.add("app", "feeds started ($reason)")
    }
}
