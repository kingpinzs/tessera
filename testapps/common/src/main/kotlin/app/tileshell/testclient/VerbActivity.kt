package app.tileshell.testclient

import android.Manifest
import android.app.Activity
import android.app.BroadcastOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import app.tileshell.livetile.client.BadgeGlyph
import app.tileshell.livetile.client.BadgeUpdater
import app.tileshell.livetile.client.ImagePlacement
import app.tileshell.livetile.client.LiveTile
import app.tileshell.livetile.client.LiveTileResult
import app.tileshell.livetile.client.TextStyle
import app.tileshell.livetile.client.TileUpdater
import app.tileshell.livetile.client.tileContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Button-free QA driver for phase 01 rows E15-E17. Each launch performs the verb named in the intent extras and shows
 * (and logs, tag "TileClient") the result, e.g.
 *
 *   adb shell am start -n app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity --es verb tile.update --es text "Hi|Line two"
 *
 * Verbs and extras:
 *   tile.update      text ("|"-separated lines), tag, expiresInMs (long), image (bool: peek image from this app's own
 *                    provider), bigImage (bool: > 200 KB image), imageAuthority (string: use another authority, no grant),
 *                    xml (raw payload, overrides the rest)
 *   tile.clear
 *   tile.enableQueue enabled (bool, default true)
 *   tile.schedule    id (default s1), deliverInMs (long, default 30000), text, tag, expiresInMs
 *   badge.update     value (int) or glyph (string)
 *   badge.clear
 *   tile.setting
 *   demo.queue       enables the queue and sends five tagged updates (q1..q5)
 *   owner.attack     tile.update and badge.update carrying owner=<target> (default app.tileshell.testclient.a)
 *   flood            count (int, default 70) tile.setting calls; reports ok / rate
 *   notify           number (int, default 7), title, text: a notification with setNumber
 *   notify.cancel
 *   legacyBadge      count (int, default 3), action (android|shortcutbadger), explicit (bool), badgePackage (default own)
 *
 * tileclient-b alone has its own uid (owner.attack -> error "identity"); once tileclient-b2 is installed they share a
 * uid and every call from either answers error "shared-uid".
 */
