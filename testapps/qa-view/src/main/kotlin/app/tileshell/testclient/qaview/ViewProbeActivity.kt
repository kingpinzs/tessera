package app.tileshell.testclient.qaview

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Another app's VIEW of a video, aimed at the shell's player:
 *
 *   am start -n app.tileshell.testclient.qaview/.ViewProbeActivity --es uri <uri> [--ez grant true]
 *            [--es queue 1,2,3] [--ez share true] [--es component <pkg/class>] [--es mime <type>]
 *
 * `uri`    the source; `own:<name>` is this app's own provider's URI for filesDir/<name>.
 * `grant`  adds FLAG_GRANT_READ_URI_PERMISSION.
 * `queue`  a long-array extra named `queue` (the player's Autoplay queue, which only the shell's own launch may set).
 * `share`  starts with ActivityOptions.setShareIdentityEnabled(true), so the shell is told who launched it.
 *
 * Every fact is one logcat line under the tag TileShellQa, `qa-view: <name>=<value>`; no value of an extra other than
 * the URI is logged.
 */
class ViewProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra("uri").orEmpty()
        val uri = if (text.startsWith("own:")) Uri.parse("content://${OwnProvider.AUTHORITY}/${text.removePrefix("own:")}") else Uri.parse(text)
        val grant = intent.getBooleanExtra("grant", false)
        val share = intent.getBooleanExtra("share", false)
        val queue = intent.getStringExtra("queue")?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toLongArray()
        val component = ComponentName.unflattenFromString(intent.getStringExtra("component") ?: "app.tileshell/app.tileshell.video.PlayerActivity")
        say("start", "uri=$uri grant=$grant share=$share queue=${queue?.size ?: "none"}")
        say("READ_MEDIA_VIDEO", if (checkSelfPermission("android.permission.READ_MEDIA_VIDEO") == PackageManager.PERMISSION_GRANTED) "granted" else "denied")
        // Whether THIS app can read the source itself — the question the player asks about its caller.
        say("own read", if (uri.scheme == "content") attempt { contentResolver.openInputStream(uri)?.use { "${it.read(ByteArray(16))} bytes" } ?: "no stream" } else "not a content source")
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, intent.getStringExtra("mime") ?: "video/mp4").setComponent(component)
        if (grant) view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (queue != null) view.putExtra("queue", queue)
        say("launch", attempt {
            if (share) startActivity(view, ActivityOptions.makeBasic().setShareIdentityEnabled(true).toBundle()) else startActivity(view)
            "started"
        })
        finish()
    }

    private fun attempt(block: () -> String): String = try {
        "OK " + block()
    } catch (e: SecurityException) {
        "DENIED SecurityException"
    } catch (e: Exception) {
        "FAILED ${e.javaClass.simpleName}"
    }

    private fun say(name: String, value: String) {
        Log.i("TileShellQa", "qa-view: $name=$value")
    }
}
