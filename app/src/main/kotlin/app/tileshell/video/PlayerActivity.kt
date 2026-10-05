package app.tileshell.video

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.HttpDataSource
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.diag.RemoteRings
import app.tileshell.ui.setShellAppContent
import app.tileshell.video.server.MediaServer
import java.io.File
import java.util.concurrent.Executors

/** What the player page draws; written on the main thread by [PlayerActivity]. */
class PlayerUi {
    var failure by mutableStateOf<PlayerFailure?>(null)
    var hasSurface by mutableStateOf(true)
    var playing by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var videoW by mutableIntStateOf(0)
    var videoH by mutableIntStateOf(0)
    var hasText by mutableStateOf(false)
    var ccOn by mutableStateOf(false)
    var cue by mutableStateOf("")
    var zoomFill by mutableStateOf(false)
    var repeat by mutableStateOf(false)
    var autoplay by mutableStateOf(false)
    var landscape by mutableStateOf(false)
    var controlsShown by mutableStateOf(true)
    var menuOpen by mutableStateOf(false)

    /** Bumped by every touch on the controls, so the auto-hide hold starts again. */
    var touches by mutableIntStateOf(0)
}

/** What the page's controls do. */
interface PlayerActions {
    fun attachSurface(view: SurfaceView)
    fun refreshPosition()
    fun togglePlay()
    fun seekToFraction(fraction: Float)
    fun skip(deltaMs: Long)
    fun toggleCaptions()
    fun toggleFullScreen()
    fun castToDevice()
    fun toggleZoom()
    fun toggleRepeat()
    fun toggleAutoplay()
}

/**
 * The one player of the shell (phase 17, Q4 A; r3 D6): what a video tapped in Photos, in the hub or (phase 18) in Files
 * plays in, and what `ACTION_VIEW` on a video from another app opens — `content://` and `http(s)://` sources, and
 * `file://` only for the shell's own files. A helper in standard launch mode in `:video`, so it runs in its caller's
 * task and is not a catalog entry. Exported, so it is on qa/phase-03/exported-allowlist.txt: the intent only names what
 * is played.
 *
 * The player and its session live from `onStart` to `onStop` ([VideoPlayback]), so no session outlives the page.
 */
class PlayerActivity : ComponentActivity(), PlayerActions {
    private val ui = PlayerUi()
    private var ring: android.content.ServiceConnection? = null
    private val io = Executors.newSingleThreadExecutor()

    private var request: PlayerRequest = PlayerRequest.Unsupported("none")
    private var uri: Uri? = null
    private var queue: LongArray? = null
    private var displayName: String? = null
    private var subtitle: Uri? = null
    private var lookedUp = false
    private var started = false

