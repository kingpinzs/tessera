package app.tileshell.video.server

import app.tileshell.net.CredentialFile
import app.tileshell.net.CredentialStore
import app.tileshell.net.MiniJson
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import java.io.File
import java.util.UUID

/**
 * What the pages show about the server: nothing secret, and nothing that decides where a request goes.
 * @param label `host[:port]`, as the lines name the server
 * @param address `scheme://host[:port]` as the user's typed address was read — what "Sign in again" prefills
 */
data class ServerConfig(val label: String, val userName: String, val address: String)

/** The server's token with where it may go. [toString] never holds the token. */
class ServerCredential(val token: String, val base: String, val userId: String) {
    override fun toString(): String = "ServerCredential(base=$base)"
}

/** A sealed entry, opened: the server and the id of the save that wrote it ([ServerRules.paired]). No token in [toString]. */
class SealedServer(val credential: ServerCredential, val pair: String) {
    override fun toString(): String = "SealedServer(base=${credential.base})"
}

/**
 * What is kept about the media server (build task 13; trust review B-4, B2-M1). TRUST-TOUCHING. Plain java.io over the
 * credential store's file half, so the tests run it with the stand-in cipher.
 *
 * The token, the server's address and the user's id are ONE sealed entry of the credential store: they are written in
 * one rename and open together or not at all, so the address the token goes to is as hard to change as the token is to
 * read. (Chosen over binding the address as the cipher's AAD: that would leave the address itself in a plain file,
 * make a read depend on two files agreeing, and change the Keystore half that no JVM test reaches. With one entry the
 * cipher is untouched.) `media_server.json` holds what the pages SHOW — the host's label, the user's name, the address
 * as it was typed — and decides nothing about where a request goes: [tokenFor] and [streamToken] compare the address a
 * request is about to go to with the SEALED one and give no token for any other, writing [LINE_WITHHELD].
 *
 * ONE meaning of "a server is set up" (B2-M1): the sealed entry opens AND the pages' file is there AND both carry the
 * id of the same save ([ServerRules.paired]). [credential] is that and nothing less, and every token is given through
 * it — so there is never a token in use for a server the pages do not show. What can be left behind by a save or a
 * removal cut in half is cleared three ways: [remove] always deletes the pages' file and says whether the sealed entry
 * really went; the setting offers Remove whenever [entryHeld]; and [sweep] clears a left-over entry when the hub
 * starts.
 *
 * @param log one `[video]` message per event; none holds the token or any other value
 */
class ServerStore(private val dir: File, private val credentials: CredentialFile, private val log: (String) -> Unit) {
    private val file = File(dir, CONFIG_FILE)

    /** The pages' file as it is: what it shows, and the id of the save that wrote it (null when it has none). */
    private class Shown(val config: ServerConfig, val pair: String?)

    private fun shown(): Shown? {
        val o = runCatching { MiniJson.parseOrNull(file.readText(Charsets.UTF_8)).jsonObject() }.getOrNull() ?: return null
        val config = ServerConfig(
            o.jsonString("label") ?: return null, o.jsonString("userName").orEmpty(),
            // A file written before the address was kept has the server's base under its old name.
            o.jsonString("address") ?: o.jsonString("base").orEmpty(),
        )
        return Shown(config, o.jsonString("pair"))
    }

    /** What the pages show, or null when no server's file is there. Reads the file every time. */
    fun display(): ServerConfig? = shown()?.config

    /**
     * The server that is set up, or null — signed out — when there is no sealed entry, when it does not open, when
     * what opens is not a sealed server (the bare token an earlier build stored is such an entry), or when the pages'
     * file is not there or is not the same save's.
     */
    fun credential(): ServerCredential? {
        val sealed = credentials.get(CredentialStore.JELLYFIN)?.let(ServerRules::openCredential) ?: return null
        return sealed.credential.takeIf { ServerRules.paired(sealed.pair, shown()?.pair) }
    }

    /** True when ANYTHING is stored under the server's name in the credential store, set up or not. */
    fun entryHeld(): Boolean = credentials.holds(CredentialStore.JELLYFIN)

