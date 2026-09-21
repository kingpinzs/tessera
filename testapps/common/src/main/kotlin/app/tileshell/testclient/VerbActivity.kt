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
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.tileshell.livetile.client.BadgeGlyph
import app.tileshell.livetile.client.BadgeUpdater
import app.tileshell.livetile.client.ImagePlacement
import app.tileshell.livetile.client.LiveTile
import app.tileshell.livetile.client.LiveTileResult
import app.tileshell.livetile.client.SecondaryTile
import app.tileshell.livetile.client.TextStyle
import app.tileshell.livetile.client.TileSize
import app.tileshell.livetile.client.TileUpdater
import app.tileshell.livetile.client.tileContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * QA driver for phase 01 rows E15-E17 and phase 02 row E5. Each launch performs the verb named in the intent extras
 * and shows (and logs, tag "TileClient") the result, e.g.
 *
 *   adb shell am start -n app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity --es verb tile.update --es text "Hi|Line two"
 *
 * Verbs and extras:
 *   tile.update      text ("|"-separated lines), tag, expiresInMs (long), image (bool: peek image from this app's own
 *                    provider), bigImage (bool: > 200 KB image), slowImage (bool: a pipe the provider never writes,
 *                    for the shell's read deadline), imageAuthority (string: use another authority, no grant),
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
 *   legacyBadge      count (int, default 3), action (android|shortcutbadger), explicit (bool), badgePackage (default own),
 *                    shareIdentity (bool, default true; false is how the real ShortcutBadger senders send)
 *
 * Secondary tiles (phase 02, E5). Every tile / badge verb above takes a `tileId` extra to address one of this app's
 * secondary tiles instead of its own tile:
 *   secondary.requestCreate  tileId (default st1), displayName, arguments, size (small|medium|wide), showName (bool),
 *                            logo (bool: this app's own image provider), logoAuthority (string: someone else's)
 *   secondary.update         the same extras; the tile must already be pinned
 *   secondary.requestDelete  tileId
 *   secondary.exists         tileId
 *   secondary.findAll
 * The PIN TILE button runs secondary.requestCreate with those same extras, so E5 can drive it by hand as Windows
 * requires ("The user must explicitly click a Pin button within your app").
 *
 * When the shell launches this app from a secondary tile, the top line of the screen shows what the tile sent:
 * `TILE_ID=... ARGS=... ACTIVATED=...`, which `uiautomator dump` reads (dumpsys activity prints only "(has extras)").
 *
 * tileclient-b alone has its own uid (owner.attack -> error "identity"); once tileclient-b2 is installed they share a
 * uid and every call from either answers error "shared-uid".
 */
class VerbActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var launchArgs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchArgs = TextView(this).apply {
            setPadding(32, 96, 32, 8)
            textSize = 14f
            setTextIsSelectable(true)
        }
        val pin = Button(this).apply {
            text = "PIN TILE"
            contentDescription = "pin_tile_button"
            setOnClickListener { pinFromButton() }
        }
        output = TextView(this).apply {
            setPadding(32, 8, 32, 32)
            textSize = 14f
            setTextIsSelectable(true)
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(launchArgs)
                addView(pin, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(ScrollView(this@VerbActivity).apply { addView(output) })
            },
        )
        showLaunchArgs(intent)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showLaunchArgs(intent)
        handle(intent)
    }

    /**
     * What the tile that started us sent (E5 reads this line with `uiautomator dump`, because `dumpsys activity`
     * prints only "(has extras)" for an intent's extras).
     */
    private fun showLaunchArgs(intent: Intent?) {
        val tileId = LiveTile.tileId(intent)
        val args = LiveTile.arguments(intent)
        val activated = LiveTile.activatedArguments(intent)
        val text = "TILE_ID=${tileId ?: "-"} ARGS=${args ?: "-"} ACTIVATED=${activated.joinToString(",").ifEmpty { "-" }}"
        launchArgs.text = text
        launchArgs.contentDescription = text
        Log.i(TAG, "$packageName launched with $text")
    }

    /** Windows: only the user may pin a secondary tile, from a button inside the app (R5 1.9). */
    private fun pinFromButton() {
        val x = intent?.extras ?: Bundle()
        show("secondary.requestCreate: running...")
        Thread {
            val text = runCatching { secondaryCreate("secondary.requestCreate", x) }.getOrElse { "secondary.requestCreate: exception $it" }
            Log.i(TAG, "$packageName $text")
            runOnUiThread { show(text) }
        }.start()
    }

    private fun secondaryTile(x: Bundle): SecondaryTile = LiveTile.forSecondaryTile(this, x.getString("tileId") ?: "st1")

    private fun secondaryCreate(verb: String, x: Bundle): String {
        val tile = secondaryTile(x)
        val logo = when {
            x.getString("logoAuthority") != null -> Uri.parse("content://" + x.getString("logoAuthority") + "/peek.png")
            x.getBoolean("logo", false) -> TestImageProvider.uri(this, TestImageProvider.PEEK)
            else -> null
        }
        val size = TileSize.entries.firstOrNull { it.value == x.getString("size") } ?: TileSize.MEDIUM
        val displayName = x.getString("displayName") ?: "Test tile"
        val arguments = x.getString("arguments") ?: "tile-args-${tile.tileId}"
        val r = if (verb == "secondary.update") {
            tile.update(displayName, arguments, logo, size, x.getBoolean("showName", false))
        } else {
            tile.requestCreate(displayName, arguments, logo, size, x.getBoolean("showName", false))
        }
        return "$verb tileId=${tile.tileId} arguments=$arguments: ok=${r.ok} error=${r.error} pending=${r.pending} detail=${r.detail}"
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
        // A tileId extra sends the tile / badge verbs to one of this app's secondary tiles instead of its own tile.
        val tileIdExtra = x.getString("tileId")
        val tiles = if (tileIdExtra == null) TileUpdater.forApplication(this) else TileUpdater.forSecondaryTile(this, tileIdExtra)
        val badges = if (tileIdExtra == null) BadgeUpdater.forApplication(this) else BadgeUpdater.forSecondaryTile(this, tileIdExtra)
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
                    x.getBoolean("slowImage", false) -> TestImageProvider.uri(this, TestImageProvider.SLOW)
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
            "tile.setting" -> "tile.setting: ${tiles.setting()}"
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
            "notify" -> notifyWithNumber(x)
            "xml.big" -> {
                // A payload past the shell's 8 KB XML cap.
                val pad = "x".repeat(x.getInt("bytes", 9000))
                fmt(verb, tiles.update("""<tile><visual><binding template="TileMedium"><text>$pad</text></binding></visual></tile>"""))
            }
            "images.many" -> {
                // More distinct images than the shell's per-payload cap.
                val count = x.getInt("count", 14)
                val images = (1..count).joinToString("") { """<image src="content://$packageName.images/peek$it.png" placement="inline"/>""" }
                fmt(verb, tiles.update("""<tile><visual><binding template="TileMedium">$images<text>many</text></binding></visual></tile>"""))
            }
            "notify.cancel" -> {
                val nm = getSystemService(NotificationManager::class.java)
                if (x.containsKey("id")) nm.cancel(x.getInt("id")) else nm.cancelAll()
                "notify.cancel: done"
            }
            "legacyBadge" -> sendLegacyBadge(x)
            "secondary.requestCreate", "secondary.update" -> secondaryCreate(verb, x)
            "secondary.requestDelete" -> {
                val tile = secondaryTile(x)
                val r = tile.requestDelete()
                "$verb tileId=${tile.tileId}: ok=${r.ok} error=${r.error} detail=${r.detail}"
            }
            "secondary.exists" -> {
                val tile = secondaryTile(x)
                "$verb tileId=${tile.tileId}: ${tile.exists()}"
            }
            "secondary.findAll" -> "secondary.findAll: ${SecondaryTile.findAll(this)}"
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

    /**
     * notify extras: number, title, text, id (default NOTIFICATION_ID), group (string), summary (bool: post it as the
     * group summary), ongoing (bool), badgeOff (bool: a channel with setShowBadge(false)), visibility
     * (public|private|secret), empty (bool: no title and no text).
     */
    private fun notifyWithNumber(x: Bundle): String {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
            return "notify: POST_NOTIFICATIONS not granted (adb shell pm grant $packageName android.permission.POST_NOTIFICATIONS)"
        }
        val nm = getSystemService(NotificationManager::class.java)
        val badgeOff = x.getBoolean("badgeOff", false)
        val channel = if (badgeOff) "$CHANNEL.nobadge" else CHANNEL
        nm.createNotificationChannel(
            NotificationChannel(channel, if (badgeOff) "Tile client (no badge)" else "Tile client", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { setShowBadge(!badgeOff) },
        )
        val id = x.getInt("id", NOTIFICATION_ID)
        val empty = x.getBoolean("empty", false)
        val builder = Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setNumber(x.getInt("number", 7))
            .setOngoing(x.getBoolean("ongoing", false))
        if (!empty) {
            builder.setContentTitle(x.getString("title") ?: "Tile client").setContentText(x.getString("text") ?: "setNumber test")
        }
        x.getString("group")?.let { builder.setGroup(it).setGroupSummary(x.getBoolean("summary", false)) }
        when (x.getString("visibility")) {
            "secret" -> builder.setVisibility(Notification.VISIBILITY_SECRET)
            "private" -> builder.setVisibility(Notification.VISIBILITY_PRIVATE)
            "public" -> builder.setVisibility(Notification.VISIBILITY_PUBLIC)
        }
        nm.notify(id, builder.build())
        return "notify: posted id=$id number=${x.getInt("number", 7)} channel=$channel group=${x.getString("group")} summary=${x.getBoolean("summary", false)} ongoing=${x.getBoolean("ongoing", false)} empty=$empty visibility=${x.getString("visibility")}"
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
        // shareIdentity=false is how the real ShortcutBadger senders behave: the receiver is not told who sent it.
        val shareIdentity = x.getBoolean("shareIdentity", true)
        val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(shareIdentity).toBundle()
        val explicit = x.getBoolean("explicit", false)
        if (explicit) {
            receivers.map { it.activityInfo.packageName }.distinct().forEach { sendBroadcast(Intent(intent).setPackage(it), null, options) }
        } else {
            sendBroadcast(intent, null, options)
        }
        return "legacyBadge: sent $action count=$count package=$badgePackage explicit=$explicit shareIdentity=$shareIdentity receivers=${receivers.map { it.activityInfo.packageName + "/" + it.activityInfo.name }}"
    }

    private fun stamp(ms: Long = System.currentTimeMillis()) = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

    private companion object {
        const val TAG = "TileClient"
        const val CHANNEL = "tileclient"
        const val NOTIFICATION_ID = 7
    }
}
