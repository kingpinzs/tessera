package app.tileshell.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.tileshell.diag.Diagnostics
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The shell's credential store (phase 17; Decisions "Trust", BS-3; phase 20 reuses it). It keeps the two secrets the
 * shell holds for the owner — the TMDB read token he pastes into Movies & TV's settings (Q-17-1 (a)) and the media
 * server's access token — encrypted with a key that never leaves the Android Keystore.
 *
 * TRUST-TOUCHING. The rules this file keeps:
 *  - a value is only ever held in memory by its caller; nothing here logs, formats or throws a value;
 *  - every diagnostics line names the entry ("tmdb", "jellyfin") and what happened, nothing else (C-32);
 *  - a value that cannot be decrypted reads as absent — never as garbage, never as an exception to the caller;
 *  - the file is written to a temp file and renamed over the old one, and re-read on every use, so two processes
 *    (`:video`, and the launcher's from phase 20) never act on a stale copy;
 *  - a read-modify-write, and the making of the Keystore key, happen under a lock file's exclusive lock, so two
 *    processes cannot each write over the other's entry or each make a key (B-8).
 */
interface CredentialCipher {
    /** A fresh IV and the ciphertext of [plain], bound to [name] (an entry moved under another name does not open). */
    fun seal(name: String, plain: ByteArray): Sealed

    /** The plain bytes; throws when the ciphertext, the IV or the name is not what [seal] produced. */
    fun open(name: String, sealed: Sealed): ByteArray

    class Sealed(val iv: ByteArray, val ciphertext: ByteArray)
}

/**
 * The file half: a JSON map from a name to its IV and ciphertext, Base64 (BS-3). Free of Android types, so the format,
 * the temp-and-rename write and the failure rules are unit-tested with a stand-in cipher.
 *
 * @param log one diagnostics message per event; it is given the entry's name and the outcome only
 */
class CredentialFile(private val file: File, private val cipher: CredentialCipher, private val log: (String) -> Unit) {

    /** The value saved under [name], or null when there is none or it cannot be read. Reads the file every time. */
    fun get(name: String): String? {
        val entry = synchronized(LOCK) { read() }[name] ?: return null
        return try {
            String(cipher.open(name, entry), Charsets.UTF_8)
        } catch (e: Exception) {
            // The class only: a provider's message can quote its input.
            log("$name: unreadable (${e.javaClass.simpleName})")
            null
        }
    }

    /** True when an entry is saved under [name] and opens. */
    fun has(name: String): Boolean = get(name) != null

    /** Saves [value] under [name], replacing what was there. False (and nothing changed) when it could not be written. */
    fun set(name: String, value: String): Boolean = try {
        locked {
            val all = read()
            // Sealing is inside the lock too: it is what makes the Keystore key the first time (B-8).
            all[name] = cipher.seal(name, value.toByteArray(Charsets.UTF_8))
            write(all)
        }
        log("$name: saved")
        true
    } catch (e: Exception) {
        log("$name: not saved (${e.javaClass.simpleName})")
        false
    }

    /** Removes [name]. True when an entry was there. */
    fun remove(name: String): Boolean = try {
        val had = locked {
            val all = read()
            (all.remove(name) != null).also { if (it) write(all) }
        }
        log("$name: ${if (had) "removed" else "nothing to remove"}")
        had
    } catch (e: Exception) {
        log("$name: not removed (${e.javaClass.simpleName})")
        false
    }

    /**
     * One read-modify-write at a time, in this process ([LOCK]) and across processes (B-8): an exclusive lock on the
     * store's own lock file, held from the read to the rename and given back however [block] ends. Another process's
     * write waits here for it. The lock file is empty and stays; a read takes no lock — the rename keeps it whole.
     */
    private fun <T> locked(block: () -> T): T = synchronized(LOCK) {
        file.parentFile?.mkdirs()
        RandomAccessFile(File(file.parentFile, file.name + LOCK_SUFFIX), "rw").channel.use { channel ->
            val lock = channel.lock()
            try {
                block()
            } finally {
                lock.release()
            }
        }
    }

    private fun read(): MutableMap<String, CredentialCipher.Sealed> {
        val out = LinkedHashMap<String, CredentialCipher.Sealed>()
        if (!file.exists()) return out
        val root = try {
            MiniJson.parse(file.readText(Charsets.UTF_8)).jsonObject()
        } catch (e: Exception) {
            log("store unreadable (${e.javaClass.simpleName})")
            null
        } ?: return out
        for ((name, v) in root) {
            val o = v.jsonObject() ?: continue
            val iv = o.jsonString("iv")?.let(::decode) ?: continue
            val ct = o.jsonString("ct")?.let(::decode) ?: continue
            out[name] = CredentialCipher.Sealed(iv, ct)
        }
        return out
    }

    /** Temp file, flushed to the disk, then renamed over the store: a reader sees the old file or the new one, never a part. */
    private fun write(all: Map<String, CredentialCipher.Sealed>) {
        val json = MiniJson.write(all.mapValues { (_, s) -> linkedMapOf("iv" to encode(s.iv), "ct" to encode(s.ciphertext)) })
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temp).use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
            runCatching { out.fd.sync() }
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            throw java.io.IOException("rename failed")
        }
    }

    private fun encode(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun decode(s: String): ByteArray? = try { Base64.getDecoder().decode(s) } catch (e: IllegalArgumentException) { null }

    private companion object {
        /** One writer at a time inside a process (a second file lock from one process is an error, not a wait). */
        val LOCK = Any()
        const val LOCK_SUFFIX = ".lock"
    }
}

/** AES-256-GCM under a non-exportable Android Keystore key (BS-3): a fresh 12-byte IV per value, the entry's name as AAD. */
class KeystoreCipher(private val alias: String) : CredentialCipher {
    /**
     * The key. It is MADE only for a seal ([create]) — which the store calls under its file lock, so two processes
     * cannot each find no key and each make one (B-8). Opening with no key fails, and the value reads as absent.
     */
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "no key" }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // No user authentication: the player reads the server's token with the screen locked (BS-3).
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }

    override fun seal(name: String, plain: ByteArray): CredentialCipher.Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))   // the Keystore makes the random 12-byte IV
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(plain)
        return CredentialCipher.Sealed(cipher.iv, ct)
    }

    override fun open(name: String, sealed: CredentialCipher.Sealed): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(TAG_BITS, sealed.iv))
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(sealed.ciphertext)
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}

object CredentialStore {
    /** BS-3. */
    const val KEY_ALIAS = "tessera_credentials_v1"
    const val FILE_NAME = "credentials_v1.json"

    /** The entries' names. */
    const val TMDB = "tmdb"
    const val JELLYFIN = "jellyfin"

    /** The store over `files/credentials_v1.json`. Its calls touch the disk and the Keystore: off the main thread. */
    fun of(context: Context): CredentialFile =
        CredentialFile(File(context.applicationContext.filesDir, FILE_NAME), KeystoreCipher(KEY_ALIAS)) { Diagnostics.add("cred", it) }
}
