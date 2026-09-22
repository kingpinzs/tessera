package app.tileshell.feeds

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.ActiveTiles
import app.tileshell.tiles.Slot
import app.tileshell.tiles.TileKey
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import app.tileshell.tiles.engine.Transport

/**
 * The Music tile (X3 approximation, extended by INDEX Change Log 2026-09-21 item 3).
 *
 * Playing: the tile grows, and the now-playing face REPLACES the logo with play/pause, stop and skip on
 * it. Not playing: the last track takes a turn behind the logo on the tile's own timer, "sort of like the
 * photo one does". [MusicRules] holds every rule; this holds the sessions and the bitmaps.
 *
 * ONE media path: the sessions this reads through notification access are the same sessions [send] drives.
 * There is no second transport, no notification-listener media hack and no new permission — the shell
 * already holds the listener component, which is what `MediaSessionManager` authenticates against.
 *
 * **The 3-second republish (phase 02 hand-off), fixed at the producer.** A player posts a new
 * PlaybackState on every position tick, so `onPlaybackStateChanged` arrives about every 3 s with nothing
 * the tile draws having changed. This now compares what it is ABOUT to publish against what it last
 * published ([MusicRules.republish]) and stops there when they are equal: the tile republishes on change.
 */
object MusicFeed {
    private val callbacks = HashMap<MediaSession.Token, MediaController.Callback>()

    /** The session the tile is following, and therefore the one [send] drives. */
    @Volatile private var current: MediaController? = null

    /** What was last published, so a position tick can be recognised as "nothing changed". */
    private var published: MusicRules.Now? = null

    /** The art belonging to [published]'s track: kept so an unchanged track is not re-read over a Binder. */
    private var publishedArt: ImageBitmap? = null

    fun start(context: Context) {
        val app = context.applicationContext
        val listener = ComponentName(app, TileNotificationListener::class.java)
        val msm = app.getSystemService(MediaSessionManager::class.java) ?: return
        runCatching {
            msm.addOnActiveSessionsChangedListener(
                { controllers -> track(controllers.orEmpty(), "sessions changed") },
                listener,
                Handler(Looper.getMainLooper()),
            )
            track(msm.getActiveSessions(listener), "start")
        }.onFailure {
            Diagnostics.add("music", "no session access (notification access not granted): $it")
            // Without access there is no playback to speak of, so the tile must not be left grown at
            // whatever size the last thing it knew about had pushed it to.
            clear("no session access")
        }
    }

    private fun track(controllers: List<MediaController>, reason: String) {
        controllers.forEach { c ->
            if (!callbacks.containsKey(c.sessionToken)) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish(controllers, "state ${c.packageName}")
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish(controllers, "metadata ${c.packageName}")
                    override fun onSessionDestroyed() {
                        callbacks.remove(c.sessionToken)
                        // The session is gone, but the track it was playing is not: the tile keeps
                        // flipping the last thing it showed rather than falling back to a bare logo.
                        publish(controllers.filter { it.sessionToken != c.sessionToken }, "destroyed ${c.packageName}")
                    }
                }
                c.registerCallback(cb, Handler(Looper.getMainLooper()))
                callbacks[c.sessionToken] = cb
            }
        }
        publish(controllers, reason)
    }

    /**
     * A control on the tile was tapped. It goes to the session the tile is showing, which is the session
     * the tile read its face from — never to "some media app" and never through a second mechanism.
     */
    fun send(transport: Transport) {
        val controller = current
        if (controller == null) {
            Diagnostics.add("music", "control $transport ignored: no session")
            return
        }
        val playing = MusicRules.isPlaying(controller.playbackState?.state ?: PlaybackState.STATE_NONE)
        runCatching {
            when (transport) {
                Transport.PLAY_PAUSE -> if (playing) controller.transportControls.pause() else controller.transportControls.play()
                Transport.STOP -> controller.transportControls.stop()
                Transport.NEXT -> controller.transportControls.skipToNext()
            }
        }.onFailure { Diagnostics.add("music", "control $transport failed on ${controller.packageName}: $it") }
        Diagnostics.add("music", "control $transport -> ${controller.packageName} (was ${if (playing) "playing" else "idle"})")
    }

    private fun publish(controllers: List<MediaController>, reason: String) {
        val controller = MusicRules.pick(controllers) { MusicRules.isPlaying(it.playbackState?.state ?: PlaybackState.STATE_NONE) }
        current = controller
        val now = controller?.let { c ->
            val meta = c.metadata ?: return@let null
            MusicRules.Now(
                track = MusicRules.Track(
                    pkg = c.packageName,
                    title = meta.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
                    album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                    durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
                ),
                playing = MusicRules.isPlaying(c.playbackState?.state ?: PlaybackState.STATE_NONE),
            )
        }
        // A session that went away leaves the tile showing what it last showed, idle: "not playing"
        // is a state of this tile, not an absence of it.
        val next = now ?: published?.copy(playing = false)
        if (!MusicRules.republish(published, next)) return

        // Only a new track costs a Binder round trip for the art.
        val art = if (MusicRules.artChanged(published?.track, next?.track)) {
            controller?.metadata?.let { meta ->
                (meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART))
                    ?.let { bmp: Bitmap -> bmp.asImageBitmap() }
            }
        } else {
            publishedArt
        }

        published = next
        publishedArt = art
        val plan = MusicRules.plan(next)
        ActiveTiles.set(MUSIC_TILE, plan.grow, "music ${if (plan.grow) "playing" else "idle"}")

        if (next == null) {
            LiveTileEngine.publish(LiveTileEngine.MUSIC, null)
            Diagnostics.add("music", "idle, nothing known ($reason)")
            return
        }
        val face = TileFace.NowPlaying(art, next.track.title, next.track.artist, next.playing, plan.controls)
        LiveTileEngine.publish(
            LiveTileEngine.MUSIC,
            TileContent(
                faces = if (plan.flip) listOf(face) else emptyList(),
                transition = FaceTransition.FLIP,
                sourceTimeMs = System.currentTimeMillis(),
                sourceTag = "music:${next.track.pkg}",
                front = if (plan.front) face else null,
            ),
        )
        Diagnostics.add(
            "music",
            "${if (next.playing) "now playing" else "idle"} ${next.track.pkg} title=${next.track.title} " +
                "front=${plan.front} flip=${plan.flip} grow=${plan.grow} ($reason)",
        )
    }

    /** Back to nothing: no tile content, no growth, no remembered track. */
    private fun clear(reason: String) {
        current = null
        published = null
        publishedArt = null
        ActiveTiles.set(MUSIC_TILE, false, reason)
        LiveTileEngine.publish(LiveTileEngine.MUSIC, null)
    }

    /**
     * The tile that grows. The Music SLOT tile is the only tile the music feed drives (a pinned app tile
     * reads `pkg:` content, which this feed never publishes), so it is the only one that can grow.
     * A Music tile in the bottom row or inside a folder is not in the grid order and is left alone by
     * [app.tileshell.tiles.TileGrowth] on its own.
     */
    private val MUSIC_TILE: TileKey = TileKey.SlotTile(Slot.MUSIC)
}
