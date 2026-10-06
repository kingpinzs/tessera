package app.tileshell.video.server

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import androidx.media3.datasource.DataSpec
import app.tileshell.BuildConfig
import app.tileshell.R
import app.tileshell.diag.Diagnostics
import app.tileshell.net.CredentialStore
import app.tileshell.net.MiniJson
import app.tileshell.video.VideoActivity
import app.tileshell.video.VideoCalls
import app.tileshell.video.VideoNav
import app.tileshell.video.catalogue.FetchOutcome
import app.tileshell.video.catalogue.QaBases
import app.tileshell.video.catalogue.VideoHttp
import java.io.File
import java.util.UUID

/**
 * The media server (build task 13; Q-B: Jellyfin only; BS-5's 12.1 API): a thin client with no SDK. `connect` signs in
 * with `POST /Users/AuthenticateByName`, `library` is `GET /Items?userId=…`, and a video is direct play
 * (`/Videos/<id>/stream?static=true`) in the shared player. Every call blocks: run it off the main thread.
 *
 * TRUST-TOUCHING. The password is used for the one sign-in request and is never stored, logged or kept in a field of
 * this class. The access token lives in the credential store alone, SEALED WITH the server it was given by
 * ([ServerStore]; B-4): every request that carries it is built on the sealed address and asks [ServerStore.tokenFor] or
 * [ServerStore.streamToken] for it, which refuse any other server. It is sent as the `Authorization` header and, for a
 * stream, added as `ApiKey` by the player's data source ([streamResolver]; the shell's own launches only) — so it is in
 * no intent, no saved URL and no line. Every line names the host and a state only.
 */
class MediaServer(context: Context) {
    private val app = context.applicationContext
    private val store = storeOf(app)

    /** What the pages show about the saved server, or null when none is set up. Reads the file every time. */
    fun config(): ServerConfig? = store.display()

    /**
     * True while a server is set up: its token and address open from the store AND its page's file is the same save's
     * ([ServerStore.credential]) — the one meaning every token is given under (B2-M1).
     */
    fun isSetUp(): Boolean = store.credential() != null

    /** True when anything is stored under the server's name, set up or not: the setting then offers Remove. */
    fun entryHeld(): Boolean = store.entryHeld()

    /** On the hub's start: a left-over entry that is not a set-up server is cleared ([ServerStore.sweep]). */
    fun sweep(): Boolean = store.sweep(app.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true)

    /**
     * `mediaServer.connect(host, user, password)`: signs in and, when the server accepts, saves the server and its
     * token. The caller has already asked about an insecure address (C-16 (5)) — this sends at once.
     */
    fun connect(address: ServerAddress, user: String, password: String): ServerState {
        val base = ServerRules.signInBase(BuildConfig.DEBUG, QaBases.read(app, QaBases.SERVER), address.base)
        val body = MiniJson.write(linkedMapOf("Username" to user, "Pw" to password))
        val outcome = VideoHttp.post("$base/Users/AuthenticateByName", mapOf("Authorization" to header(null), "Accept" to "application/json"), body)
        val state = when (outcome) {
            is FetchOutcome.Answer -> {
                val signIn = ServerRules.parseSignIn(outcome.body)
                if (signIn == null) {
                    // An answer with no token, or with one that is not a plain token: the words only (B-1).
                    Diagnostics.add("video", ServerRules.line(address.label, ServerRules.WORD_BAD_ANSWER))
                    ServerState.UNREACHABLE
                } else {
                    // Connected only when the token, the address it belongs to and the page's file are all written.
                    val saved = store.save(ServerCredential(signIn.token, base, signIn.userId), ServerConfig(address.label, user, address.base))
                    if (saved) ServerState.CONNECTED else ServerState.UNREACHABLE
                }
            }
            is FetchOutcome.Status -> ServerRules.stateOf(outcome.code)
            FetchOutcome.NoConnection, FetchOutcome.TooLarge -> ServerState.UNREACHABLE
        }
        Diagnostics.add("video", ServerRules.line(address.label, state.word))
        if (state == ServerState.CONNECTED) MediaServerShortcut.publish(app)
        return state
    }

    /** `mediaServer.library()`: the server's videos, or the state that stopped the read. Writes the state's line. */
    fun library(): Pair<ServerState, List<ServerItem>> {
        val label = store.display()?.label ?: return ServerState.UNREACHABLE to emptyList()
        val server = store.credential()
        val url = server?.let { it.base + ServerRules.libraryPath(it.userId) }
        val token = url?.let(store::tokenFor)
        if (url == null || token == null) {
            Diagnostics.add("video", ServerRules.line(label, ServerState.UNAUTHORISED.word))
            return ServerState.UNAUTHORISED to emptyList()
        }
        val outcome = VideoHttp.get(url, mapOf("Authorization" to header(token), "Accept" to "application/json"))
        val items = (outcome as? FetchOutcome.Answer)?.let { ServerRules.parseItems(it.body) }
        val state = when {
            items != null -> ServerState.CONNECTED
            outcome is FetchOutcome.Status -> ServerRules.stateOf(outcome.code)
            else -> ServerState.UNREACHABLE
        }
        Diagnostics.add("video", ServerRules.line(label, state.word))
        return state to items.orEmpty()
    }

