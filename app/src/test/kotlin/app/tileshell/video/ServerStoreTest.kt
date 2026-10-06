package app.tileshell.video

import app.tileshell.net.CredentialCipher
import app.tileshell.net.CredentialFile
import app.tileshell.net.MiniJson
import app.tileshell.net.jsonObject
import app.tileshell.video.server.ServerConfig
import app.tileshell.video.server.ServerCredential
import app.tileshell.video.server.ServerRules
import app.tileshell.video.server.ServerSettingsView
import app.tileshell.video.server.ServerStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 trust review, B-4: the media server's token is sealed WITH the server it belongs to, so `media_server.json`
 * — a plain file — never decides where the token goes; and a save that could not be written is not a sign-in. Run
 * through the credential store's cipher interface with a stand-in cipher (the Keystore half is the device's).
 */
class ServerStoreTest {
    /** Not a real cipher: the stored bytes differ from the value, and a changed byte or name does not open. */
    private class FakeCipher : CredentialCipher {
        var counter = 0
        override fun seal(name: String, plain: ByteArray): CredentialCipher.Sealed {
            val iv = ByteArray(12) { (counter++ + it).toByte() }
            val body = plain.mapIndexed { i, b -> (b.toInt() xor 0x5A xor iv[i % 12].toInt()).toByte() }.toByteArray()
            return CredentialCipher.Sealed(iv, body + tag(name, iv, body))
        }

        override fun open(name: String, sealed: CredentialCipher.Sealed): ByteArray {
            val body = sealed.ciphertext.copyOfRange(0, sealed.ciphertext.size - 4)
            val tag = sealed.ciphertext.copyOfRange(sealed.ciphertext.size - 4, sealed.ciphertext.size)
            if (!tag.contentEquals(tag(name, sealed.iv, body))) throw IllegalStateException("bad tag for " + String(body))
            return body.mapIndexed { i, b -> (b.toInt() xor 0x5A xor sealed.iv[i % 12].toInt()).toByte() }.toByteArray()
        }

        private fun tag(name: String, iv: ByteArray, body: ByteArray): ByteArray {
            var h = 17
            for (b in name.toByteArray() + iv + body) h = h * 31 + b
            return byteArrayOf((h shr 24).toByte(), (h shr 16).toByte(), (h shr 8).toByte(), h.toByte())
        }
    }

    private val dir: File = Files.createTempDirectory("server").toFile()
    private val lines = mutableListOf<String>()
    private val cipher = FakeCipher()
    private val credentials = CredentialFile(File(dir, "credentials_v1.json"), cipher) { lines += "cred $it" }
    private fun store() = ServerStore(dir, credentials) { lines += it }
    private val config = File(dir, "media_server.json")

    private val token = "QA-TOKEN-0123456789abcdef"
    private val home = ServerCredential(token, "https://media.example.org", "7e0a575717d34c8292ee80ac53cc1ee3")
    private val shown = ServerConfig("media.example.org", "qa", "https://media.example.org")
    private fun stream(base: String) = "$base/Videos/e90356d9dbdedc30a27710927ef3ac87/stream?static=true"
    private fun library(base: String) = "$base/Items?userId=7e0a575717d34c8292ee80ac53cc1ee3&recursive=true"

    /** What an attacker with the file, or a save cut in half, leaves: the plain file naming another server. */
    private fun pointFileAt(base: String) {
        val o = MiniJson.parse(config.readText()).jsonObject()!!.toMutableMap()
        for (key in listOf("base", "address")) o[key] = base
        o["label"] = base.substringAfter("://")
        config.writeText(MiniJson.write(o))
    }

