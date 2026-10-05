package app.tileshell.video

import java.util.Locale

/**
 * What [PlayerActivity] was asked to play, decided before any ExoPlayer source exists (phase 17 build task 7; T17-1
 * "the VIEW schemes"). Pure: no Android type, so the scheme rule is unit-tested. Every text here that came from the
 * caller has been through [PlayerRules.lineText].
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

    /**
     * A `content` source another app named that it may not read itself: "Can't play this address",
     * `[video] refused source: no grant`, and nothing is opened — not the source, not a provider query (C-M4).
     */
    data object Refused : PlayerRequest
}

/** What [PlayerAccess.decide] says about a source, before the scheme rule's own checks. */
enum class SourceDecision { ALLOWED, UNSUPPORTED, NO_GRANT }

/** Why another app's `content` source may be played — or [NONE]: it may not. The word is what the line says. */
enum class CallerRead(val word: String) {
    NONE("no grant"),
    OWN_PROVIDER("its own provider"),
    URI_GRANT("a read grant"),
    PROVIDER_ALLOWS("the provider allows it"),
    LAUNCH_ACCESS("it had access at launch"),
}

/** What the player does about the media server's token for one source: see [PlayerAccess.serverToken]. */
enum class ServerTokenUse { RESOLVE, NOT_GIVEN, NONE }

/**
 * What the player asks Android about who started it, for ONE source (the port behind [PlayerAccess.callerMayRead];
 * [PlayerActivity] holds the implementation). Every answer fails closed: an error or a missing API is "no".
 */
interface PlayerCallerPort {
    /** `Activity.getLaunchedFromUid()`: the uid that started the player, or [PlayerAccess.UNKNOWN_UID]. */
    val launchedFromUid: Int

    /** The uid of the provider behind the source's authority, or null when it cannot be seen. */
    fun providerUid(): Int?

    /** `checkUriPermission(uri, -1, uid, READ)`: [uid] holds an explicit read grant for the source. */
    fun holdsReadGrant(uid: Int): Boolean

    /** API 35+ `checkContentUriPermissionFull`: the provider itself lets [uid] read the source. False below 35. */
    fun providerAllowsRead(uid: Int): Boolean

    /**
     * API 35+ `Activity.getInitialCaller().checkContentUriPermission(uri, READ)`: whoever started the player could
     * read the source when it did — the platform's own answer, which needs no uid. False below 35.
     */
    fun hadAccessAtLaunch(): Boolean
}

/**
 * Who may make the player open what (trust review C-M4 (a), (b), (d)). TRUST-TOUCHING. PlayerActivity is exported and
 * opens a source with the SHELL's identity — its media permission, its own files — so what it opens for another app
 * must be something that app could open itself. Pure: no Android type, every combination unit-tested.
 */
object PlayerAccess {
    /** `Process.INVALID_UID`: Android did not say who started the activity. Treated as another app. */
    const val UNKNOWN_UID = -1

    /** True only when the launcher is known and is the shell itself (the hub, Photos, a tile — the same uid). */
    fun isOwnUid(launchedFromUid: Int, ownUid: Int): Boolean = launchedFromUid != UNKNOWN_UID && ownUid != UNKNOWN_UID && launchedFromUid == ownUid

    /**
     * Whether the app that started the player may itself read the `content` source — asked only for another app's
     * launch. With a known uid: the source is its own provider's, or it holds a read grant, or (API 35+) the provider
     * allows it. With or without one: (API 35+) the platform says the launcher had access at launch.
     */
    fun callerMayRead(port: PlayerCallerPort): CallerRead {
        val uid = port.launchedFromUid
        if (uid != UNKNOWN_UID) {
            if (port.providerUid() == uid) return CallerRead.OWN_PROVIDER
            if (port.holdsReadGrant(uid)) return CallerRead.URI_GRANT
            if (port.providerAllowsRead(uid)) return CallerRead.PROVIDER_ALLOWS
        }
        return if (port.hadAccessAtLaunch()) CallerRead.LAUNCH_ACCESS else CallerRead.NONE
    }

    /**
     * The rule: `http` and `https` are open to every caller (the address is the caller's own to fetch); `file` only to
     * the shell's own uid; `content` to the shell's own uid, and to another app only when that app may read it.
     */
    fun decide(scheme: String?, isOwnUid: Boolean, callerMayRead: Boolean): SourceDecision = when (scheme?.lowercase(Locale.ROOT)) {
        "http", "https" -> SourceDecision.ALLOWED
        "file" -> if (isOwnUid) SourceDecision.ALLOWED else SourceDecision.UNSUPPORTED
        "content" -> if (isOwnUid || callerMayRead) SourceDecision.ALLOWED else SourceDecision.NO_GRANT
        else -> SourceDecision.UNSUPPORTED
    }

    /** `EXTRA_QUEUE` — which MediaStore ids Autoplay goes on to — is taken from the shell's own uid only. */
    fun queueHonoured(isOwnUid: Boolean): Boolean = isOwnUid

