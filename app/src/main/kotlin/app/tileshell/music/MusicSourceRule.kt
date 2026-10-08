package app.tileshell.music

import app.tileshell.media.ContentUriText
import app.tileshell.music.radio.StationUrl
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * What the music player's data source may open (phase 20; Decisions "untrusted URLs never reach ExoPlayer's other
 * schemes", r3 D11 / D12; `docs/plan/qa/phase-20/hls-rereview.md` (b)). Pure: every case is in `MusicSourceRuleTest`.
 *
 * The player's `DefaultDataSource` opens `file:`, `content:`, `asset:`, `rawresource:`, `android.resource:` and
 * `data:` as well as http, with the SHELL's identity. `StationUrl.accept` weighs the address of a station ITEM — but
 * with HLS linked a station's playlist goes on to name segments, keys, init segments and nested playlists of its
 * own, and none of those is an item. So the question is asked again at the data source, for every address:
 *
 *  - an address that is, character for character, the address of an item the shell itself queued ([MusicSources])
 *    may be opened, whatever its scheme: a library track's MediaStore URI, phase 18's play-file URI, a station's
 *    accepted address, a home-server track's address (the home server is rightly on the private network) — and so
 *    every re-open of it: a reconnect, a seek's range request;
 *  - any OTHER `http` / `https` address — what a playlist names — only when [StationUrl.accept] takes it: a public
 *    host, never loopback, link-local, RFC 1918 or an unspecified literal, so a station's playlist is not a way to
 *    make the phone call a device on the network it is on. `qaHost` (the debug-only fixture host, null in a release
 *    build) is the one private host let through, as for the station's own address;
 *  - anything else — any other scheme, an address with none — is refused. Nothing a playlist, a redirect or a nested
 *    reference names can be local unless the shell queued that very address already.
 *
 * This is the FIRST layer, and it reads text only. A NAME that resolves to a private address, and a REDIRECT — which
 * is followed under the data source and never comes back here — are judged where the player connects (review R20-1;
 * [MusicConnectRule], [MusicHttp]).
 */
object MusicSourceRule {
    /**
     * @param asked the address the data source is about to open, as `Uri.toString()`
     * @param queued the addresses of the items the shell queued ([MusicSources])
     * @param qaHost [StationUrl.accept]'s: the debug-only fixture host, null in a release build
     */
    fun mayOpen(asked: String, queued: Set<String>, qaHost: String?): Boolean {
        if (asked in queued) return true
        val scheme = ContentUriText.parse(asked).scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return false
        return StationUrl.accept(asked, qaHost) == StationUrl.Accept.Ok
    }
}

/**
 * The addresses of the items the shell itself built for its queue, in this process: told by the item builders as they
 * give an item its address (`MusicService.mediaItem`, `MusicService.fileItem`, `StationItem.build`,
 * `ServerTrackItem.build` — the only four `.setUri(` sites under `music/`, `UriAccessWiringScanTest`), asked by the player's guarded data source on its loading thread. So the guard
 * never reads an item's address back out of a controller's item: a stranger's item never reaches a builder with its
 * own address (`MusicItemRule`), and what the builders were handed is the library's, the checked file's, or an address
 * its URL rule accepted.
 *
 * It only grows, and is not saved: an address is a library row's or a played file's, and after the process dies no
 * queue returns (playback resumption is not answered).
 */
class MusicSources {
    private val queued: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * The home server's among them (review R20-1): the one kind of address whose CONNECTION may be to a private
     * network — the user's own server, there by design ([MusicConnectRule]). Told by `ServerTrackItem.build` alone.
     */
    private val servers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** The shell is giving an item this address: remembered, and handed back for the item. */
    fun queued(address: String): String = address.also { queued.add(it) }

    /** [queued], for a home-server track's address. */
    fun queuedServer(address: String): String = address.also { queued.add(it); servers.add(it) }

    /** The home-server addresses queued so far (a live view: it grows). */
    fun servers(): Set<String> = servers

    /** [MusicSourceRule.mayOpen] against what was queued. */
    fun mayOpen(asked: String, qaHost: String?): Boolean = MusicSourceRule.mayOpen(asked, queued, qaHost)

    companion object {
        /** The process's one set: the Music screens' controller and the service's session build items in one process. */
        val own = MusicSources()
    }
}