    @Test fun `a saved server reads back, and its token goes to its own addresses`() {
        assertNull(store().credential())
        assertNull(store().display())
        assertTrue(store().save(home, shown))
        val read = store().credential()!!
        assertEquals(token, read.token)
        assertEquals("https://media.example.org", read.base)
        assertEquals("7e0a575717d34c8292ee80ac53cc1ee3", read.userId)
        assertEquals(shown, store().display())
        assertEquals(token, store().tokenFor(library("https://media.example.org")))
        assertEquals(token, store().tokenFor("https://media.example.org:443/Items/ab12/Images/Primary?maxWidth=336"))
        assertEquals(token, store().streamToken(stream("https://media.example.org")))
    }

    @Test fun `the plain file does not decide where the token goes`() {
        assertTrue(store().save(home, shown))
        pointFileAt("http://attacker.example:8096")
        lines.clear()
        // The server is still the one the token was sealed with …
        assertEquals("https://media.example.org", store().credential()!!.base)
        // … and no request to the file's server gets the token, by header or by stream.
        assertNull(store().tokenFor(library("http://attacker.example:8096")))
        assertNull(store().streamToken(stream("http://attacker.example:8096")))
        assertEquals(token, store().tokenFor(library("https://media.example.org")))
        assertTrue(lines.toString(), lines.any { it == ServerStore.LINE_WITHHELD })
        assertTrue(lines.none { it.contains(token) })
    }

    @Test fun `the token is refused to another scheme, host or port of the sealed server`() {
        assertTrue(store().save(ServerCredential(token, "http://10.0.2.2:8096", "a1ab"), ServerConfig("10.0.2.2:8096", "qa", "http://10.0.2.2:8096")))
        assertEquals("http://10.0.2.2:8096", store().credential()!!.base)
        for (other in listOf("https://10.0.2.2:8096", "http://10.0.2.3:8096", "http://10.0.2.2:8097", "http://10.0.2.2:8096.evil.example", "http://10.0.2.2:8096@evil.example", "ftp://10.0.2.2:8096", "not a url")) {
            assertNull(other, store().tokenFor("$other/Items?userId=a1ab"))
            assertNull(other, store().streamToken(stream(other)))
        }
        assertEquals(token, store().tokenFor("HTTP://10.0.2.2:8096/Items?userId=a1ab"))
        // A stream's token rides only on the direct-play path.
        assertNull(store().streamToken("http://10.0.2.2:8096/Users/Me"))
    }

    @Test fun `the sealed entry holds the server's address, and the plain file holds no token, no user id and no base`() {
        assertTrue(store().save(home, shown))
        val plain = MiniJson.parse(config.readText()).jsonObject()!!
        assertEquals(setOf("label", "userName", "address", "pair"), plain.keys)
        assertTrue("the save's id names nothing", ServerRules.isPair(plain["pair"] as String))
        assertFalse(config.readText().contains(token))
        assertFalse(File(dir, "credentials_v1.json").readText().contains(token))
        // One entry: token, base and user id open together or not at all.
        assertEquals(setOf("jellyfin"), MiniJson.parse(File(dir, "credentials_v1.json").readText()).jsonObject()!!.keys)
    }

    @Test fun `a save whose file cannot be written is not a sign-in`() {
        // A directory with something in it where the file goes: the rename over it fails.
        File(config, "in-the-way").apply { parentFile.mkdirs(); writeText("x") }
        lines.clear()
        assertFalse(store().save(home, shown))
        assertNull("no token is left behind for a server the pages cannot show", store().credential())
        assertNull(credentials.get("jellyfin"))
        assertTrue(lines.toString(), lines.any { it == ServerStore.LINE_NOT_SAVED })
        assertTrue(lines.none { it.contains(token) })
        assertTrue(dir.list()!!.none { it.endsWith(".tmp") })
    }

