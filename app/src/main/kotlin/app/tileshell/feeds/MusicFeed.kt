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
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.PackageSource
import app.tileshell.tiles.engine.TileRouting
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

    /**
     * Where the face currently is — its content key and its growth key — so it can be cleared when the
     * session moves to another app (phase 10 Q4). Without this, stopping Spotify and starting the shell's own
     * player would leave Spotify's tile showing a track that is no longer playing anywhere. Both keys come
     * from [TileRouting]: a package for another app, Music's component for the shell's own player (phase 15
     * build task 0).
     */
    private var publishedRoute: Route? = null

    /** A face's destination: the content key it is published under and the key whose tiles grow with it. */
    private data class Route(val contentKey: String, val growthKey: String)

    /** The shell sessions already reported as going to no tile, so the line is written once per session. */
    private val reportedNone = HashSet<MediaSession.Token>()

    /** The shell's own package, read once at [start]. */
    @Volatile private var shellPackage: String = ""

    private val forgetRegistered = java.util.concurrent.atomic.AtomicBoolean(false)

    /** The art belonging to [published]'s track: kept so an unchanged track is not re-read over a Binder. */
    private var publishedArt: ImageBitmap? = null

    fun start(context: Context) {
        val app = context.applicationContext
        // A package forgotten by the engine (gone, or reinstalled as another identity) is not republished from the
        // track remembered here when its session dies (the L11-1 fix review, F-2). Once per process: start runs again
        // on every checklist resume, and a late duplicate forget could clear a new track (the re-judge, R1-2).
        if (forgetRegistered.compareAndSet(false, true)) {
            LiveTileEngine.addForgetListener { pkg -> Handler(Looper.getMainLooper()).post { forget(pkg) } }
        }
        shellPackage = app.packageName
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

    private fun publish(all: List<MediaController>, reason: String) {
        // A shell session routed to no tile (Voice Recorder's playback; phase 15 build task 0) is not one this
        // feed follows at all: were it picked, a recording playing would take the face off Music's or another
        // app's tile, and the tile's controls would drive the recorder.
        val controllers = all.filter { c ->
            val routed = c.packageName != shellPackage || route(c) != null
            if (!routed && reportedNone.add(c.sessionToken)) {
                Diagnostics.add("music", "session ${c.packageName} id=${TileRouting.sessionId(c.tag) ?: "?"} -> none")
            }
            routed
        }
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
        val pkg = next?.track?.pkg
        // A live controller gives the route; a session that went away keeps the route its face already had.
        val route = if (now != null) controller?.let { route(it) } else publishedRoute

        // Phase 10 Q4: the face belongs to the tile of the app that OWNS the session, so it is published
        // under that app's key and the tiles standing for that app are the ones that grow. A tile is never
        // borrowed: an app with nothing on Start simply shows nowhere. "That app" is a package for everyone
        // but the shell, whose apps share one package and are told apart by component (TileRouting).
        val previous = publishedRoute
        if (previous != null && previous != route) {
            publishRoute(previous, null)
            ActiveTiles.setPackage(previous.growthKey, false, "the session moved to ${route?.growthKey ?: "nothing"}")
        }
        if (route != previous && route != null && pkg == shellPackage) {
            Diagnostics.add("music", "session $pkg id=${controller?.let { TileRouting.sessionId(it.tag) } ?: "?"} -> ${route.contentKey}")
        }
        publishedRoute = route
        route?.let { ActiveTiles.setPackage(it.growthKey, plan.grow, "music ${if (plan.grow) "playing" else "idle"}") }

        if (next == null || pkg == null || route == null) {
            Diagnostics.add("music", "idle, nothing known ($reason)")
            return
        }
        val face = TileFace.NowPlaying(art, next.track.title, next.track.artist, next.playing, plan.controls)
        publishRoute(
            route,
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

    private fun forget(pkg: String) {
        if (publishedRoute?.growthKey != pkg) return
        // The engine forgot the package on the wipe's thread; a publish of ours that ran on main in between (a session
        // dying mid-uninstall) re-created the slot. Main has the last word on our own slot (the re-judge, R1-1).
        LiveTileEngine.publishPackage(pkg, PackageSource.MUSIC, null)
        published = null
        publishedArt = null
        publishedRoute = null
        ActiveTiles.setPackage(pkg, false, "package forgotten")
        Diagnostics.add("music", "forgot $pkg's track (package gone)")
    }

    /** Back to nothing: no tile content, no growth, no remembered track. */
    private fun clear(reason: String) {
        current = null
        published = null
        publishedArt = null
        publishedRoute?.let {
            ActiveTiles.setPackage(it.growthKey, false, reason)
            publishRoute(it, null)
        }
        publishedRoute = null
    }

    /** The route of a live session, or null when it goes to no tile (TileRouting). */
    /**
     * A route's content: another app's slot goes through the engine's arbiter (the L11-1 fix: its now-playing face, its
     * Live Tile queue and its notification previews share that one slot), a shell app's component key straight to
     * the engine, where nothing else publishes (TileRouting; the listener's own-package previews are keyed by package).
     */
    private fun publishRoute(route: Route, content: TileContent?) {
        if (route.contentKey == LiveTileEngine.packageKey(route.growthKey)) {
            LiveTileEngine.publishPackage(route.growthKey, PackageSource.MUSIC, content)
        } else {
            LiveTileEngine.publish(route.contentKey, content)
        }
    }

    private fun route(c: MediaController): Route? {
        val content = TileRouting.sessionContentKey(c.packageName, c.tag, shellPackage) ?: return null
        val growth = TileRouting.sessionGrowthKey(c.packageName, c.tag, shellPackage) ?: return null
        return Route(content, growth)
    }

}
