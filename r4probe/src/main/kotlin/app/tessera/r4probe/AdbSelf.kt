package app.tessera.r4probe

import android.content.Context
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * The phone's own adb, reached from this app (R4 is phone-only: PLAN.md R4 "phone-only, standalone"; Jeremy 2026-09-28,
 * the phone cannot be plugged into the PC). Wireless debugging is paired once with the code its "Pair device with
 * pairing code" dialog shows, then the app connects to its own adbd over TLS and runs shell commands as the shell uid —
 * which is how it starts the helper ([HelperMain]) that R4 is about. Kadb (Apache-2.0) is the pairing + TLS client.
 * The adb key lives in this app's files dir, so a pairing survives the app being killed (part 4a kills it on purpose).
 */
object AdbSelf {
    @Volatile private var configured = false
    @Volatile private var kadb: Kadb? = null
    /** True once a command has gone through this connection; false after any failure (Kadb's connectionCheck() reports
     *  false for a fresh connection that has carried nothing yet, which read as "not connected"). */
    @Volatile private var alive = false

    private fun configure(ctx: Context) {
        if (configured) return
        KadbCert.configure(OkioFilePrivateKeyStore("${ctx.filesDir.absolutePath}/adbkey.pem".toPath(), FileSystem.SYSTEM))
        configured = true
    }

    /** The last pairing's result line ("paired with …" or "PAIR FAILED: …"), for the run that waits on it. */
    @Volatile var lastPair: String? = null

    /** Pairs with the code shown in Wireless debugging's pairing dialog. Blocks; call off the main thread. */
    fun pair(ctx: Context, host: String, port: Int, code: String): String = (try {
        configure(ctx)
        runBlocking { Kadb.pair(host, port, code) }
        "paired with $host:$port"
    } catch (e: Throwable) { "PAIR FAILED: ${e.javaClass.simpleName}: ${e.message}" }).also { lastPair = it }

    /** Connects to the phone's own adbd (Wireless debugging's IP address & port). Blocks. */
    fun connect(ctx: Context, host: String, port: Int): String = try {
        configure(ctx)
        runCatching { kadb?.close() }
        alive = false
        kadb = Kadb.create(host, port)
        val probe = kadb!!.shell("echo r4")
        alive = probe.allOutput.contains("r4")
        if (alive) "connected to $host:$port" else "CONNECT FAILED: no answer on $host:$port"
    } catch (e: Throwable) { kadb = null; alive = false; "CONNECT FAILED: ${e.javaClass.simpleName}: ${e.message}" }

    fun connected(): Boolean = kadb != null && alive

    /** Drops the connection (Wi-Fi went away and adbd came back on a new port). */
    fun disconnect() { runCatching { kadb?.close() }; kadb = null; alive = false }

    /** Runs one shell command as the shell uid; (exit code, output). Blocks. */
    fun shell(cmd: String): Pair<Int, String> {
        val k = kadb ?: return -1 to "not connected"
        return try {
            val r = k.shell(cmd)
            alive = true
            r.exitCode to r.allOutput
        } catch (e: Throwable) { alive = false; -1 to "SHELL FAILED: ${e.javaClass.simpleName}: ${e.message}" }
    }
}
