package app.tileshell.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import app.tileshell.diag.Diagnostics
import app.tileshell.video.server.ServerRules

/**
 * The video player's own ExoPlayer and its own Media3 session (Decisions "the video player owns its own ExoPlayer";
 * r3 D16): usage MEDIA with content type MOVIE and `handleAudioFocus`, so a video pauses Music and nothing resumes by
 * itself; the session id is [SESSION_ID], which Start's routing sends to no tile and no pod (`TileRouting`).
 *
 * One per process and never two alive: Media3 refuses a second session with the same id, and a player opened from a
 * second task starts before the first one's activity stops. [acquire] therefore releases whatever is live; the
 * activity that owned it re-acquires in its own `onStart` when it comes back.
 */
class VideoPlayback private constructor(context: Context, resolver: ResolvingDataSource.Resolver?) {
    val exo: ExoPlayer
    private val session: MediaSession
    var released = false
        private set

    init {
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(TIMEOUT_MS)
            .setReadTimeoutMs(TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(false)
        val upstream: DataSource.Factory = DefaultDataSource.Factory(context, http)
        val sources: DataSource.Factory = if (resolver == null) upstream else ResolvingDataSource.Factory(upstream, resolver)
        exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(sources)
                    // One retry, then the error state: "Can't reach this video" must not wait out Media3's default three.
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(1)),
            )
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(context, exo).setId(SESSION_ID).build()
        Diagnostics.add("video", "player ready (session id $SESSION_ID)")
    }

    fun release() {
        if (released) return
        released = true
        session.release()
        exo.release()
        if (live === this) live = null
        Diagnostics.add("video", "player released")
    }

    companion object {
        const val SESSION_ID = "video"
        const val TIMEOUT_MS = 10_000

        private var live: VideoPlayback? = null

        /**
         * The process's player, made new for this owner (main thread). [resolver] rewrites a request as it opens — the
         * media server's stream gets its `ApiKey` there, so the token is in no MediaItem, intent or session metadata.
         */
        fun acquire(context: Context, resolver: ((DataSpec) -> DataSpec)? = null): VideoPlayback {
            live?.release()
            val r = resolver?.let { fn ->
                object : ResolvingDataSource.Resolver {
                    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec = fn(dataSpec)

                    /** B-5: what the player reports as the opened address never carries the token [fn] added. */
                    override fun resolveReportedUri(uri: Uri): Uri = Uri.parse(ServerRules.reportedUrl(uri.toString()))
                }
            }
            return VideoPlayback(context.applicationContext, r).also { live = it }
        }
    }
}
