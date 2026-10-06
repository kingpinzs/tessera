package app.tileshell.testclient.qaview

import android.app.Activity
import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
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
 * And, for the rows that PASTE a key (adb's `input text` cannot type a carriage return or 5,000 characters):
 *
 *   --es clip_b64 <base64 of UTF-8 text>   puts the text on the clipboard once this window has focus, then finishes
 *   --ez clip_clear true                   empties the clipboard, then finishes
 *
 * (no VIEW is started in either case; the text itself is never logged, only its length).
 *
 * Every fact is one logcat line under the tag TileShellQa, `qa-view: <name>=<value>`; no value of an extra other than
 * the URI is logged.
 */
class ViewProbeActivity : Activity() {
    private var clipDone = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || clipDone) return
        val clipboard = getSystemService(ClipboardManager::class.java)
        val encoded = intent.getStringExtra("clip_b64")
        if (encoded != null) {
            clipDone = true
            val text = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
            say("clip", attempt { clipboard.setPrimaryClip(ClipData.newPlainText("qa", text)); "set ${text.length} chars" })
            finish()
        } else if (intent.getBooleanExtra("clip_clear", false)) {
            clipDone = true
            say("clip", attempt { clipboard.clearPrimaryClip(); "cleared" })
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.hasExtra("clip_b64") || intent.getBooleanExtra("clip_clear", false)) return   // see onWindowFocusChanged
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
        // A file: source from a targetSdk 24+ app is stopped on the SENDER by the platform's FileUriExposedException;
        // the VmPolicy is relaxed for that one leg (as testapps/qa-capture does), so the shell is the one that answers.
        if (uri.scheme == "file") android.os.StrictMode.setVmPolicy(android.os.StrictMode.VmPolicy.Builder().build())
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