    @Test fun `a save whose credential cannot be sealed is not a sign-in, and the server before it stays`() {
        assertTrue(store().save(home, shown))
        val failing = object : CredentialCipher {
            override fun seal(name: String, plain: ByteArray): CredentialCipher.Sealed = throw IllegalStateException("no key for " + String(plain))
            override fun open(name: String, sealed: CredentialCipher.Sealed): ByteArray = cipher.open(name, sealed)
        }
        val broken = ServerStore(dir, CredentialFile(File(dir, "credentials_v1.json"), failing) { lines += "cred $it" }) { lines += it }
        lines.clear()
        assertFalse(broken.save(ServerCredential("OTHER-TOKEN", "http://10.0.2.2:8096", "a2ab"), ServerConfig("10.0.2.2:8096", "x", "http://10.0.2.2:8096")))
        assertEquals("https://media.example.org", store().credential()!!.base)
        assertEquals(shown, store().display())
        assertTrue(lines.none { it.contains("OTHER-TOKEN") || it.contains(token) })
    }

    @Test fun `an entry that is not a sealed server - a bare token, a changed byte, another name - reads as signed out`() {
        // What the build before this one stored: the token alone, with the server in the plain file.
        credentials.set("jellyfin", token)
        config.writeText(MiniJson.write(mapOf("base" to "http://attacker.example", "label" to "attacker.example", "userId" to "a1ab", "userName" to "qa")))
        assertNull(store().credential())
        assertNull(store().tokenFor(library("http://attacker.example")))
        assertNull(store().streamToken(stream("http://attacker.example")))
        // A sealed server whose ciphertext was changed.
        assertTrue(store().save(home, shown))
        val file = File(dir, "credentials_v1.json")
        val root = MiniJson.parse(file.readText()).jsonObject()!!.toMutableMap()
        val entry = root["jellyfin"].jsonObject()!!.toMutableMap()
        val ct = java.util.Base64.getDecoder().decode(entry["ct"] as String)
        ct[3] = (ct[3] + 1).toByte()
        entry["ct"] = java.util.Base64.getEncoder().encodeToString(ct)
        file.writeText(MiniJson.write(root + ("jellyfin" to entry)))
        lines.clear()
        assertNull(store().credential())
        assertTrue(lines.none { it.contains(token) || it.contains("bad tag") })
    }

    @Test fun `a sealed server whose parts are not what they must be is not one`() {
        val pair = "0123456789abcdef0123456789abcdef"
        fun sealed(token: Any?, base: Any?, userId: Any?, id: Any? = pair): ServerCredential? =
            ServerRules.openCredential(MiniJson.write(mapOf("token" to token, "base" to base, "userId" to userId, "pair" to id)))?.credential
        assertEquals("http://10.0.2.2:8096", sealed("t0k", "http://10.0.2.2:8096", "ab12")!!.base)
        // The save's id: 32 hex digits, or it is not a sealed server of this build.
        for (bad in listOf(null, "", "0123", pair.uppercase(), pair + "0", "../" + pair.drop(3), 5)) assertNull("$bad", sealed("t0k", "http://10.0.2.2:8096", "ab12", bad))
        assertNull(sealed("t0k\rINJECT", "http://10.0.2.2:8096", "ab12"))
        assertNull(sealed("", "http://10.0.2.2:8096", "ab12"))
        assertNull(sealed("t0k", "ftp://10.0.2.2", "ab12"))
        assertNull(sealed("t0k", "http://user:pw@10.0.2.2:8096", "ab12"))
        assertNull(sealed("t0k", "http://10.0.2.2:8096/path", "ab12"))
        assertNull(sealed("t0k", "http://10.0.2.2:8096", "../Users"))
        assertNull(sealed("t0k", null, "ab12"))
        assertNull(sealed(5, "http://10.0.2.2:8096", "ab12"))
    }

    @Test fun `the pages' file from before the address was kept still names its server's scheme`() {
        config.writeText(MiniJson.write(mapOf("base" to "https://media.example.org", "label" to "media.example.org", "userId" to "a1ab", "userName" to "qa")))
        assertEquals(ServerConfig("media.example.org", "qa", "https://media.example.org"), store().display())
        assertEquals("https://media.example.org", ServerRules.signInAgainPrefill(store().display()))
    }

