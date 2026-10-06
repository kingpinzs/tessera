package app.tileshell.video

import app.tileshell.media.ContentUriText
import app.tileshell.media.UriAccessPort
import app.tileshell.media.UriAccessRules
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

/** What the player does about the media server's token for one source: see [PlayerAccess.serverToken]. */
enum class ServerTokenUse { RESOLVE, NOT_GIVEN, NONE }

/**
 * Who started the player, read ONCE at its creation ([PlayerAccess.launch]): whether it was the shell itself, the
 * autoplay queue it may use (null for every other starter) and the `[video]` lines to write.
 */
class PlayerLaunch(val own: Boolean, val queue: LongArray?, val lines: List<String>) {
    companion object {
        /** Before the launch is read, and whenever it cannot be: another app, with nothing honoured. */
        val OTHER = PlayerLaunch(own = false, queue = null, lines = emptyList())
    }
}

/** What [PlayerRules.source] decided for one source: the request, its error state when refused, and the lines. */
class PlayerSource(val request: PlayerRequest, val failure: PlayerFailure?, val lines: List<String>)

/**
 * Who may make the player open what (trust reviews C-M4 (a), (b), (d); C2-M1, C2-M3, C2-L1, C2-L3). TRUST-TOUCHING.
 * PlayerActivity is exported and opens a source with the SHELL's identity — its media permission, its own files — so
 * what it opens for another app must be something that app could open itself. Whether another app may read a `content`
 * source is not decided here: it is `media/UriAccessRules.starterMayRead`, the ONE rule the viewer and the capture
 * answer read by too, over the one platform port. Pure: no Android type, every combination unit-tested.
 */
object PlayerAccess {
    const val LINE_QUEUE_IGNORED = "queue ignored: not the shell's own launch"