    /**
     * Saves the server: the sealed entry, then the pages' file, both with this save's new id. False — and the sign-in
     * must not be reported as connected — when either could not be written; a sealed entry whose file failed is taken
     * back out, and if even that fails it is no set-up server ([credential]) and is cleared by [remove] or [sweep].
     */
    fun save(credential: ServerCredential, display: ServerConfig): Boolean = synchronized(LOCK) {
        val pair = UUID.randomUUID().toString().replace("-", "")
        if (!credentials.set(CredentialStore.JELLYFIN, ServerRules.sealCredential(credential, pair))) return false
        if (writeDisplay(display, pair)) return true
        credentials.clear(CredentialStore.JELLYFIN)
        log(LINE_NOT_SAVED)
        false
    }

    /** Temp file, then a rename over the old one; false when either step fails. */
    private fun writeDisplay(display: ServerConfig, pair: String): Boolean {
        val temp = File(dir, TEMP_FILE)
        return try {
            temp.writeText(MiniJson.write(linkedMapOf("label" to display.label, "userName" to display.userName, "address" to display.address, "pair" to pair)), Charsets.UTF_8)
            if (temp.renameTo(file)) true else { temp.delete(); false }
        } catch (e: Exception) {
            temp.delete()
            false
        }
    }

    /**
     * Removes the server and says whether its SEALED ENTRY is gone (B2-M1). The pages' file is deleted either way —
     * a delete needs no room on the disk, and with no file there is no set-up server ([credential]), so after a Remove
     * the token is given to nothing whatever else failed. [LINE_CLEARED] is written only when the sealed entry really
     * went; when it could not be removed (a full disk, the store's lock held) the line is [LINE_NOT_REMOVED], the
     * entry is still held ([entryHeld]) so the setting still offers Remove, and [sweep] tries again at the next start.
     */
    fun remove(): Boolean = synchronized(LOCK) {
        val cleared = credentials.clear(CredentialStore.JELLYFIN)
        file.delete()
        log(if (cleared) LINE_CLEARED else LINE_NOT_REMOVED)
        cleared
    }

    /**
     * On the hub's start (B2-M1): an entry stored under the server's name that is NOT a set-up server — a sealed
     * entry with no pages' file or another save's, one whose removal failed, one that does not open or is not a sealed
     * server (an earlier build's bare token, a key that is gone) — is removed, with one line that names no value. A
     * set-up server is left alone. True when something was removed.
     *
     * @param deviceLocked nothing is judged while the phone is locked: the store's key may need an unlocked phone
     *   (B2-L8), and an entry that only cannot be opened NOW must not be taken for one that never will be
     */
    fun sweep(deviceLocked: Boolean): Boolean = synchronized(LOCK) {
        if (deviceLocked || !entryHeld() || credential() != null) return false
        if (!credentials.clear(CredentialStore.JELLYFIN)) {
            log(LINE_NOT_REMOVED)
            return false
        }
        file.delete()
        log(LINE_SWEPT)
        true
    }

    /** The token for a request to [url] — only when [url] is on the set-up server (scheme, host and port). */
    fun tokenFor(url: String): String? = tokenIf(url) { ServerRules.sameServer(url, it.base) }

    /** The token for the player's request to [url] — only for the set-up server's direct-play path ([ServerRules.mayCarryToken]). */
    fun streamToken(url: String): String? = tokenIf(url) { ServerRules.mayCarryToken(url, it.base) }

    private fun tokenIf(url: String, allowed: (ServerCredential) -> Boolean): String? {
        val server = credential() ?: return null
        if (allowed(server)) return server.token
        // Worth a line only when the request was for a media server at all: the player asks for every `…/stream`.
        log(LINE_WITHHELD)
        return null
    }

    companion object {
        const val CONFIG_FILE = "media_server.json"

        /** The pages' file while it is being written (the extraction rules name it too, B2-L3). */
        const val TEMP_FILE = "$CONFIG_FILE.tmp"

        /** The random id this install shows the server as its device ([MediaServer]): kept on this phone only (B2-L3). */
        const val DEVICE_FILE = "media_server_device.txt"

        const val LINE_WITHHELD = "server token withheld: the address is not the signed-in server's"
        const val LINE_NOT_SAVED = "server not saved: its file could not be written"
        const val LINE_CLEARED = "server token cleared"
        const val LINE_NOT_REMOVED = "server not removed: its token could not be cleared"
        const val LINE_SWEPT = "server entry cleared at start: it was not a saved server"

        /** One save, removal or sweep at a time in this process: a sweep must not judge a save that is half written. */
        private val LOCK = Any()
    }
}
