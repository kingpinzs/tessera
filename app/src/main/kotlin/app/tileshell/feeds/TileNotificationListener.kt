package app.tileshell.feeds

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.engine.BadgeStore
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import java.io.FileDescriptor
import java.io.PrintWriter

/**
 * Notification-driven live tiles for every app (Q5, interview Q2). Counts follow AOSP Launcher3's badge
 * rule (sum of max(1, Notification.number) over badge-eligible notifications) as the lowest-precedence
 * source in [BadgeStore]; previews come from the newest notifications.
 * Its dump() is the release-build read path for the diagnostics ring buffer (phase 01 Decisions).
 */
class TileNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = true
        Diagnostics.add("notif", "listener connected")
        rescan("connected")
    }

    override fun onListenerDisconnected() {
        connected = false
        Diagnostics.add("notif", "listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Diagnostics.add("notif", "posted ${sbn.packageName} key=${sbn.key} postTime=${sbn.postTime}")
        rebuild(sbn.packageName, sbn.postTime)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Diagnostics.add("notif", "removed ${sbn.packageName} key=${sbn.key}")
        rebuild(sbn.packageName, System.currentTimeMillis())
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        rescan("ranking")
    }

    private fun active(): List<StatusBarNotification> = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()

    private fun rescan(reason: String) {
        val byPkg = active().groupBy { it.packageName }
        val known = LiveTileEngine.content.value.keys.filter { it.startsWith("pkg:") }.map { it.removePrefix("pkg:") }
        (byPkg.keys + known).toSet().forEach { rebuild(it, byPkg[it]?.maxOf { n -> n.postTime } ?: System.currentTimeMillis()) }
        Diagnostics.add("notif", "rescan ($reason): ${byPkg.size} packages with notifications")
    }

    private fun eligible(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val ranking = Ranking()
        val ranked = runCatching { currentRanking.getRanking(sbn.key, ranking) }.getOrDefault(false)
        if (ranked && !ranking.canShowBadge()) return false
        if (sbn.isOngoing && (!ranked || ranking.channel?.id == "miscellaneous")) return false
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)
        return !(title.isNullOrBlank() && text.isNullOrBlank())
    }

    private fun rebuild(pkg: String, sourceTime: Long) {
        val mine = active().filter { it.packageName == pkg && eligible(it) }.sortedByDescending { it.postTime }
        val count = mine.sumOf { maxOf(1, it.notification.number) }
        BadgeStore.set(pkg, BadgeStore.Source.NOTIFICATIONS, count)
        val faces = mine.take(3).map { sbn ->
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            TileFace.TextLines(listOf(title, text).filter { it.isNotBlank() })
        }
        LiveTileEngine.publish(
            LiveTileEngine.packageKey(pkg),
            if (faces.isEmpty()) null else TileContent(faces, FaceTransition.FLIP, sourceTimeMs = sourceTime, sourceTag = "notification"),
        )
    }

    override fun dump(fd: FileDescriptor?, writer: PrintWriter, args: Array<out String>?) {
        writer.println("listener connected=$connected")
        writer.println("badges=${BadgeStore.counts.value}")
        Diagnostics.dump(writer)
    }

    companion object {
        @Volatile var connected: Boolean = false
            private set
    }
}
