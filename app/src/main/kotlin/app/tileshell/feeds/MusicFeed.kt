package app.tileshell.feeds

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.asImageBitmap
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace

/**
 * Music tile now-playing face (X3 approximation): the active media session's art, title and artist.
 * Active sessions are readable because the shell holds notification access (the listener component).
 */
object MusicFeed {
    private var manager: MediaSessionManager? = null
    private val callbacks = HashMap<android.media.session.MediaSession.Token, MediaController.Callback>()

    fun start(context: Context) {
        val app = context.applicationContext
        val listener = ComponentName(app, TileNotificationListener::class.java)
        val msm = app.getSystemService(MediaSessionManager::class.java) ?: return
        manager = msm
        runCatching {
            msm.addOnActiveSessionsChangedListener({ controllers -> track(controllers.orEmpty(), "sessions changed") }, listener, Handler(Looper.getMainLooper()))
            track(msm.getActiveSessions(listener), "start")
        }.onFailure {
            Diagnostics.add("music", "no session access (notification access not granted): $it")
            LiveTileEngine.publish(LiveTileEngine.MUSIC, null)
        }
    }

    private fun track(controllers: List<MediaController>, reason: String) {
        controllers.forEach { c ->
            if (!callbacks.containsKey(c.sessionToken)) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish(controllers, "state ${c.packageName}")
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish(controllers, "metadata ${c.packageName}")
                    override fun onSessionDestroyed() { callbacks.remove(c.sessionToken); publish(emptyList(), "destroyed ${c.packageName}") }
                }
                c.registerCallback(cb, Handler(Looper.getMainLooper()))
                callbacks[c.sessionToken] = cb
            }
        }
        publish(controllers, reason)
    }

    private fun publish(controllers: List<MediaController>, reason: String) {
        val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val meta = playing?.metadata
        if (playing == null || meta == null) {
            LiveTileEngine.publish(LiveTileEngine.MUSIC, null)
            Diagnostics.add("music", "idle ($reason)")
            return
        }
        val art: Bitmap? = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        LiveTileEngine.publish(
            LiveTileEngine.MUSIC,
            TileContent(listOf(TileFace.NowPlaying(art?.asImageBitmap(), title, artist)), FaceTransition.FLIP, System.currentTimeMillis(), "music:${playing.packageName}"),
        )
        Diagnostics.add("music", "now playing ${playing.packageName} title=$title ($reason)")
    }
}
