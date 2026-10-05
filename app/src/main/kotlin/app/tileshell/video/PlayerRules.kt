package app.tileshell.video

import java.util.Locale

/**
 * What [PlayerActivity] was asked to play, decided before any ExoPlayer source exists (phase 17 build task 7; T17-1
 * "the VIEW schemes"). Pure: no Android type, so the scheme rule is unit-tested.
 */
sealed interface PlayerRequest {
    /**
     * A source the player takes.
     * @param mediaStoreId the row id when the source is a MediaStore video (`[video] playing <id>`)
     * @param host `host[:port]` of a network source with any `user:password@` part removed — what a line may name
     * @param name the last path segment, never the query string (C-32): what `cannot decode <name>` falls back to
     */
    data class Play(val scheme: String, val mediaStoreId: Long?, val host: String?, val name: String) : PlayerRequest {
        val isNetwork: Boolean get() = scheme == "http" || scheme == "https"
    }

    /** Anything else: "Can't play this address", `[video] unsupported scheme=<s>`, and no source is created. */
    data class Unsupported(val scheme: String) : PlayerRequest
}

/** Why a source stopped (the three error states of build task 7). */
enum class PlayerErrorKind { UNREACHABLE, HTTP_STATUS, UNDECODABLE }

/** An error state: the page's text and the diagnostics line's message (after `[video] `). */
data class PlayerFailure(val text: String, val line: String)

object PlayerRules {
    const val TEXT_BAD_ADDRESS = "Can't play this address"
    const val TEXT_UNREACHABLE = "Can't reach this video"

    /** E14 writes this one in lower case, and a row reads the text as written. */
    const val TEXT_UNDECODABLE = "can't play this file"

    private val MEDIA_VIDEO_PATH = Regex("^/[^/]+/video/media/(\\d+)$")

    /**
     * The scheme rule: `content`, `http`, `https`, and `file` only for a file under one of [ownFileRoots] (the shell's
     * own directories; [path] must already be canonical for a `file` source). A missing scheme reads `none`.
     */
    fun classify(scheme: String?, authority: String?, path: String?, ownFileRoots: List<String>): PlayerRequest {
        val s = scheme?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return PlayerRequest.Unsupported("none")
        val name = path?.trimEnd('/')?.substringAfterLast('/').orEmpty().ifEmpty { "video" }
        return when (s) {
            "content" -> {
                val id = if (authority == "media") MEDIA_VIDEO_PATH.find(path.orEmpty())?.groupValues?.get(1)?.toLongOrNull() else null
                PlayerRequest.Play(s, id, null, name)
            }
            "http", "https" -> {
                val host = hostLabel(authority)
                if (host.isEmpty()) PlayerRequest.Unsupported(s) else PlayerRequest.Play(s, null, host, name)
            }
            "file" -> {
                val p = path.orEmpty()
                val own = ownFileRoots.any { root -> root.isNotEmpty() && p.startsWith(root.trimEnd('/') + "/") } && !p.contains("/../")
                if (own) PlayerRequest.Play(s, null, null, name) else PlayerRequest.Unsupported(s)
            }
            else -> PlayerRequest.Unsupported(s)
        }
    }

    /** `host[:port]` of a URI authority, the `user:password@` part dropped (a credential never reaches a line). */
    fun hostLabel(authority: String?): String = authority.orEmpty().substringAfterLast('@')

    fun unsupported(request: PlayerRequest.Unsupported) = PlayerFailure(TEXT_BAD_ADDRESS, "unsupported scheme=${request.scheme}")

    /**
     * Media3's error code (`PlaybackException.errorCode`) as one of the three states. A connection failure is
     * "unreachable" only for a network source; everything that is not a connection failure or an HTTP status is a file
     * the device cannot play.
     */
    fun kindOf(errorCode: Int, isNetwork: Boolean): PlayerErrorKind = when {
        isNetwork && (errorCode == CODE_NETWORK_FAILED || errorCode == CODE_NETWORK_TIMEOUT) -> PlayerErrorKind.UNREACHABLE
        isNetwork && errorCode == CODE_BAD_HTTP_STATUS -> PlayerErrorKind.HTTP_STATUS
        else -> PlayerErrorKind.UNDECODABLE
    }

    /** @param displayName the file's own name when the source has one (a MediaStore row), else the request's name */
    fun failure(kind: PlayerErrorKind, request: PlayerRequest.Play, httpStatus: Int?, displayName: String?): PlayerFailure = when (kind) {
        PlayerErrorKind.UNREACHABLE -> PlayerFailure(TEXT_UNREACHABLE, "cannot reach ${request.host}")
        PlayerErrorKind.HTTP_STATUS -> PlayerFailure(TEXT_UNDECODABLE, "cannot decode ${httpStatus ?: 0}")
        PlayerErrorKind.UNDECODABLE -> PlayerFailure(TEXT_UNDECODABLE, "cannot decode ${displayName?.takeIf { it.isNotBlank() } ?: request.name}")
    }

