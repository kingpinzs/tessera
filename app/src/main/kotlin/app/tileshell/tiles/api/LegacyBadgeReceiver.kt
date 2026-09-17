package app.tileshell.tiles.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.engine.BadgeStore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Legacy badge broadcasts (R5 §2.5, source S3): `android.intent.action.BADGE_COUNT_UPDATE` and ShortcutBadger's
 * `me.leolin.shortcutbadger.BADGE_COUNT_UPDATE`, extras `badge_count` (int), `badge_count_package_name`,
 * `badge_count_class_name`.
 *
 * Declared in the manifest (so senders' queryBroadcastReceivers finds the shell, and explicit-by-package sends arrive)
 * and registered on the Application context with RECEIVER_EXPORTED (which is what receives the implicit copy on
 * API 26+). Both instances may see one broadcast; duplicates within 2 s are dropped.
 *
 * Identity: getSentFromPackage() (API 34+) must equal badge_count_package_name. Android only fills that in when the
 * sender opted into sharing its identity, which the ShortcutBadger senders do not, so an unverified broadcast is
 * refused by default: otherwise any installed app could set or clear the badge of any other app by naming it in the
 * extra (adversarial review F1). Settings > Live tile access has a switch that accepts unverified senders again for
 * people who want the old behaviour. Accepted badges also honour the per-app kill switch and the master switch, and
 * a sender is rate limited.
 */
class LegacyBadgeReceiver(private val via: String = "manifest") : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ACTIONS) return
        val sentPkg = sentFromPackage
        val sentUid = sentFromUid
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val cls = intent.getStringExtra(EXTRA_CLASS)
        val count = intent.getIntExtra(EXTRA_COUNT, Int.MIN_VALUE)
        val who = "sender=${sentPkg ?: "unknown"} senderUid=${if (sentUid == Process.INVALID_UID) "invalid" else sentUid} via=$via action=$action"

        if (pkg.isNullOrBlank()) return log("reject ? $who reason=package: no $EXTRA_PACKAGE")
        if (count == Int.MIN_VALUE || count < 0) return log("reject $pkg $who reason=count: $EXTRA_COUNT missing, not an int, or negative")
        val installed = try {
            context.packageManager.getPackageInfo(pkg, 0); true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        if (!installed) return log("reject $pkg $who reason=package: not installed")
        if (sentPkg != null && sentPkg != pkg) return log("reject $pkg $who reason=identity: sender is not the badge's package")
        if (sentPkg == null && !LiveTileSettings.trustsUnverifiedLegacyBadges(context)) {
            return log("reject $pkg $who reason=identity: the sender did not share its identity (Settings > Live tile access can accept these)")
        }
        if (!LiveTileSettings.isApiEnabled(context)) return log("reject $pkg $who reason=disabled: live tiles are off for every app")
        if (LiveTileSettings.isDisabled(context, pkg)) return log("reject $pkg $who reason=disabled: this app's live tile is off")
        val limiterKey = if (sentPkg != null) "sender:$sentPkg" else "uid:$sentUid|pkg:$pkg"
        if (!limiter.tryAcquire(limiterKey, SystemClock.elapsedRealtime())) {
            return log("reject $pkg $who reason=rate: more than $RATE_LIMIT badge broadcasts a minute")
        }

        val key = "$sentUid|$pkg|$count"
        val now = SystemClock.elapsedRealtime()
        synchronized(recent) {
            recent.entries.removeAll { now - it.value > DEDUPE_MS }
            if (recent.containsKey(key)) return log("duplicate $pkg count=$count $who (within ${DEDUPE_MS}ms)")
            recent[key] = now
        }
        lastSeen = LastSeen(pkg, sentPkg, count, System.currentTimeMillis())
        log("accept $pkg count=$count class=$cls $who${if (sentPkg == null) " UNVERIFIED (sender identity not shared)" else " verified"}")
        BadgeStore.set(pkg, BadgeStore.Source.LEGACY_BROADCAST, minOf(count, LiveTileStore.MAX_BADGE))
    }

    private fun log(message: String) {
        Diagnostics.add("livetile", "legacy $message")
    }

    data class LastSeen(val pkg: String, val sender: String?, val count: Int, val wallMs: Long)

    companion object {
        /** Badge broadcasts are not the provider, so they carry their own limiter (adversarial review F1). */
        const val RATE_LIMIT = 30
        private val limiter = RateLimiter(limit = RATE_LIMIT, windowMs = 60_000L)

        const val ACTION_BADGE = "android.intent.action.BADGE_COUNT_UPDATE"
        const val ACTION_SHORTCUTBADGER = "me.leolin.shortcutbadger.BADGE_COUNT_UPDATE"
        const val EXTRA_COUNT = "badge_count"
        const val EXTRA_PACKAGE = "badge_count_package_name"
        const val EXTRA_CLASS = "badge_count_class_name"
        private val ACTIONS = setOf(ACTION_BADGE, ACTION_SHORTCUTBADGER)
        private const val DEDUPE_MS = 2_000L
        private val recent = HashMap<String, Long>()
        private val registered = AtomicBoolean(false)

        /** Most recent accepted legacy badge broadcast (onboarding checklist row "Legacy badge broadcasts seen"). */
        @Volatile var lastSeen: LastSeen? = null
            private set

        fun registerRuntime(app: Context) {
            if (!registered.compareAndSet(false, true)) return
            val filter = IntentFilter().apply { ACTIONS.forEach { addAction(it) } }
            app.registerReceiver(LegacyBadgeReceiver(via = "runtime"), filter, Context.RECEIVER_EXPORTED)
            Diagnostics.add("livetile", "legacy badge receiver registered at runtime (RECEIVER_EXPORTED)")
        }

        fun forget(pkg: String) {
            synchronized(recent) { recent.keys.removeAll { it.split('|').getOrNull(1) == pkg } }
            if (lastSeen?.pkg == pkg) lastSeen = null
        }
    }
}
