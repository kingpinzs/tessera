package app.tileshell.video.server

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.media3.datasource.DataSpec
import app.tileshell.BuildConfig
import app.tileshell.R
import app.tileshell.diag.Diagnostics
import app.tileshell.net.CredentialStore
import app.tileshell.net.MiniJson
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import app.tileshell.video.VideoActivity
import app.tileshell.video.VideoNav
import app.tileshell.video.catalogue.FetchOutcome
import app.tileshell.video.catalogue.QaBases
import app.tileshell.video.catalogue.VideoHttp
import java.io.File
import java.util.UUID

/** What is kept about the server besides its token: nothing secret. */
data class ServerConfig(val base: String, val label: String, val userId: String, val userName: String)

/**
 * The media server (build task 13; Q-B: Jellyfin only; BS-5's 12.1 API): a thin client with no SDK. `connect` signs in
 * with `POST /Users/AuthenticateByName`, `library` is `GET /Items?userId=…`, and a video is direct play
 * (`/Videos/<id>/stream?static=true`) in the shared player. Every call blocks: run it off the main thread.
 *
 * TRUST-TOUCHING. The password is used for the one sign-in request and is never stored, logged or kept in a field of
 * this class. The access token lives in the credential store alone; it is sent as the `Authorization` header and, for
 * a stream, added as `ApiKey` by the player's data source ([streamResolver]) — so it is in no intent, no saved URL and
 * no line. Every line names the host and a state only.
 */
class MediaServer(context: Context) {
    private val app = context.applicationContext
    private val file = File(app.filesDir, CONFIG_FILE)

    /** The saved server, or null when none is set up. Reads the file every time. */
    fun config(): ServerConfig? {
        val o = runCatching { MiniJson.parseOrNull(file.readText(Charsets.UTF_8)).jsonObject() }.getOrNull() ?: return null
        return ServerConfig(
            o.jsonString("base") ?: return null, o.jsonString("label") ?: return null,
            o.jsonString("userId") ?: return null, o.jsonString("userName").orEmpty(),
        )
    }

    /** True while a server is set up: its config is saved and its token is in the store. */
    fun isSetUp(): Boolean = config() != null && CredentialStore.of(app).has(CredentialStore.JELLYFIN)

    /**
     * `mediaServer.connect(host, user, password)`: signs in and, when the server accepts, saves the server and its
     * token. The caller has already asked about an insecure address (C-16 (5)) — this sends at once.
     */
    fun connect(address: ServerAddress, user: String, password: String): ServerState {
        val base = QaBases.read(app, QaBases.SERVER)?.trimEnd('/') ?: address.base
        val body = MiniJson.write(linkedMapOf("Username" to user, "Pw" to password))
        val outcome = VideoHttp.post("$base/Users/AuthenticateByName", mapOf("Authorization" to header(null), "Accept" to "application/json"), body)
        val state = when (outcome) {
            is FetchOutcome.Answer -> {
                val signIn = ServerRules.parseSignIn(outcome.body)
                if (signIn == null) ServerState.UNREACHABLE else {
                    val saved = CredentialStore.of(app).set(CredentialStore.JELLYFIN, signIn.first)
                    if (saved) write(ServerConfig(base, address.label, signIn.second, user))
                    if (saved) ServerState.CONNECTED else ServerState.UNREACHABLE
                }
            }
            is FetchOutcome.Status -> ServerRules.stateOf(outcome.code)
            FetchOutcome.NoConnection -> ServerState.UNREACHABLE
        }
        Diagnostics.add("video", ServerRules.line(address.label, state.word))
        if (state == ServerState.CONNECTED) MediaServerShortcut.publish(app)
        return state
    }

    /** `mediaServer.library()`: the server's videos, or the state that stopped the read. Writes the state's line. */
    fun library(): Pair<ServerState, List<ServerItem>> {
        val cfg = config() ?: return ServerState.UNREACHABLE to emptyList()
        val token = CredentialStore.of(app).get(CredentialStore.JELLYFIN)
        if (token == null) {
            Diagnostics.add("video", ServerRules.line(cfg.label, ServerState.UNAUTHORISED.word))
            return ServerState.UNAUTHORISED to emptyList()
        }
        val outcome = VideoHttp.get(cfg.base + ServerRules.libraryPath(cfg.userId), mapOf("Authorization" to header(token), "Accept" to "application/json"))
        val items = (outcome as? FetchOutcome.Answer)?.let { ServerRules.parseItems(it.body) }
        val state = when {
            items != null -> ServerState.CONNECTED
            outcome is FetchOutcome.Status -> ServerRules.stateOf(outcome.code)
            else -> ServerState.UNREACHABLE
        }
        Diagnostics.add("video", ServerRules.line(cfg.label, state.word))
        return state to items.orEmpty()
    }

    /** `mediaServer.streamUrl(item)`: the direct-play address, with no token in it. */
    fun streamUrl(item: ServerItem): String? = config()?.let { ServerRules.streamUrl(it.base, item.id) }

    /** An item's picture, asked for with the token in the header. Null when there is none. */
    fun thumbnail(item: ServerItem): Bitmap? {
        val cfg = config() ?: return null
        val token = CredentialStore.of(app).get(CredentialStore.JELLYFIN) ?: return null
        val bytes = VideoHttp.bytes("${cfg.base}/Items/${item.id}/Images/Primary?maxWidth=336", mapOf("Authorization" to header(token))) ?: return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }

    /** Removes the server: its token leaves the store (`[video] server token cleared`), its config and its shortcut go. */
    fun remove() {
        CredentialStore.of(app).remove(CredentialStore.JELLYFIN)
        Diagnostics.add("video", "server token cleared")
        file.delete()
        MediaServerShortcut.remove(app)
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

    private fun write(cfg: ServerConfig) {
        val temp = File(app.filesDir, "$CONFIG_FILE.tmp")
        temp.writeText(MiniJson.write(linkedMapOf("base" to cfg.base, "label" to cfg.label, "userId" to cfg.userId, "userName" to cfg.userName)), Charsets.UTF_8)
        if (!temp.renameTo(file)) temp.delete()
    }

    companion object {
        const val CONFIG_FILE = "media_server.json"
        private const val DEVICE_FILE = "media_server_device.txt"

        /**
         * What the player's data source does to a request as it opens: a direct-play request to the SAVED server gets
         * the token as `ApiKey` (BS-5; [ServerRules.mayCarryToken]); every other request is passed on untouched. Null
         * when [source] could never qualify, so an ordinary video is played with no resolver at all.
         */
        fun streamResolver(context: Context, source: Uri): ((DataSpec) -> DataSpec)? {
            val app = context.applicationContext
            if (source.scheme != "http" && source.scheme != "https") return null
            if (source.path?.endsWith("/stream") != true) return null
            return { spec ->
                val cfg = MediaServer(app).config()
                val url = spec.uri.toString()
                if (cfg != null && ServerRules.mayCarryToken(url, cfg.base)) {
                    val token = CredentialStore.of(app).get(CredentialStore.JELLYFIN)
                    if (token != null) {
                        // The line shows the address with its query string removed: the token's place (C-32).
                        Diagnostics.add("video", "server stream ${ServerRules.withoutQuery(url)}")
                        spec.withUri(spec.uri.buildUpon().appendQueryParameter("ApiKey", token).build())
                    } else spec
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