class VerbActivity : Activity() {
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            setPadding(32, 96, 32, 32)
            textSize = 14f
            setTextIsSelectable(true)
        }
        setContentView(ScrollView(this).apply { addView(output) })
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val verb = intent.getStringExtra("verb")
        if (verb == null) {
            show("${packageName}\nshell available=${LiveTile.isAvailable(this)}\nno verb given (see VerbActivity docs)")
            return
        }
        val extras = intent.extras ?: Bundle()
        show("$verb: running…")
        Thread {
            val text = try {
                perform(verb, extras)
            } catch (e: Exception) {
                "$verb: exception ${e.javaClass.simpleName}: ${e.message}"
            }
            Log.i(TAG, "$packageName $text")
            runOnUiThread { show(text) }
        }.start()
    }

    private fun show(text: String) {
        output.text = "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}  $packageName\n$text"
    }

    private fun fmt(verb: String, r: LiveTileResult) =
        "$verb: ok=${r.ok} error=${r.error} detail=${r.detail}" + (r.setting?.let { " setting=$it" } ?: "")

    private fun perform(verb: String, x: Bundle): String {
        val tiles = TileUpdater.forApplication(this)
        val badges = BadgeUpdater.forApplication(this)
        val now = System.currentTimeMillis()
        return when (verb) {
            "tile.update" -> {
                val xml = x.getString("xml")
                val expires = x.getLong("expiresInMs", 0L).takeIf { it > 0 }?.let { now + it }
                val tag = x.getString("tag")
                if (xml != null) return fmt(verb, tiles.update(xml, tag = tag, expiresAtMs = expires))
                val authority = x.getString("imageAuthority")
                if (authority != null) {
                    val raw = """<tile><visual><binding template="TileMedium"><image src="content://$authority/peek.png" placement="peek"/>""" +
                        """<text>deputy</text></binding></visual></tile>"""
                    // Raw call without a grant: this app cannot grant another authority, and the shell must refuse it before reading.
                    return rawCall(verb, Bundle().apply { putString("xml", raw) })
                }
                val lines = (x.getString("text") ?: "Hello from $packageName|${stamp()}").split('|')
                val image = when {
                    x.getBoolean("bigImage", false) -> TestImageProvider.uri(this, TestImageProvider.BIG)
                    x.getBoolean("image", false) -> TestImageProvider.uri(this, TestImageProvider.PEEK)
                    else -> null
                }
                fmt(verb, tiles.update(content(lines, image), tag, expires))
            }
            "tile.clear" -> fmt(verb, tiles.clear())
            "tile.enableQueue" -> fmt(verb, tiles.enableNotificationQueue(x.getBoolean("enabled", true)))
            "tile.schedule" -> {
                val deliverAt = now + x.getLong("deliverInMs", 30_000L)
                val expires = x.getLong("expiresInMs", 0L).takeIf { it > 0 }?.let { deliverAt + it }
                val lines = (x.getString("text") ?: "Scheduled|for ${stamp(deliverAt)}").split('|')
                fmt(verb, tiles.schedule(x.getString("id") ?: "s1", deliverAt, content(lines, null), x.getString("tag"), expires))
            }
            "badge.update" -> {
                val glyph = x.getString("glyph")
                if (glyph != null) {
                    val g = BadgeGlyph.entries.firstOrNull { it.value == glyph } ?: return "$verb: unknown glyph $glyph"
                    fmt(verb, badges.update(g))
                } else {
                    fmt(verb, badges.update(x.getInt("value", 5)))
                }
            }
            "badge.clear" -> fmt(verb, badges.clear())
            "tile.setting" -> {
                val r = TileUpdater.forApplication(this)
                "tile.setting: ${r.setting()}"
            }
            "demo.queue" -> {
                val results = mutableListOf(fmt("tile.enableQueue", tiles.enableNotificationQueue(true)))
                for (i in 1..5) results += fmt("tile.update q$i", tiles.update(content(listOf("Queue item $i", stamp()), null), tag = "q$i"))
                results.joinToString("\n")
            }
            "owner.attack" -> {
                val target = x.getString("owner") ?: "app.tileshell.testclient.a"
                val xml = """<tile><visual><binding template="TileMedium"><text>spoofed by $packageName</text></binding></visual></tile>"""
                val update = rawCall("tile.update", Bundle().apply { putString("xml", xml); putString("owner", target) })
                val badge = rawCall("badge.update", Bundle().apply { putInt("value", 42); putString("owner", target) })
                "owner.attack target=$target\n$update\n$badge"
            }
            "flood" -> {
                val n = x.getInt("count", 70)
                val results = (1..n).map { rawCall("tile.setting", Bundle()) }
                val ok = results.count { it.contains("ok=true") }
                val rate = results.count { it.contains("error=rate") }
                "flood: $n calls, ok=$ok rate=$rate other=${n - ok - rate}"
            }
            "notify" -> notifyWithNumber(x.getInt("number", 7), x.getString("title") ?: "Tile client", x.getString("text") ?: "setNumber test")
            "notify.cancel" -> {
                getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                "notify.cancel: done"
            }
            "legacyBadge" -> sendLegacyBadge(x)
            else -> "unknown verb $verb"
        }
    }

    private fun content(lines: List<String>, image: Uri?) = tileContent {
        small { text(lines.first(), TextStyle.CAPTION) }
        medium {
            image?.let { image(it, ImagePlacement.PEEK) }
            lines.forEachIndexed { i, l -> text(l, if (i == 0) TextStyle.BASE else TextStyle.CAPTION_SUBTLE) }
        }
        wide {
            image?.let { image(it, ImagePlacement.BACKGROUND) }
            lines.forEach { text(it) }
        }
    }

    /** Direct ContentResolver.call, bypassing the client library (for identity attacks the library never sends). */
    private fun rawCall(method: String, extras: Bundle): String = try {
        val out = contentResolver.call(Uri.parse("content://${LiveTile.AUTHORITY}"), method, null, extras.apply { putInt("v", 1) })
        "$method: ok=${out?.getBoolean("ok")} error=${out?.getString("error")} detail=${out?.getString("detail")}"
    } catch (e: Exception) {
        "$method: exception ${e.javaClass.simpleName}: ${e.message}"
    }

    private fun notifyWithNumber(number: Int, title: String, text: String): String {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
            return "notify: POST_NOTIFICATIONS not granted (adb shell pm grant $packageName android.permission.POST_NOTIFICATIONS)"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Tile client", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(true) })
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setNumber(number)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        return "notify: posted id=$NOTIFICATION_ID number=$number"
    }

    /** ShortcutBadger's delivery: resolve manifest receivers first, then send (implicit by default) with identity shared. */
    private fun sendLegacyBadge(x: Bundle): String {
        val action = if (x.getString("action") == "shortcutbadger") "me.leolin.shortcutbadger.BADGE_COUNT_UPDATE" else "android.intent.action.BADGE_COUNT_UPDATE"
        val count = x.getInt("count", 3)
        val badgePackage = x.getString("badgePackage") ?: packageName
        val intent = Intent(action)
            .putExtra("badge_count", count)
            .putExtra("badge_count_package_name", badgePackage)
            .putExtra("badge_count_class_name", VerbActivity::class.java.name)
        val receivers = packageManager.queryBroadcastReceivers(intent, 0)
        if (receivers.isEmpty()) return "legacyBadge: unable to resolve intent (no manifest receiver for $action)"
        val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
        val explicit = x.getBoolean("explicit", false)
        if (explicit) {
            receivers.map { it.activityInfo.packageName }.distinct().forEach { sendBroadcast(Intent(intent).setPackage(it), null, options) }
        } else {
            sendBroadcast(intent, null, options)
        }
        return "legacyBadge: sent $action count=$count package=$badgePackage explicit=$explicit receivers=${receivers.map { it.activityInfo.packageName + "/" + it.activityInfo.name }}"
    }

    private fun stamp(ms: Long = System.currentTimeMillis()) = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

    private companion object {
        const val TAG = "TileClient"
        const val CHANNEL = "tileclient"
        const val NOTIFICATION_ID = 7
    }
}