    /** `mediaServer.streamUrl(item)`: the direct-play address on the sealed server, with no token in it. */
    fun streamUrl(item: ServerItem): String? = store.credential()?.let { ServerRules.streamUrl(it.base, item.id) }

    /** An item's picture, asked for with the token in the header. Null when there is none. */
    fun thumbnail(item: ServerItem): Bitmap? {
        val server = store.credential() ?: return null
        val url = "${server.base}/Items/${item.id}/Images/Primary?maxWidth=336"
        val token = store.tokenFor(url) ?: return null
        val bytes = VideoHttp.bytes(url, mapOf("Authorization" to header(token))) ?: return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }

    /**
     * Removes the server and says whether its token really left the store (B2-M1): `[video] server token cleared` —
     * or `[video] server not removed: its token could not be cleared`, written by [ServerStore.remove], with the
     * setting still offering Remove. Either way its file and its shortcut go and the token is given to nothing.
     */
    fun remove(): Boolean {
        val cleared = store.remove()
        if (!isSetUp()) MediaServerShortcut.remove(app)
        return cleared
    }

    private fun header(token: String?): String = ServerRules.authorization(Build.MODEL ?: "phone", deviceId(), BuildConfig.VERSION_NAME, token)

    /** A random id made once for this install: Jellyfin keys a device's token on it. */
    private fun deviceId(): String {
        val f = File(app.filesDir, DEVICE_FILE)
        runCatching { f.readText(Charsets.UTF_8).trim() }.getOrNull()?.takeIf { it.length >= 16 }?.let { return it }
        val id = UUID.randomUUID().toString()
        runCatching { f.writeText(id, Charsets.UTF_8) }
        return id
    }

    companion object {
        const val CONFIG_FILE = ServerStore.CONFIG_FILE
        private const val DEVICE_FILE = ServerStore.DEVICE_FILE

        private fun storeOf(app: Context): ServerStore =
            ServerStore(app.filesDir, CredentialStore.of(app)) { Diagnostics.add("video", it) }

        /**
         * What the player's data source does to a request as it opens: a direct-play request to the server the token
         * was SEALED WITH gets the token as `ApiKey` (BS-5; [ServerStore.streamToken]); every other request is passed
         * on untouched. The player makes one ONLY for the shell's own launch of a source that could be a stream
         * ([app.tileshell.video.PlayerAccess.serverToken], B2-M2): an ordinary video, and any other app's launch, is
         * played with no resolver at all.
         */
        fun streamResolver(context: Context): (DataSpec) -> DataSpec {
            val app = context.applicationContext
            return { spec ->
                val url = spec.uri.toString()
                // On the player's loading thread: an unexpected exception is "no token", named by its class only.
                val token = VideoCalls.guarded<String?>("server stream token", null, { Diagnostics.add("video", it) }) { storeOf(app).streamToken(url) }
                if (token != null) {
                    // The line shows the address with its query string removed: the token's place (C-32).
                    Diagnostics.add("video", ServerRules.streamLine(url))
                    spec.withUri(spec.uri.buildUpon().appendQueryParameter("ApiKey", token).build())
                } else spec
            }
        }
    }
}

/**
 * "Media server" as a DYNAMIC App Shortcut (T17-9 / C-9; build task 15): published while a server is set up, removed
 * when it is not, and attached to Movies & TV's own launcher activity so it bursts on that tile and never on Music's
 * (C-21).
 */
object MediaServerShortcut {
    const val ID = "video_mediaserver"

    fun publish(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val intent = Intent(Intent.ACTION_VIEW).setClass(context, VideoActivity::class.java).putExtra(VideoNav.EXTRA_PAGE, "mediaserver")
        val info = ShortcutInfo.Builder(context, ID)
            .setShortLabel(context.getString(R.string.video_shortcut_mediaserver))
            .setLongLabel(context.getString(R.string.video_shortcut_mediaserver))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_video_glyph))
            .setIntent(intent)
            .setActivity(ComponentName(context, VideoActivity::class.java))
            .setRank(2)
            .build()
        val had = runCatching { manager.dynamicShortcuts.any { it.id == ID } }.getOrDefault(false)
        // addDynamicShortcuts, never setDynamicShortcuts: the package's other dynamic shortcuts (Camera's modes) stay.
        val ok = runCatching { manager.addDynamicShortcuts(listOf(info)) }.getOrDefault(false)
        if (ok && !had) Diagnostics.add("video", "shortcut mediaserver published")
        if (!ok) Diagnostics.add("video", "shortcut mediaserver: not published")
    }

    fun remove(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val had = runCatching { manager.dynamicShortcuts.any { it.id == ID } }.getOrDefault(false)
        runCatching { manager.removeDynamicShortcuts(listOf(ID)) }
        if (had) Diagnostics.add("video", "shortcut mediaserver removed")
    }

    /** On the hub's start: the shortcut matches whether a server is set up (a restore, an update, a cleared store). */
    fun sync(context: Context, setUp: Boolean) {
        if (setUp) publish(context) else remove(context)
    }
}
