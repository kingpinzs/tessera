package app.tileshell.testclient.qacapture

import android.app.Activity
import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken

/**
 * Another app's controller on the shell's exported music session (ledger L18-1; the adversarial GATE review's device
 * experiment 1). This app holds no permission. It does what any app can do:
 *
 *   am start -n app.tileshell.testclient.qacapture/.MediaProbeActivity --es tag <name> --es uri <uri> [--es id <media id>]
 *
 *  1. connects a Media3 `MediaController` to `app.tileshell/.music.MusicService` and sends `setMediaItem` with an item
 *     that CARRIES the URI (Media3's controller sends an item's local configuration), then `prepare` and `play`; it
 *     also asks for the shell's own PLAY_FILE custom command with the same URI;
 *  2. four seconds later it drops that controller and connects a FRESH one, so what it reads is the session's own
 *     state and not the first controller's optimistic copy of what it asked for;
 *  3. connects a platform `MediaBrowser` to the same service (the manifest declares the legacy action) and, if that
 *     connects, asks its `MediaController` to `playFromUri` the same URI.
 *
 * Logged under the tag TileShellQa, every line starting `media <tag> ` (the URI is never repeated, so the lines of two
 * runs with different URIs can be compared as text):
 *   media <tag> connect=<connected|failed <class>> play_file_offered=<bool> set_item_offered=<bool>
 *   media <tag> play_file result=<SessionResult code | failed <class>>
 *   media <tag> observed state=<n> playWhenReady=<bool> playing=<bool> items=<n> id=<id|none> title=<t|none>
 *                artist=<a|none> album=<a|none> duration=<ms|unset> error=<name|none> saw_error=<name|none> saw_playing=<bool>
 *   media <tag> legacy connect=<connected|failed|suspended> [state=<n> title=<t|none> duration=<ms|unset> error=<text|none>]
 *   media <tag> done
 */
class MediaProbeActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val service = ComponentName("app.tileshell", "app.tileshell.music.MusicService")
    private lateinit var tag: String
    private lateinit var uri: String
    private var sawError = "none"
    private var sawPlaying = false
    private var browser: MediaBrowser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tag = intent.getStringExtra("tag") ?: "probe"
        uri = intent.getStringExtra("uri").orEmpty()
        val id = intent.getStringExtra("id") ?: "qa-probe"
        connect(
            onFailed = { say("connect=failed ${it.javaClass.simpleName} play_file_offered=false set_item_offered=false"); legacy() },
        ) { controller ->
            val playFile = SessionCommand(PLAY_FILE, Bundle.EMPTY)
            say("connect=connected play_file_offered=${controller.isSessionCommandAvailable(playFile)} set_item_offered=${controller.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM)}")
            controller.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { sawError = error.errorCodeName }
                override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) sawPlaying = true }
            })
            controller.setMediaItem(MediaItem.Builder().setMediaId(id).setUri(uri).build())
            controller.prepare()
            controller.play()
            val asked = controller.sendCustomCommand(playFile, Bundle().apply { putString("uri", uri) })
            asked.addListener({ say("play_file result=${runCatching { asked.get().resultCode.toString() }.getOrElse { "failed ${it.javaClass.simpleName}" }}") }, mainExecutor)
            main.postDelayed({
                controller.release()
                connect(onFailed = { say("observed connect=failed ${it.javaClass.simpleName}"); legacy() }) { fresh ->
                    val meta = fresh.mediaMetadata
                    say(
                        "observed state=${fresh.playbackState} playWhenReady=${fresh.playWhenReady} playing=${fresh.isPlaying} items=${fresh.mediaItemCount} " +
                            "id=${fresh.currentMediaItem?.mediaId ?: "none"} title=${meta.title ?: "none"} artist=${meta.artist ?: "none"} album=${meta.albumTitle ?: "none"} " +
                            "duration=${fresh.duration.takeIf { it != C.TIME_UNSET } ?: "unset"} error=${fresh.playerError?.errorCodeName ?: "none"} saw_error=$sawError saw_playing=$sawPlaying",
                    )
                    fresh.release()
                    legacy()
                }
            }, WAIT_MS)
        }
    }

    private fun connect(onFailed: (Throwable) -> Unit, onConnected: (MediaController) -> Unit) {
        val future = runCatching { MediaController.Builder(this, SessionToken(this, service)).buildAsync() }.getOrElse { onFailed(it); return }
        future.addListener({ runCatching { future.get() }.fold(onConnected, onFailed) }, mainExecutor)
    }

    /** The legacy route: a platform MediaBrowser, then the platform controller's play-from-URI. */
    private fun legacy() {
        val callback = object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                val controller = android.media.session.MediaController(this@MediaProbeActivity, browser!!.sessionToken)
                runCatching { controller.transportControls.playFromUri(Uri.parse(uri), null) }
                main.postDelayed({
                    val description = controller.metadata?.description
                    val duration = controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }
                    say("legacy connect=connected state=${controller.playbackState?.state ?: -1} title=${description?.title ?: "none"} duration=${duration ?: "unset"} error=${controller.playbackState?.errorMessage ?: "none"}")
                    done()
                }, WAIT_MS)
            }
            override fun onConnectionFailed() { say("legacy connect=failed"); done() }
            override fun onConnectionSuspended() { say("legacy connect=suspended"); done() }
        }
        runCatching { browser = MediaBrowser(this, service, callback, null).also { it.connect() } }
            .onFailure { say("legacy connect=failed ${it.javaClass.simpleName}"); done() }
    }

    private var finished = false
    private fun done() {
        if (finished) return
        finished = true
        runCatching { browser?.disconnect() }
        say("done")
        finish()
    }

    private fun say(line: String) { Log.i(CaptureProbeActivity.TAG, "media $tag $line") }

    private companion object {
        /** `MusicCommands.PLAY_FILE`: the shell's own command, which no other app is offered. */
        const val PLAY_FILE = "app.tileshell.music.PLAY_FILE"
        const val WAIT_MS = 4_000L
    }
}