    /**
     * The launch, from the platform port and the intent's two reads. [hasQueue] is `Intent.hasExtra(EXTRA_QUEUE)` and
     * [queue] `Intent.getLongArrayExtra(EXTRA_QUEUE)`; [queue] is not even read unless the shell itself started the
     * player. Anything that throws is another app's launch.
     */
    fun launch(access: UriAccessPort, hasQueue: () -> Boolean, queue: () -> LongArray?): PlayerLaunch {
        val own = try { UriAccessRules.isOwnLaunch(access) } catch (e: Throwable) { false }
        if (queueHonoured(own)) return PlayerLaunch(own, try { queue() } catch (e: Throwable) { null }, emptyList())
        val named = try { hasQueue() } catch (e: Throwable) { false }
        return PlayerLaunch(own, null, if (named) listOf(LINE_QUEUE_IGNORED) else emptyList())
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

    /** `EXTRA_TITLE` sets the session's title — shown in the system's media controls — for the shell's own launch only (C2-L3). */
    fun titleHonoured(isOwnUid: Boolean): Boolean = isOwnUid

    /**
     * The `.srt` beside a MediaStore video is looked for — a query made with the shell's own access — only for a
     * MediaStore item the shell itself launched (C2-M3).
     */
    fun subtitleLookedUp(isOwnUid: Boolean, mediaStoreId: Long?): Boolean = isOwnUid && mediaStoreId != null

    /**
     * What the player's data source may OPEN while it plays one launch (C2-M3). The access rule covers the launch URI;
     * a media source can ask for more — a playlist's entries, a redirect, a nested reference — and the data source
     * would open any `content:`, `file:`, `asset:` or `android.resource:` address with the shell's identity. So:
     *  - a network launch (`http` / `https`) opens only `http` and `https` addresses;
     *  - any other launch (`content`, or the shell's own `file`) opens only the launch URI itself, and the `.srt` the
     *    shell found beside its own MediaStore item ([subtitle], null when there is none).
     * Everything else is refused before it is opened.
     *
     * @param launchScheme the launch's scheme as [PlayerRequest.Play.scheme] has it (lower case)
     * @param launchUri the launch URI's text
     * @param asked the address the data source is about to open, as `Uri.toString()`
     */
    fun mayOpen(launchScheme: String, launchUri: String, subtitle: String?, asked: String): Boolean {
        if (launchScheme == "http" || launchScheme == "https") {
            val scheme = ContentUriText.parse(asked).scheme?.lowercase(Locale.ROOT)
            return scheme == "http" || scheme == "https"
        }
        return asked == launchUri || (subtitle != null && asked == subtitle)
    }

    /**
     * Whether the saved media server's token may ride on this player's requests (B2-M2). The token is the owner's, and
     * the player is exported: ONLY a launch by the shell itself — the hub's Media server page — gets the resolver that
     * adds it ([ServerTokenUse.RESOLVE]). For any other launch, and when Android does not say who launched, a source
     * that looks like a server's stream is played as it is, with no token, and [LINE_TOKEN_NOT_GIVEN] is written
     * ([ServerTokenUse.NOT_GIVEN]) — so no other app, and no link, can make the owner's token travel, whatever item
     * and stream parameters it names. A source that could never be a server's stream needs nothing
     * ([ServerTokenUse.NONE]).
     *
     * @param isOwnUid [PlayerLaunch.own] for this launch
     * @param couldBeServerStream the source is http(s) and its path ends `/stream`
     */
    fun serverToken(isOwnUid: Boolean, couldBeServerStream: Boolean): ServerTokenUse = when {
        !couldBeServerStream -> ServerTokenUse.NONE
        isOwnUid -> ServerTokenUse.RESOLVE
        else -> ServerTokenUse.NOT_GIVEN
    }

    const val LINE_TOKEN_NOT_GIVEN = "server token not given: not the shell's own launch"

    /** `[video] source from another app: <why it may be played>`. */
    fun line(read: UriAccessRules.ReadWhy): String = "source from another app: ${read.word}"
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
    const val LINE_MAX = VideoLines.MAX
    const val TITLE_MAX = 200

    private val MEDIA_VIDEO_PATH = Regex("^/[^/]+/video/media/(\\d+)$")

    /**
     * Caller-supplied text made fit for a line or a title (C-L1, C-L7) by the hub's one cleaner, [VideoLines.text]: a
     * scheme, a host label, a last path segment, a provider's display name and `EXTRA_TITLE` all pass through here
     * before they are kept.
     */
    fun lineText(text: String?, max: Int = LINE_MAX): String = VideoLines.text(text, max)

    /**
     * The whole decision for a source: who may open it ([PlayerAccess.decide]) FIRST, then — only for a source that
     * may be opened — the scheme rule ([classify]) over the path [sourcePath] gives. So a `file` path another app
     * named is never resolved on the disk (C2-L9): [canonical] is not called for it.
     */
    fun request(
        scheme: String?, authority: String?, path: String?, ownFileRoots: List<String>, isOwnUid: Boolean, callerMayRead: Boolean,
        canonical: (String) -> String?,
    ): PlayerRequest = when (PlayerAccess.decide(scheme, isOwnUid, callerMayRead)) {
        SourceDecision.NO_GRANT -> PlayerRequest.Refused
        SourceDecision.UNSUPPORTED -> PlayerRequest.Unsupported(lineText(scheme?.lowercase(Locale.ROOT)).ifEmpty { "none" })
        SourceDecision.ALLOWED -> classify(scheme, authority, sourcePath(scheme, path, canonical), ownFileRoots)
    }

    /**
     * From a launch's source to what the player does with it — the whole mapping, so no decision is left to the
     * Android side (C2-M1). [text] is the source as its string (`Uri.toString()`, null when the intent names none):
     * the access rule reads the scheme and authority from that one text, and it is the text the player opens.
     * [scheme], [authority] and [path] are `Uri.getScheme()`, `getEncodedAuthority()` and `getPath()` of the same URI,
     * for the scheme rule's names and lines. Another app's `content` source is asked about
     * (`UriAccessRules.starterMayRead`) and nothing else is; a rule or a platform read that throws refuses.
     */
    fun source(
        text: String?, scheme: String?, authority: String?, path: String?, ownFileRoots: List<String>, own: Boolean, access: UriAccessPort,
        canonical: (String) -> String?,
    ): PlayerSource {
        val lines = mutableListOf<String>()
        var callerMayRead = false
        if (text != null && !own && scheme.equals("content", ignoreCase = true)) {
            val read = try {
                UriAccessRules.starterMayRead(access, ContentUriText.parse(text))
            } catch (e: Throwable) {
                UriAccessRules.Read(UriAccessRules.ReadWhy.NONE, UriAccessRules.LAUNCH_NOT_ASKED)
            }
            callerMayRead = read.allowed
            if (read.launch != UriAccessRules.LAUNCH_NOT_ASKED) lines += UriAccessRules.launchLine(UriAccessRules.READ, read.launch)
            if (read.allowed) lines += PlayerAccess.line(read.why)
        }
        val request = if (text == null) PlayerRequest.Unsupported("none") else request(scheme, authority, path, ownFileRoots, own, callerMayRead, canonical)
        val failure = when (request) {
            is PlayerRequest.Unsupported -> unsupported(request)
            PlayerRequest.Refused -> refused
            is PlayerRequest.Play -> null
        }
        failure?.let { lines += it.line }
        return PlayerSource(request, failure, lines)
    }

    /**
     * The path the scheme rule is given: a `file` source's CANONICAL path (links and `..` resolved by [canonical],
     * `File.getCanonicalPath`), or null — nothing is played — when it cannot be resolved; any other source's path as
     * it is.
     */
    fun sourcePath(scheme: String?, path: String?, canonical: (String) -> String?): String? =
        if (scheme.equals("file", ignoreCase = true)) path?.let { p -> runCatching { canonical(p) }.getOrNull() } else path

    val refused = PlayerFailure(TEXT_BAD_ADDRESS, LINE_NO_GRANT)

    /**
     * The session's title: `EXTRA_TITLE` when the shell itself started the player ([own]; C2-L3 — another app's title
     * would show in the system's media controls under the shell's name, so [extra] is not even read for it), else the
     * source's own name — fit for a title either way.
     */
    fun sessionTitle(own: Boolean, extra: () -> String?, displayName: String?, fallback: String): String =
        (if (PlayerAccess.titleHonoured(own)) lineText(try { extra() } catch (e: Throwable) { null }, TITLE_MAX).takeIf { it.isNotBlank() } else null)
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
