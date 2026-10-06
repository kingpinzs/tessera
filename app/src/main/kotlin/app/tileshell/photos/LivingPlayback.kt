package app.tileshell.photos

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import app.tileshell.diag.Diagnostics
import java.io.FileInputStream
import java.io.IOException

/**
 * A Living Image's clip, playing while its photo is held (build task 6d): one small ExoPlayer over the clip's bytes
 * where they lie in the still's own file — nothing is copied out and nothing is written.
 *
 * It runs in the launcher's process (and in whichever process the other apps' viewer runs), so it is kept small and
 * narrow:
 *  - ONE at a time: [start] releases whatever is live first;
 *  - a video renderer only — there is no audio renderer, so a clip's sound track is never decoded, no audio focus is
 *    asked for and no audio device is touched;
 *  - the MP4 parsers only ([Mp4Extractor], [FragmentedMp4Extractor]): the bytes are another app's, and nothing else of
 *    Media3's container code is offered them;
 *  - every read goes through [ClipSource], which opens the file again, bounds the clip against the file AS IT IS THEN
 *    ([LivingRules.clip]) and serves [LivingRules.window]'s range and no byte beyond it.
 * A clip that does not parse, or whose file changed under it, ends as `stop (error <why>)` with the still left showing.
 *
 * Lines: `[photosapp] living <id>: play <clipBytes> bytes`, then exactly one `[photosapp] living <id>: stop (<released
 * |ended|left|error <why>>)`. Main thread only.
 */
@OptIn(UnstableApi::class)
class LivingPlayback private constructor(
    context: Context,
    /** The viewer item this plays over ([ViewerItem.key]). */
    val itemKey: String,
    private val id: String,
    uri: Uri,
    clip: LivingRules.Clip,
    private val onStopped: (LivingPlayback) -> Unit,
) {
    /** True from the clip's first drawn frame: until then the still is what shows. */
    var rendered by mutableStateOf(false)
        private set

    /** The clip's picture size as it is shown (turned upright); 0 until the decoder says. */
    var videoWidth by mutableIntStateOf(0)
        private set
    var videoHeight by mutableIntStateOf(0)
        private set

    private var stopped = false
    private val exo: ExoPlayer

    init {
        val app = context.applicationContext
        val renderers = RenderersFactory { handler, videoListener, _, _, _ ->
            arrayOf(MediaCodecVideoRenderer.Builder(app).setMediaCodecSelector(MediaCodecSelector.DEFAULT).setEventHandler(handler).setEventListener(videoListener).build())
        }
        val extractors = ExtractorsFactory {
            arrayOf(Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED), FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED))
        }
        val source = ProgressiveMediaSource.Factory(DataSource.Factory { ClipSource(app, uri, clip) }, extractors)
            // No retry: a clip that cannot be read or parsed is an error at once, while the hold still lasts.
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))
            .createMediaSource(MediaItem.fromUri(uri))
        exo = ExoPlayer.Builder(app, renderers).build()
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) stop(LivingRules.Stop.ENDED)
            }

            override fun onPlayerError(error: PlaybackException) = fail(reason(error))

            override fun onRenderedFirstFrame() { rendered = true }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    videoWidth = (size.width * size.pixelWidthHeightRatio).toInt().coerceAtLeast(1)
                    videoHeight = size.height
                }
            }
        })
        Diagnostics.add("photosapp", LivingRules.playLine(id, clip.length))
        exo.setMediaSource(source)
        exo.playWhenReady = true
        exo.prepare()
    }

    /** The view the clip is drawn into. */
    fun attach(view: TextureView) {
        if (!stopped) exo.setVideoTextureView(view)
    }

    /** Ends the clip and releases the player; the first call's reason is the line's, later calls do nothing. */
    fun stop(reason: LivingRules.Stop) = end(LivingRules.stopLine(id, reason))

    private fun fail(why: String) = end(LivingRules.errorLine(id, why))

    private fun end(line: String) {
        if (stopped) return
        stopped = true
        runCatching { exo.release() }
        if (live === this) live = null
        Diagnostics.add("photosapp", line)
        onStopped(this)
    }

    /** The rule's own reason when [ClipSource] refused the file, else Media3's error code name. */
    private fun reason(error: PlaybackException): String {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is ClipRefused) return cause.why
            cause = cause.cause
        }
        return error.errorCodeName
    }

    companion object {
        private var live: LivingPlayback? = null

        /** Starts [clip] of [uri] over the item [itemKey]; a clip already playing is stopped first (`left`). */
        fun start(context: Context, itemKey: String, id: String, uri: Uri, clip: LivingRules.Clip, onStopped: (LivingPlayback) -> Unit): LivingPlayback? {
            live?.stop(LivingRules.Stop.LEFT)
            return runCatching { LivingPlayback(context, itemKey, id, uri, clip, onStopped) }
                .onFailure { Diagnostics.add("photosapp", LivingRules.errorLine(id, it.javaClass.simpleName)) }
                .getOrNull()
                ?.also { if (!it.stopped) live = it }
        }
    }
}

/** [ClipSource]'s refusal: [why] is [LivingRules.clip]'s reason, or what could not be opened. */
private class ClipRefused(val why: String) : IOException(why)

/**
 * The player's only source: the bytes of [clip] inside the file [uri] names, read from the file where it is. Each open
 * takes the file's length from its descriptor and bounds the clip again — the file may have been replaced since it was
 * found to be a Living Image — and serves exactly [LivingRules.window]'s range.
 */
@OptIn(UnstableApi::class)
private class ClipSource(private val context: Context, private val uri: Uri, private val clip: LivingRules.Clip) : BaseDataSource(/* isNetwork = */ false) {
    private var descriptor: ParcelFileDescriptor? = null
    private var stream: FileInputStream? = null
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw ClipRefused("file cannot be opened")
            descriptor = pfd
            val now = LivingRules.clip(clip.offset, clip.length, pfd.statSize).clip
            if (now != clip) throw ClipRefused(LivingRules.clip(clip.offset, clip.length, pfd.statSize).why ?: "file changed")
            val window = LivingRules.window(clip, dataSpec.position, if (dataSpec.length == C.LENGTH_UNSET.toLong()) -1L else dataSpec.length)
                ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            val input = FileInputStream(pfd.fileDescriptor)
            stream = input
            input.channel.position(window.start)
            remaining = window.count
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            // A provider's SecurityException or FileNotFoundException subclass, a pipe with no positions.
            throw ClipRefused("file cannot be opened (${e.javaClass.simpleName})")
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0L) return C.RESULT_END_OF_INPUT
        val n = (stream ?: throw IOException("not open")).read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (n < 0) return C.RESULT_END_OF_INPUT
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = if (opened) uri else null

    override fun close() {
        try {
            runCatching { stream?.close() }
            runCatching { descriptor?.close() }
        } finally {
            stream = null
            descriptor = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}

/**
 * The clip's picture over the still: a TextureView the size at which the video covers the still's box, cut to the box
 * ([LivingRules.cover]). It is invisible until the clip's first frame is drawn, so the still shows through any wait.
 * Tag `viewer_living_clip`.
 */
@Composable
fun LivingSurface(play: LivingPlayback, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().testTag("viewer_living_clip"), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val (w, h) = LivingRules.cover(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), play.videoWidth, play.videoHeight)
        val shown = play.rendered
        AndroidView(
            factory = { TextureView(it).apply { isOpaque = false; alpha = 0f; play.attach(this) } },
            update = { it.alpha = if (shown) 1f else 0f },
            modifier = Modifier.requiredSize(with(density) { w.toDp() }, with(density) { h.toDp() }),
        )
    }
}