    /**
     * Whether the saved media server's token may ride on this player's requests (B2-M2). The token is the owner's, and
     * the player is exported: ONLY a launch by the shell itself — the hub's Media server page — gets the resolver that
     * adds it ([ServerTokenUse.RESOLVE]). For any other launch, and when Android does not say who launched, a source
     * that looks like a server's stream is played as it is, with no token, and [LINE_TOKEN_NOT_GIVEN] is written
     * ([ServerTokenUse.NOT_GIVEN]) — so no other app, and no link, can make the owner's token travel, whatever item
     * and stream parameters it names. A source that could never be a server's stream needs nothing
     * ([ServerTokenUse.NONE]).
     *
     * @param isOwnUid [isOwnUid]'s answer for this launch
     * @param couldBeServerStream the source is http(s) and its path ends `/stream`
     */
    fun serverToken(isOwnUid: Boolean, couldBeServerStream: Boolean): ServerTokenUse = when {
        !couldBeServerStream -> ServerTokenUse.NONE
        isOwnUid -> ServerTokenUse.RESOLVE
        else -> ServerTokenUse.NOT_GIVEN
    }

    const val LINE_TOKEN_NOT_GIVEN = "server token not given: not the shell's own launch"

    /** `[video] source from another app: <why it may be played>`. */
    fun line(read: CallerRead): String = "source from another app: ${read.word}"
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

    const val LINE_NO_GRANT = "refused source: no grant"

    /** The longest piece of caller-supplied text a `[video]` line carries, and the longest session title. */
    const val LINE_MAX = 80
    const val TITLE_MAX = 200

    private val MEDIA_VIDEO_PATH = Regex("^/[^/]+/video/media/(\\d+)$")

    /**
     * THE one place caller-supplied text is made fit for a line or a title (C-L1, C-L7): every control character is
     * dropped — C0 and C1, DEL, and the Unicode line and paragraph separators, so a `%0A` in an address cannot start a
     * forged line — and what is left is cut to [max] characters. A scheme, a host label, a last path segment, a
     * provider's display name and `EXTRA_TITLE` all pass through here before they are kept.
     */
    fun lineText(text: String?, max: Int = LINE_MAX): String = buildString {
        for (c in text.orEmpty()) {
            if (length >= max) break
            val control = c < ' ' || c in '\u007f'..'\u009f' || c == '\u2028' || c == '\u2029'
            if (!control) append(c)
        }
    }

    /**
     * The whole decision for a source: who may open it ([PlayerAccess.decide]), then the scheme rule ([classify]).
     * [path] must already be canonical for a `file` source ([sourcePath]).
     */
    fun request(scheme: String?, authority: String?, path: String?, ownFileRoots: List<String>, isOwnUid: Boolean, callerMayRead: Boolean): PlayerRequest =
        when (PlayerAccess.decide(scheme, isOwnUid, callerMayRead)) {
            SourceDecision.NO_GRANT -> PlayerRequest.Refused
            SourceDecision.UNSUPPORTED -> PlayerRequest.Unsupported(lineText(scheme?.lowercase(Locale.ROOT)).ifEmpty { "none" })
            SourceDecision.ALLOWED -> classify(scheme, authority, path, ownFileRoots)
        }

    /**
     * The path the scheme rule is given: a `file` source's CANONICAL path (links and `..` resolved by [canonical],
     * `File.getCanonicalPath`), or null — nothing is played — when it cannot be resolved; any other source's path as
     * it is.
     */
    fun sourcePath(scheme: String?, path: String?, canonical: (String) -> String?): String? =
        if (scheme.equals("file", ignoreCase = true)) path?.let { p -> runCatching { canonical(p) }.getOrNull() } else path

    val refused = PlayerFailure(TEXT_BAD_ADDRESS, LINE_NO_GRANT)

    /** The session's title: the caller's `EXTRA_TITLE`, else the source's own name — fit for a title either way. */
    fun sessionTitle(extra: String?, displayName: String?, fallback: String): String =
        lineText(extra, TITLE_MAX).takeIf { it.isNotBlank() }
            ?: lineText(displayName?.substringBeforeLast('.'), TITLE_MAX).takeIf { it.isNotBlank() }
            ?: lineText(fallback, TITLE_MAX)

    /**
     * The scheme rule: `content`, `http`, `https`, and `file` only for a file under one of [ownFileRoots] (the shell's
     * own directories; [path] must already be canonical for a `file` source). A missing scheme reads `none`.
     */
    fun classify(scheme: String?, authority: String?, path: String?, ownFileRoots: List<String>): PlayerRequest {
        val s = lineText(scheme?.lowercase(Locale.ROOT)).takeIf { it.isNotEmpty() } ?: return PlayerRequest.Unsupported("none")
        val name = lineText(path?.trimEnd('/')?.substringAfterLast('/')).ifEmpty { "video" }
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
                // A root that is empty or only slashes names the whole disk: it matches nothing (C-L6).
                val own = ownFileRoots.any { root -> root.trimEnd('/').let { r -> r.isNotBlank() && p.startsWith("$r/") } } && !p.contains("/../")
                if (own) PlayerRequest.Play(s, null, null, name) else PlayerRequest.Unsupported(s)
            }
            else -> PlayerRequest.Unsupported(s)
        }
    }

    /** `host[:port]` of a URI authority, the `user:password@` part dropped (a credential never reaches a line). */
    fun hostLabel(authority: String?): String = lineText(authority.orEmpty().substringAfterLast('@'))

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
        // The display name is the provider's text — another app's, for another app's source: fit for a line first.
        PlayerErrorKind.UNDECODABLE -> PlayerFailure(TEXT_UNDECODABLE, "cannot decode ${lineText(displayName).takeIf { it.isNotBlank() } ?: request.name}")
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