    @Test fun `removing the server removes the sealed entry and the file, and says so`() {
        assertTrue(store().save(home, shown))
        lines.clear()
        assertTrue(store().remove())
        assertNull(store().credential())
        assertNull(store().display())
        assertNull(credentials.get("jellyfin"))
        assertFalse(store().entryHeld())
        assertFalse(config.exists())
        assertEquals(listOf("cred jellyfin: removed", "server token cleared"), lines)
        // Nothing there: nothing is left, which is what was asked.
        assertTrue(store().remove())
    }

    // ---- B2-M1: a token is in use only for a server the pages show, and what a half-done save or removal leaves is cleared

    /** The store's own lock, held as another process would hold it: every credential write fails while [block] runs. */
    private fun whileCredentialWritesFail(block: () -> Unit) {
        java.io.RandomAccessFile(File(dir, "credentials_v1.json.lock"), "rw").channel.use { channel ->
            val held = channel.lock()
            try { block() } finally { held.release() }
        }
    }

    @Test fun `a sealed entry with no pages' file is no server - no stream token, no header token`() {
        assertTrue(store().save(home, shown))
        assertEquals(token, store().streamToken(stream("https://media.example.org")))
        config.delete()
        assertNull("the stream token", store().streamToken(stream("https://media.example.org")))
        assertNull("the header token", store().tokenFor(library("https://media.example.org")))
        assertNull(store().credential())
        // It is still stored, so the setting offers Remove.
        assertTrue(store().entryHeld())
        assertEquals(ServerSettingsView.LEFT_OVER, ServerRules.settingsView(setUp = store().credential() != null, entryHeld = store().entryHeld()))
    }

    @Test fun `a pages' file from another save does not make a sealed entry a server`() {
        // A sign-in again, killed between its two writes: the new server's entry beside the old server's file.
        assertTrue(store().save(home, shown))
        val oldFile = config.readText()
        assertTrue(store().save(ServerCredential("OTHER-TOKEN-1", "http://10.0.2.2:8096", "a2ab"), ServerConfig("10.0.2.2:8096", "x", "http://10.0.2.2:8096")))
        config.writeText(oldFile)
        assertNull(store().credential())
        assertNull(store().streamToken(stream("http://10.0.2.2:8096")))
        assertNull(store().tokenFor(library("http://10.0.2.2:8096")))
        assertNull(store().streamToken(stream("https://media.example.org")))
        // And a file with no save's id at all (written by hand, or by an earlier build).
        config.writeText(MiniJson.write(mapOf("label" to "10.0.2.2:8096", "userName" to "x", "address" to "http://10.0.2.2:8096")))
        assertNull(store().streamToken(stream("http://10.0.2.2:8096")))
        assertTrue(ServerRules.paired("0123456789abcdef0123456789abcdef", "0123456789abcdef0123456789abcdef"))
        assertFalse(ServerRules.paired("0123456789abcdef0123456789abcdef", "1123456789abcdef0123456789abcdef"))
        assertFalse(ServerRules.paired("0123456789abcdef0123456789abcdef", null))
        assertFalse(ServerRules.paired(null, null))
        assertFalse(ServerRules.paired("", ""))
    }

    @Test fun `a removal whose credential write fails says not removed, still offers Remove, and the token is given to nothing`() {
        assertTrue(store().save(home, shown))
        lines.clear()
        whileCredentialWritesFail { assertFalse(store().remove()) }
        assertEquals(listOf("cred jellyfin: not removed (OverlappingFileLockException)", ServerStore.LINE_NOT_REMOVED), lines)
        assertEquals("server not removed: its token could not be cleared", ServerStore.LINE_NOT_REMOVED)
        assertTrue(lines.none { it == ServerStore.LINE_CLEARED })
        // The entry is still sealed in the store, so the setting reads "left over" and offers Remove; the file is gone …
        assertTrue(store().entryHeld())
        assertFalse(config.exists())
        assertEquals(ServerSettingsView.LEFT_OVER, ServerRules.settingsView(setUp = store().credential() != null, entryHeld = store().entryHeld()))
        assertTrue(ServerRules.offersRemove(ServerSettingsView.LEFT_OVER))
        // … but the token the owner asked to remove is given to nothing.
        assertNull("the stream token after the failed removal", store().streamToken(stream("https://media.example.org")))
        assertNull("the header token after the failed removal", store().tokenFor(library("https://media.example.org")))
        // Remove again, the store writable: now it is gone, and only now is "cleared" said.
        lines.clear()
        assertTrue(store().remove())
        assertFalse(store().entryHeld())
        assertFalse(config.exists())
        assertTrue(lines.toString(), lines.last() == ServerStore.LINE_CLEARED)
        assertTrue(lines.none { it.contains(token) })
    }

