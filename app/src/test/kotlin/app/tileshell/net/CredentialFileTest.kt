package app.tileshell.net

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 (BS-3; Decisions "Trust"): the credential store's file half behind a stand-in cipher — the format, the
 * temp-and-rename write, re-reading on every use, and "a value that cannot be opened reads as absent, with a line that
 * holds no secret". The Keystore half is proven on the device.
 */
class CredentialFileTest {
    /** Not a real cipher: it only has to make the stored bytes differ from the value and fail when tampered with. */
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
            // The message quotes the input on purpose: the store must not pass a provider's message on.
            if (!tag.contentEquals(tag(name, sealed.iv, body))) throw IllegalStateException("bad tag for " + String(body))
            return body.mapIndexed { i, b -> (b.toInt() xor 0x5A xor sealed.iv[i % 12].toInt()).toByte() }.toByteArray()
        }

        private fun tag(name: String, iv: ByteArray, body: ByteArray): ByteArray {
            var h = 17
            for (b in name.toByteArray() + iv + body) h = h * 31 + b
            return byteArrayOf((h shr 24).toByte(), (h shr 16).toByte(), (h shr 8).toByte(), h.toByte())
        }
    }

    private val dir: File = Files.createTempDirectory("cred").toFile()
    private val file = File(dir, "credentials_v1.json")
    private val lines = mutableListOf<String>()
    private fun store(cipher: CredentialCipher = FakeCipher()) = CredentialFile(file, cipher) { lines += it }

    @Test fun `set, get, replace and remove by name`() {
        val s = store()
        assertNull(s.get("tmdb"))
        assertFalse(s.has("tmdb"))
        assertTrue(s.set("tmdb", "qa-dummy-token"))
        assertTrue(s.set("jellyfin", "0123456789abcdef"))
        assertEquals("qa-dummy-token", s.get("tmdb"))
        assertEquals("0123456789abcdef", s.get("jellyfin"))
        assertTrue(s.set("tmdb", "second-token"))
        assertEquals("second-token", s.get("tmdb"))
        assertTrue(s.remove("tmdb"))
        assertNull(s.get("tmdb"))
        assertEquals("0123456789abcdef", s.get("jellyfin"))
        assertFalse(s.remove("tmdb"))
    }

    @Test fun `the file is a map from a name to an iv and a ciphertext and holds no value`() {
        store().set("tmdb", "qa-dummy-token")
        store().set("jellyfin", "server-access-token")
        val text = file.readText()
        val root = MiniJson.parse(text).jsonObject()!!
        assertEquals(setOf("tmdb", "jellyfin"), root.keys)
        for ((_, v) in root) {
            val o = v.jsonObject()!!
            assertEquals(setOf("iv", "ct"), o.keys)
            assertEquals(12, java.util.Base64.getDecoder().decode(o.jsonString("iv")).size)
        }
        assertFalse(text.contains("qa-dummy-token"))
        assertFalse(text.contains("server-access-token"))
        assertFalse(text.contains(java.util.Base64.getEncoder().encodeToString("qa-dummy-token".toByteArray())))
    }

    @Test fun `every value gets a fresh iv`() {
        val s = store()
        s.set("tmdb", "same-value")
        val first = MiniJson.parse(file.readText()).jsonObject()!!["tmdb"].jsonObject()!!.jsonString("iv")
        s.set("tmdb", "same-value")
        val second = MiniJson.parse(file.readText()).jsonObject()!!["tmdb"].jsonObject()!!.jsonString("iv")
        assertTrue(first != second)
    }

    @Test fun `a write goes through a temp file that is renamed over the store`() {
        val s = store()
        File(dir, "credentials_v1.json.tmp").writeText("left by a killed process")
        s.set("tmdb", "qa-dummy-token")
        assertFalse(File(dir, "credentials_v1.json.tmp").exists())
        // The store's file and its (empty) lock file - and no temp file.
        assertEquals(listOf("credentials_v1.json", "credentials_v1.json.lock"), dir.list()!!.sorted())
        assertEquals("qa-dummy-token", s.get("tmdb"))
    }

    @Test fun `a write is made under the store's file lock, so two processes cannot interleave a read-modify-write`() {
        // B-8: the lock another process would hold is held here, on the same lock file. A write must wait for it or
        // fail - inside one JVM the platform answers a second lock on the file at once, with an exception - and must
        // never go ahead beside it.
        val s = store()
        assertTrue(s.set("tmdb", "first-value"))
        val lockFile = File(dir, "credentials_v1.json.lock")
        assertTrue("the store made its lock file", lockFile.isFile)
        java.io.RandomAccessFile(lockFile, "rw").channel.use { channel ->
            val held = channel.lock()
            lines.clear()
            assertFalse(s.set("tmdb", "second-value"))
            assertFalse(s.set("jellyfin", "other-value"))
            assertFalse(s.remove("tmdb"))
            assertEquals(listOf("tmdb: not saved (OverlappingFileLockException)", "jellyfin: not saved (OverlappingFileLockException)", "tmdb: not removed (OverlappingFileLockException)"), lines)
            // A read needs no lock: the rename keeps every read whole.
            assertEquals("first-value", s.get("tmdb"))
            held.release()
        }
        // The lock given back, the store writes again; the lock file holds nothing.
        assertTrue(s.set("tmdb", "second-value"))
        assertEquals("second-value", s.get("tmdb"))
        assertTrue(s.remove("tmdb"))
        assertEquals(0L, lockFile.length())
        assertTrue(lines.none { it.contains("first-value") || it.contains("second-value") || it.contains("other-value") })
    }

    @Test fun `the file is re-read on every use, so two holders never disagree`() {
        val cipher = FakeCipher()
        val a = store(cipher)
        val b = store(cipher)
        a.set("jellyfin", "token-1")
        assertEquals("token-1", b.get("jellyfin"))
        b.set("jellyfin", "token-2")
        assertEquals("token-2", a.get("jellyfin"))
        b.remove("jellyfin")
        assertNull(a.get("jellyfin"))
        // A holder that never read "tmdb" keeps it when it writes another name.
        a.set("tmdb", "t")
        b.set("jellyfin", "j")
        assertEquals("t", a.get("tmdb"))
    }

    @Test fun `a value that does not open reads as absent and its line holds no secret`() {
        val s = store()
        s.set("tmdb", "qa-dummy-token")
        // Tamper with the ciphertext.
        val root = MiniJson.parse(file.readText()).jsonObject()!!.toMutableMap()
        val entry = root["tmdb"].jsonObject()!!.toMutableMap()
        val ct = java.util.Base64.getDecoder().decode(entry.jsonString("ct"))
        ct[0] = (ct[0] + 1).toByte()
        entry["ct"] = java.util.Base64.getEncoder().encodeToString(ct)
        root["tmdb"] = entry
        file.writeText(MiniJson.write(root))
        lines.clear()
        assertNull(s.get("tmdb"))
        assertFalse(s.has("tmdb"))
        assertEquals("tmdb: unreadable (IllegalStateException)", lines.first())
        assertTrue(lines.none { it.contains("qa-dummy") || it.contains("bad tag") })
    }

    @Test fun `an entry moved under another name does not open`() {
        val s = store()
        s.set("tmdb", "qa-dummy-token")
        val root = MiniJson.parse(file.readText()).jsonObject()!!
        file.writeText(MiniJson.write(mapOf("jellyfin" to root["tmdb"])))
        assertNull(s.get("jellyfin"))
        assertNull(s.get("tmdb"))
    }

    @Test fun `a broken file reads as empty and the next write replaces it`() {
        file.parentFile.mkdirs()
        file.writeText("{ not json")
        val s = store()
        assertNull(s.get("tmdb"))
        assertTrue(lines.any { it.startsWith("store unreadable") })
        assertTrue(s.set("tmdb", "qa-dummy-token"))
        assertEquals("qa-dummy-token", s.get("tmdb"))
        file.writeText("""{"tmdb": {"iv": "not base64 !!", "ct": 5}, "x": []}""")
        assertNull(s.get("tmdb"))
    }

    @Test fun `no line ever carries a value`() {
        val s = store()
        s.set("tmdb", "SECRET-VALUE-1")
        s.get("tmdb"); s.has("tmdb"); s.set("tmdb", "SECRET-VALUE-2"); s.remove("tmdb"); s.remove("tmdb")
        assertEquals(listOf("tmdb: saved", "tmdb: saved", "tmdb: removed", "tmdb: nothing to remove"), lines)
        assertTrue(lines.none { it.contains("SECRET") })
    }
}