    /** Autoplay: the id after [current] in its My videos group, or null at the group's end or outside one. */
    fun nextInQueue(queue: LongArray?, current: Long?): Long? {
        if (queue == null || current == null) return null
        val i = queue.indexOf(current)
        return if (i >= 0 && i + 1 < queue.size) queue[i + 1] else null
    }

    // androidx.media3.common.PlaybackException's codes, repeated here so the rule stays free of Android types;
    // PlayerRulesTest pins them to the library's constants.
    const val CODE_NETWORK_FAILED = 2001
    const val CODE_NETWORK_TIMEOUT = 2002
    const val CODE_BAD_HTTP_STATUS = 2004
}

/**
 * The player chrome's measured places (Y5; r11/movies-tv.md 1.6 with r11/movies-tv-pass2.md §1 / §4 governing), in epx
 * on the content box that ends at the nav bar's top: "nav − n" is n above that box's bottom. Pure, so the travel and
 * seek rules are unit-tested.
 */
object PlayerGeometry {
    const val SCRIM_HEIGHT = 120f
    const val SCRIM_ALPHA = 0.6f
    const val TRACK_INSET = 12f
    const val TRACK_THICKNESS = 2f
    const val TRACK_CENTRE_ABOVE_NAV = 93f
    const val THUMB_DIAMETER = 22f
    const val THUMB_STROKE = 2f
    const val THUMB_BELOW_TRACK = 1.2f
    const val UNPLAYED_ALPHA = 0.25f
    const val LABEL_BASELINE_ABOVE_NAV = 67.6f
    const val LABEL_FONT = 12f
    const val ELAPSED_LEFT = 12.5f
    const val REMAINING_RIGHT_INSET = 14.5f       // the label ends at 345.5 on the 360 canvas
    const val ROW_CENTRE_ABOVE_NAV = 39.8f
    const val GLYPH_HEIGHT = 18f
    const val CC_CENTRE = 36f
    const val SKIP_OFFSET = 48f                    // back 10 at W/2 − 48, forward 30 at W/2 + 48
    const val FULLSCREEN_FROM_RIGHT = 84f
    const val MORE_FROM_RIGHT = 36f
    const val MENU_WIDTH = 171.2f
    const val MENU_ITEM = 44f
    const val MENU_PAD = 8f
    const val MENU_TEXT_INSET = 39.6f
    const val MENU_BOTTOM_ABOVE_NAV = 60f

    /** Y6: the controls fade in ≈200 ms, hold ≈3.2 s, fade out over 367–400 ms. */
    const val FADE_IN_MS = 200
    const val HOLD_MS = 3200L
    const val FADE_OUT_MS = 360                    // the last frame lands ≈381 ms after the input at 60 fps

    const val SKIP_BACK_MS = 10_000L
    const val SKIP_FORWARD_MS = 30_000L

    fun trackWidth(canvasWidth: Float): Float = canvasWidth - 2 * TRACK_INSET

    /** 1.6.8's travel rule: the thumb's centre runs from track left + r to track right − r. */
    fun thumbCentreX(fraction: Float, canvasWidth: Float): Float {
        val r = THUMB_DIAMETER / 2f
        return TRACK_INSET + r + fraction.coerceIn(0f, 1f) * (trackWidth(canvasWidth) - 2 * r)
    }

    /** A tap at [x] on the canvas seeks to this fraction of the track (E11: 70 % of x 12 → 348 is 247.2). */
    fun fractionAt(x: Float, canvasWidth: Float): Float = ((x - TRACK_INSET) / trackWidth(canvasWidth)).coerceIn(0f, 1f)

    fun fraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /**
     * A time label in the locale's form (r11/movies-tv-pass2.md §4, 1.6.10): "0:17:34" on en-US, a two-digit hour on
     * en-GB ("00:00:29"). Other locales take the en-US form.
     */
    fun clock(totalSeconds: Long, locale: Locale): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val rest = String.format(Locale.ROOT, "%02d:%02d", (s % 3600) / 60, s % 60)
        return if (locale.country == "GB") String.format(Locale.ROOT, "%02d:%s", h, rest) else "$h:$rest"
    }

    fun elapsedSeconds(positionMs: Long): Long = positionMs.coerceAtLeast(0) / 1000

    /** The right label counts down: duration − position (1.6.11). */
    fun remainingSeconds(positionMs: Long, durationMs: Long): Long =
        ((durationMs - positionMs).coerceAtLeast(0) + 500) / 1000

    /**
     * The video's box inside a [boxW] × [boxH] area: fitted (letterboxed, the default since v3.6.1867) or, with Zoom
     * to fill, covering it. An unknown size takes the whole area.
     */
    fun videoBox(videoW: Int, videoH: Int, boxW: Float, boxH: Float, fill: Boolean): Pair<Float, Float> {
        if (videoW <= 0 || videoH <= 0 || boxW <= 0f || boxH <= 0f) return boxW to boxH
        val scaleFit = minOf(boxW / videoW, boxH / videoH)
        val scaleFill = maxOf(boxW / videoW, boxH / videoH)
        val scale = if (fill) scaleFill else scaleFit
        return videoW * scale to videoH * scale
    }
}