    private var playback: VideoPlayback? = null
    private var surface: SurfaceView? = null
    private var resumePositionMs = 0L
    private var resumePlay = true
    private var announced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("video", "PlayerActivity created")
        hideSystemBars()
        // Y6: the player opens and closes as a cut, whoever started it.
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        ring = RemoteRings.hold(this, VideoDumpService::class.java)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ui.zoomFill = prefs.getBoolean(KEY_ZOOM, false)
        ui.repeat = prefs.getBoolean(KEY_REPEAT, false)
        ui.autoplay = prefs.getBoolean(KEY_AUTOPLAY, false)
        queue = runCatching { intent?.getLongArrayExtra(EXTRA_QUEUE) }.getOrNull()
        open(intent?.data)
        setShellAppContent(statusBar = false) { PlayerScreen(ui, this) }
    }

    /** Decides the source (the scheme rule) and, for one the player takes, looks up what it needs off the main thread. */
    private fun open(source: Uri?) {
        uri = source
        lookedUp = false
        announced = false
        displayName = null
        subtitle = null
        resumePositionMs = 0L
        resumePlay = true
        ui.failure = null
        ui.positionMs = 0L
        ui.durationMs = 0L
        ui.hasText = false
        ui.cue = ""
        val path = source?.path?.let { p -> if (source.scheme.equals("file", true)) runCatching { File(p).canonicalPath }.getOrDefault(p) else p }
        val req = PlayerRules.classify(source?.scheme, source?.encodedAuthority, path, ownRoots())
        request = req
        if (req is PlayerRequest.Unsupported) {
            val f = PlayerRules.unsupported(req)
            Diagnostics.add("video", f.line)
            ui.failure = f
            ui.hasSurface = false
            return
        }
        ui.hasSurface = true
        io.execute {
            val meta = runCatching { lookUp(source!!, req as PlayerRequest.Play) }.getOrNull()
            runOnUiThread {
                if (uri != source || isDestroyed) return@runOnUiThread
                displayName = meta?.first
                subtitle = meta?.second
                lookedUp = true
                if (started) begin()
            }
        }
    }

    /** The source's own file name and, for a MediaStore video, the `.srt` of the same name in its folder (1.6.17). */
    private fun lookUp(source: Uri, req: PlayerRequest.Play): Pair<String?, Uri?> {
        if (req.scheme != "content") return null to null
        var name: String? = null
        var folder: String? = null
        val columns = if (req.mediaStoreId != null) arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH) else arrayOf(OpenableColumns.DISPLAY_NAME)
        contentResolver.query(source, columns, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                if (c.columnCount > 1) folder = c.getString(1)
            }
        }
        val base = name?.substringBeforeLast('.', "")?.takeIf { it.isNotEmpty() } ?: return name to null
        val dir = folder ?: return name to null
        var srt: Uri? = null
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        contentResolver.query(
            files, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(dir, "$base.srt"), null,
        )?.use { c -> if (c.moveToFirst()) srt = ContentUris.withAppendedId(files, c.getLong(0)) }
        Diagnostics.add("video", "subtitle file beside ${req.mediaStoreId}: ${if (srt != null) "found" else "none"}")
        return name to srt
    }

    private fun ownRoots(): List<String> = listOfNotNull(
        runCatching { dataDir.canonicalPath }.getOrNull(),
        runCatching { getExternalFilesDir(null)?.canonicalPath }.getOrNull(),
    )

    override fun onStart() {
        super.onStart()
        started = true
        if (lookedUp) begin()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onPause() {
        remember()
        super.onPause()
    }

    override fun onStop() {
        started = false
        remember()
        playback?.release()
        playback = null
        ui.playing = false
        super.onStop()
    }

    override fun onDestroy() {
        playback?.release()
        playback = null
        io.shutdown()
        RemoteRings.release(this, ring)
        super.onDestroy()
    }

    /** Where to pick up again when the page comes back (another player may take the session meanwhile). */
    private fun remember() {
        val p = playback?.takeUnless { it.released } ?: return
        resumePositionMs = p.exo.currentPosition.coerceAtLeast(0L)
        resumePlay = p.exo.playWhenReady && p.exo.playbackState != Player.STATE_ENDED
    }

    /** Makes the player and its session and loads the source. Only for a request the scheme rule accepted. */
    private fun begin() {
        val source = uri ?: return
        val req = request as? PlayerRequest.Play ?: return
        if (ui.failure != null) return
        // A direct-play request to the saved media server gets its token as it opens; nothing else is touched.
        val p = VideoPlayback.acquire(this, MediaServer.streamResolver(this, source))
        playback = p
        p.exo.addListener(listener)
        surface?.let { p.exo.setVideoSurfaceView(it) }
        val item = MediaItem.Builder()
            .setUri(source)
            .setMediaId(req.mediaStoreId?.toString() ?: req.name)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(titleOf(req)).build())
        subtitle?.let { srt ->
            item.setSubtitleConfigurations(
                listOf(MediaItem.SubtitleConfiguration.Builder(srt).setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("und").build()),
            )
        }
        applyCaptions()
        p.exo.repeatMode = if (ui.repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.exo.setMediaItem(item.build(), resumePositionMs)
        p.exo.playWhenReady = resumePlay
        p.exo.prepare()
    }

    private fun titleOf(req: PlayerRequest.Play): String =
        runCatching { intent?.getStringExtra(EXTRA_TITLE) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: req.name

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ui.playing = isPlaying
            if (isPlaying && !announced) {
                announced = true
                val req = request as? PlayerRequest.Play ?: return
                // Written when the picture starts moving, so a row can time a frame from the line (E11).
                req.mediaStoreId?.let { Diagnostics.add("video", "playing $it") }
                Diagnostics.add("video", "playing scheme=${req.scheme}")
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            val exo = playback?.exo ?: return
            if (state == Player.STATE_READY) ui.durationMs = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            if (state == Player.STATE_ENDED) {
                refreshPosition()
                ui.controlsShown = true
                if (ui.autoplay) playNext()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val req = request as? PlayerRequest.Play ?: return
            var status: Int? = null
            var cause: Throwable? = error
            while (cause != null) {
                if (cause is HttpDataSource.InvalidResponseCodeException) { status = cause.responseCode; break }
                cause = cause.cause
            }
            val failure = PlayerRules.failure(PlayerRules.kindOf(error.errorCode, req.isNetwork), req, status, displayName)
            // The line names the host or the file, never the address's query string (C-32).
            Diagnostics.add("video", failure.line)
            ui.failure = failure
            ui.playing = false
            ui.controlsShown = true
        }

        override fun onVideoSizeChanged(size: VideoSize) {
            ui.videoW = Math.round(size.width * size.pixelWidthHeightRatio)
            ui.videoH = size.height
        }

        override fun onTracksChanged(tracks: Tracks) {
            fun count(type: Int) = tracks.groups.count { it.type == type }
            ui.hasText = count(C.TRACK_TYPE_TEXT) > 0
            Diagnostics.add("video", "tracks: video=${count(C.TRACK_TYPE_VIDEO)} audio=${count(C.TRACK_TYPE_AUDIO)} text=${count(C.TRACK_TYPE_TEXT)}")
            if (ui.ccOn) applyCaptions()
        }

        override fun onCues(cueGroup: CueGroup) {
            ui.cue = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
        }
    }

    /** Autoplay (the Dec-2016 menu): at the end, the next video of the same My videos group. */
    private fun playNext() {
        val current = (request as? PlayerRequest.Play)?.mediaStoreId
        val next = PlayerRules.nextInQueue(queue, current) ?: return
        Diagnostics.add("video", "autoplay next $next")
        playback?.release()
        playback = null
        open(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, next))
    }

    // ---- the page's controls

    override fun attachSurface(view: SurfaceView) {
        surface = view
        playback?.takeUnless { it.released }?.exo?.setVideoSurfaceView(view)
    }

    override fun refreshPosition() {
        val exo = playback?.takeUnless { it.released }?.exo ?: return
        ui.positionMs = exo.currentPosition.coerceAtLeast(0L)
        val d = exo.duration
        if (d != C.TIME_UNSET && d > 0) ui.durationMs = d
    }

    override fun togglePlay() {
        val exo = playback?.takeUnless { it.released }?.exo ?: return
        if (ui.failure != null) return
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        if (exo.playWhenReady && exo.playbackState != Player.STATE_ENDED) exo.pause() else exo.play()
    }

    override fun seekToFraction(fraction: Float) {
        val exo = playback?.takeUnless { it.released }?.exo ?: return
        val d = exo.duration
        if (d == C.TIME_UNSET || d <= 0) return
        exo.seekTo((d * fraction.coerceIn(0f, 1f)).toLong())
        refreshPosition()
    }

    override fun skip(deltaMs: Long) {
        val exo = playback?.takeUnless { it.released }?.exo ?: return
        val d = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0L, d))
        refreshPosition()
    }

    override fun toggleCaptions() {
        ui.ccOn = !ui.ccOn
        if (!ui.ccOn) ui.cue = ""
        applyCaptions()
        Diagnostics.add("video", "captions ${if (ui.ccOn) "on" else "off"}")
    }

    /**
     * Captions off: the text renderer is disabled. On: the file's first text track is chosen outright — Media3 picks a
     * text track by itself only when its language is the preferred one or the file flags it as default.
     */
    private fun applyCaptions() {
        val exo = playback?.takeUnless { it.released }?.exo ?: return
        val wanted = exo.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !ui.ccOn)
        if (ui.ccOn) {
            exo.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT }?.let { group ->
                wanted.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            }
        }
        val built = wanted.build()
        if (built != exo.trackSelectionParameters) exo.trackSelectionParameters = built
    }

    /**
     * W10M's button hid the nav bar "to make the video wider" (r11/movies-tv.md 1.6.2). The shell's drawn nav bar stays
     * on every page, so the button turns the page to landscape and back.
     */
    override fun toggleFullScreen() {
        ui.landscape = !ui.landscape
        requestedOrientation = if (ui.landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    /** Android's own cast route, resolved at the tap; the shell ships no cast library (P5). */
    override fun castToDevice() {
        try {
            startActivity(Intent(ACTION_CAST_SETTINGS))
            Diagnostics.add("video", "cast: opened Android's cast settings")
        } catch (e: ActivityNotFoundException) {
            Diagnostics.add("video", "cast: this phone has no cast settings")
        } catch (e: SecurityException) {
            Diagnostics.add("video", "cast: this phone has no cast settings")
        }
    }

    override fun toggleZoom() {
        ui.zoomFill = !ui.zoomFill
        save(KEY_ZOOM, ui.zoomFill)
    }

    override fun toggleRepeat() {
        ui.repeat = !ui.repeat
        save(KEY_REPEAT, ui.repeat)
        playback?.takeUnless { it.released }?.exo?.repeatMode = if (ui.repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    override fun toggleAutoplay() {
        ui.autoplay = !ui.autoplay
        save(KEY_AUTOPLAY, ui.autoplay)
    }

    private fun save(key: String, value: Boolean) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }

    companion object {
        /** The ids of the My videos group the video was opened from, in order (Autoplay). */
        const val EXTRA_QUEUE = "queue"

        /** The title the session shows for a source whose address says nothing (a media-server item). */
        const val EXTRA_TITLE = "title"

        const val ACTION_CAST_SETTINGS = "android.settings.CAST_SETTINGS"

        /** Read and written by `:video` only. */
        private const val PREFS = "video_player"
        private const val KEY_ZOOM = "zoom_to_fill"
        private const val KEY_REPEAT = "repeat"
        private const val KEY_AUTOPLAY = "autoplay"
    }
}
