package app.tileshell.video.server

import app.tileshell.net.CredentialFile
import app.tileshell.net.CredentialStore
import app.tileshell.net.MiniJson
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import java.io.File

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

/**
 * What is kept about the media server (build task 13; trust review B-4). TRUST-TOUCHING. Plain java.io over the
 * credential store's file half, so the tests run it with the stand-in cipher.
 *
 * The token, the server's address and the user's id are ONE sealed entry of the credential store: they are written in
 * one rename and open together or not at all, so the address the token goes to is as hard to change as the token is to
 * read. (Chosen over binding the address as the cipher's AAD: that would leave the address itself in a plain file,
 * make a read depend on two files agreeing, and change the Keystore half that no JVM test reaches. With one entry the
 * cipher is untouched.) `media_server.json` holds what the pages SHOW — the host's label, the user's name, the address
 * as it was typed — and decides nothing: [tokenFor] and [streamToken] compare the address a request is about to go to
 * with the SEALED one and give no token for any other, writing [LINE_WITHHELD].
 *
 * @param log one `[video]` message per event; none holds the token
 */
class ServerStore(private val dir: File, private val credentials: CredentialFile, private val log: (String) -> Unit) {
    private val file = File(dir, CONFIG_FILE)

    /** What the pages show, or null when no server's file is there. Reads the file every time. */
    fun display(): ServerConfig? {
        val o = runCatching { MiniJson.parseOrNull(file.readText(Charsets.UTF_8)).jsonObject() }.getOrNull() ?: return null
        return ServerConfig(
            o.jsonString("label") ?: return null, o.jsonString("userName").orEmpty(),
            // A file written before the address was kept has the server's base under its old name.
            o.jsonString("address") ?: o.jsonString("base").orEmpty(),
        )
    }

    /**
     * The sealed server, or null — signed out — when there is none, when it does not open, or when what opens is not
     * a token with its address and user id (the bare token an earlier build stored is such an entry).
     */
    fun credential(): ServerCredential? = credentials.get(CredentialStore.JELLYFIN)?.let(ServerRules::openCredential)

    /**
     * Saves the server: the sealed entry, then the pages' file. False — and the sign-in must not be reported as
     * connected — when either could not be written; a sealed entry whose file failed is taken back out, so no token
     * stays behind for a server the pages cannot show.
     */
    fun save(credential: ServerCredential, display: ServerConfig): Boolean {
        if (!credentials.set(CredentialStore.JELLYFIN, ServerRules.sealCredential(credential))) return false
        if (writeDisplay(display)) return true
        credentials.remove(CredentialStore.JELLYFIN)
        log(LINE_NOT_SAVED)
        return false
    }

    /** Temp file, then a rename over the old one; false when either step fails. */
    private fun writeDisplay(display: ServerConfig): Boolean {
        val temp = File(dir, "$CONFIG_FILE.tmp")
        return try {
            temp.writeText(MiniJson.write(linkedMapOf("label" to display.label, "userName" to display.userName, "address" to display.address)), Charsets.UTF_8)
            if (temp.renameTo(file)) true else { temp.delete(); false }
        } catch (e: Exception) {
            temp.delete()
            false
        }
    }

    fun remove() {
        credentials.remove(CredentialStore.JELLYFIN)
        file.delete()
    }

    /** The token for a request to [url] — only when [url] is on the sealed server (scheme, host and port). */
    fun tokenFor(url: String): String? = tokenIf(url) { ServerRules.sameServer(url, it.base) }

    /** The token for the player's request to [url] — only for the sealed server's direct-play path ([ServerRules.mayCarryToken]). */
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
        const val LINE_WITHHELD = "server token withheld: the address is not the signed-in server's"
        const val LINE_NOT_SAVED = "server not saved: its file could not be written"
    }
}