    @Test fun `a removal that can write nothing at all still leaves the token given to nothing`() {
        // The lead's ruling on B2-M1: the credential removal fails AND no file could be written either (a full disk).
        assertTrue(store().save(home, shown))
        File(File(dir, "media_server.json.tmp"), "in-the-way").apply { parentFile.mkdirs(); writeText("x") }
        lines.clear()
        whileCredentialWritesFail { assertFalse(store().remove()) }
        assertNull("the stream token after the failed removal", store().streamToken(stream("https://media.example.org")))
        assertNull(store().tokenFor(library("https://media.example.org")))
        assertEquals(ServerSettingsView.LEFT_OVER, ServerRules.settingsView(setUp = store().credential() != null, entryHeld = store().entryHeld()))
        assertFalse("the pages' file is deleted: it needs no room", config.exists())
        assertEquals(ServerStore.LINE_NOT_REMOVED, lines.last())
        assertTrue(lines.none { it == ServerStore.LINE_CLEARED })
    }

    @Test fun `a save whose file fails while the entry cannot be taken back out leaves no server and no token`() {
        // The sealed entry is written, the file's rename fails, and the credential store cannot be written again.
        val stuck = object : CredentialCipher by cipher {}
        var writes = 0
        val once = CredentialFile(File(dir, "credentials_v1.json"), stuck, rename = { from, to -> writes++ == 0 && from.renameTo(to) }) { lines += "cred $it" }
        File(config, "in-the-way").apply { parentFile.mkdirs(); writeText("x") }
        assertFalse(ServerStore(dir, once) { lines += it }.save(home, shown))
        config.deleteRecursively()
        assertTrue("the entry is left behind", store().entryHeld())
        assertNull(store().credential())
        assertNull(store().streamToken(stream("https://media.example.org")))
        assertTrue(store().sweep(deviceLocked = false))
        assertFalse(store().entryHeld())
    }

