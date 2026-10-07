package app.tileshell.music.handoff

import android.content.Context
import android.content.Intent
import app.tileshell.BuildConfig
import app.tileshell.video.handoff.StreamingHandoff

/**
 * Music's "Listen on <app>" (phase 20 build task 8; r3 D3): the thin Android half of [MusicServicesTable]. It opens
 * other apps only — it plays nothing, signs in to nothing and reads no service's catalogue (Scope, Out).
 */
object MusicHandoff {
    /**
     * The "Listen on" list as the phone is NOW: asked of the package manager at every call and never kept, so an app
     * installed a minute ago appears and one just removed is gone. (`<queries>` is not needed: the shell holds
     * `QUERY_ALL_PACKAGES`.)
     */
    fun entries(context: Context): List<MusicHandoffEntry> {
        val pm = context.packageManager
        val installed = MusicServicesTable.services(BuildConfig.DEBUG).map { it.packageName }.filterTo(HashSet()) { pm.getLaunchIntentForPackage(it) != null }
        val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC), 0)
            .map { MusicLauncher(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        return MusicServicesTable.entries(installed, launchers, context.packageName, BuildConfig.DEBUG)
    }

    /**
     * A tap on "Listen on <entry>" for a title: the entry's plans tried in order, each try's `[music] handoff:` line
     * written. On [StreamingHandoff.Opened.NOT_INSTALLED] the caller shows [MusicServicesTable.TEXT_NOT_INSTALLED].
     */
    fun open(context: Context, entry: MusicHandoffEntry, title: String, artist: String): StreamingHandoff.Opened =
        StreamingHandoff.openPlans(context, entry.packageName, MusicServicesTable.plan(entry, title, artist)) { plan, opened ->
            MusicServicesTable.line(entry, title, plan, opened)
        }
}