    @Test fun `the start-up sweep removes each stale form with one line that names no value, and leaves a good pair alone`() {
        // A good pair: left alone, no line.
        assertTrue(store().save(home, shown))
        lines.clear()
        assertFalse(store().sweep(deviceLocked = false))
        assertEquals(emptyList<String>(), lines)
        assertEquals(token, store().streamToken(stream("https://media.example.org")))

        // (1) A sealed entry with no pages' file.
        config.delete()
        lines.clear()
        assertTrue(store().sweep(deviceLocked = false))
        assertFalse(store().entryHeld())
        assertEquals(listOf("cred jellyfin: removed", ServerStore.LINE_SWEPT), lines)

        // (2) An earlier build's bare token under the server's name, with that build's file.
        credentials.set("jellyfin", token)
        config.writeText(MiniJson.write(mapOf("base" to "http://10.0.2.2:8096", "label" to "10.0.2.2:8096", "userId" to "a1ab", "userName" to "qa")))
        lines.clear()
        assertTrue(store().sweep(deviceLocked = false))
        assertFalse(store().entryHeld())
        assertFalse("its file goes with it", config.exists())
        assertEquals(listOf("cred jellyfin: removed", ServerStore.LINE_SWEPT), lines)

        // (3) A sealed server that no longer opens (its key is gone, a byte changed).
        assertTrue(store().save(home, shown))
        val file = File(dir, "credentials_v1.json")
        val root = MiniJson.parse(file.readText()).jsonObject()!!.toMutableMap()
        val entry = root["jellyfin"].jsonObject()!!.toMutableMap()
        val ct = java.util.Base64.getDecoder().decode(entry["ct"] as String)
        ct[3] = (ct[3] + 1).toByte()
        entry["ct"] = java.util.Base64.getEncoder().encodeToString(ct)
        file.writeText(MiniJson.write(root + ("jellyfin" to entry)))
        lines.clear()
        assertTrue(store().sweep(deviceLocked = false))
        assertFalse(store().entryHeld())
        assertTrue(lines.toString(), lines.last() == ServerStore.LINE_SWEPT)
        assertTrue(lines.toString(), lines.none { it.contains(token) || it.contains("bad tag") || it.contains("media.example.org") })

        // (4) A sealed entry beside another save's file, and one whose removal failed.
        assertTrue(store().save(home, shown))
        whileCredentialWritesFail { assertFalse(store().remove()) }
        assertTrue(store().entryHeld())
        lines.clear()
        assertTrue(store().sweep(deviceLocked = false))
        assertFalse(store().entryHeld())
        assertFalse(config.exists())

        // Nothing stored: nothing to do, no line. Another name's entry is never touched.
        credentials.set("tmdb", "qa-dummy-token")
        lines.clear()
        assertFalse(store().sweep(deviceLocked = false))
        assertEquals(emptyList<String>(), lines)
        assertEquals("qa-dummy-token", credentials.get("tmdb"))
        assertEquals("server entry cleared at start: it was not a saved server", ServerStore.LINE_SWEPT)
    }

    @Test fun `the sweep judges nothing while the phone is locked, and says not removed when it cannot write`() {
        assertTrue(store().save(home, shown))
        config.delete()
        lines.clear()
        assertFalse(store().sweep(deviceLocked = true))
        assertTrue("left for the next unlocked start", store().entryHeld())
        assertEquals(emptyList<String>(), lines)
        whileCredentialWritesFail { assertFalse(store().sweep(deviceLocked = false)) }
        assertTrue(store().entryHeld())
        assertEquals(ServerStore.LINE_NOT_REMOVED, lines.last())
        assertNull(store().streamToken(stream("https://media.example.org")))
    }

    @Test fun `the setting offers Remove whenever anything is stored under the server's name`() {
        assertEquals(ServerSettingsView.SERVER, ServerRules.settingsView(setUp = true, entryHeld = true))
        assertEquals(ServerSettingsView.LEFT_OVER, ServerRules.settingsView(setUp = false, entryHeld = true))
        assertEquals(ServerSettingsView.FORM, ServerRules.settingsView(setUp = false, entryHeld = false))
        assertTrue(ServerRules.offersRemove(ServerSettingsView.SERVER))
        assertTrue(ServerRules.offersRemove(ServerSettingsView.LEFT_OVER))
        assertFalse(ServerRules.offersRemove(ServerSettingsView.FORM))
    }

    @Test fun `the key page says removed only when the key is gone`() {
        assertEquals("TMDB key removed", TmdbKeyLines.removal(gone = true))
        assertEquals("TMDB key not removed", TmdbKeyLines.removal(gone = false))
        credentials.set("tmdb", "qa-dummy-token")
        whileCredentialWritesFail { assertFalse(credentials.clear("tmdb")) }
        assertTrue(credentials.holds("tmdb"))
        assertTrue(credentials.clear("tmdb"))
        assertFalse(credentials.holds("tmdb"))
        assertTrue("nothing there is nothing left", credentials.clear("tmdb"))
    }

    @Test fun `no line and no text form ever carries the token`() {
        store().save(home, shown)
        store().credential(); store().tokenFor("http://elsewhere.example/x"); store().streamToken(stream("http://elsewhere.example"))
        store().remove()
        assertTrue(lines.toString(), lines.none { it.contains(token) })
        assertFalse(home.toString().contains(token))
    }
}
