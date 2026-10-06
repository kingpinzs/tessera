package app.tileshell.diag

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The diagnostics ring of a process that is not the launcher's (phase 17; Decisions "processes", r3 D16 — phase 15's
 * `Service.dump()` form). [Diagnostics] is one in-memory ring per process, so the `:camera`, `:video` and `:photosedit`
 * processes each declare one subclass of this service in their own process. It is read two ways:
 *  - `adb shell dumpsys activity service app.tileshell/<the subclass>` — the QA rows' read;
 *  - [RemoteRings.read] from the launcher's process — Settings > Diagnostics, so a phone row needs no adb (r3 V15).
 *
 * The process's activities bind it while they live ([RemoteRings.hold]), so it exists whenever there is a ring worth
 * reading. Not exported: only the shell binds it.
 */
abstract class RingDumpService : Service() {
    /** The process's name in the dump's first line: "camera", "video", "photosedit". */
    protected abstract val processLabel: String

    /** Lines above the ring: the process's own state, one `key=value` per line. */
    protected open fun status(writer: PrintWriter) {}

    private val binder = object : Binder() {
        override fun dump(fd: FileDescriptor, fout: PrintWriter, args: Array<out String>?) = print(fout)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) = print(writer)

    private fun print(writer: PrintWriter) {
        writer.println("tileshell $processLabel process pid=${Process.myPid()}")
        writer.println("--- status ---")
        status(writer)
        writer.println("--- diagnostics ---")
        Diagnostics.dump(writer)
        writer.flush()
    }
}

/** Binding a [RingDumpService]: holding it alive from its own process, and reading it from the launcher's. */
object RemoteRings {
    /**
     * Binds [service] for as long as the caller lives (an activity of that service's own process: onCreate →
     * [release] in onDestroy), so `dumpsys activity service` finds it.
     */
    fun hold(context: Context, service: Class<out RingDumpService>): ServiceConnection {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {}
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        context.bindService(Intent(context, service), connection, Context.BIND_AUTO_CREATE)
        return connection
    }

    fun release(context: Context, connection: ServiceConnection?) {
        if (connection != null) runCatching { context.unbindService(connection) }
    }

    /**
     * The dump of [service] as text, or null when its process is not running. Blocking — call it off the main thread.
     * It never starts the process: the bind carries no BIND_AUTO_CREATE, so a service that is not already running
     * simply does not connect inside [timeoutMs].
     */
    fun read(context: Context, service: Class<out RingDumpService>, timeoutMs: Long = 600): String? {
        val app = context.applicationContext
        val connected = CountDownLatch(1)
        var remote: IBinder? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { remote = binder; connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        if (!app.bindService(Intent(app, service), connection, 0)) {
            runCatching { app.unbindService(connection) }
            return null
        }
        return try {
            if (!connected.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
            val binder = remote ?: return null
            val pipe = ParcelFileDescriptor.createPipe()
            // One-way, so the remote side writes while this side reads: a full ring is larger than a pipe's buffer and
            // a synchronous dump would block both ends. The remote holds its own copy of the write end; ours is closed
            // here, so the read below ends when the remote dump does.
            pipe[1].use { write -> binder.dumpAsync(write.fileDescriptor, emptyArray()) }
            ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { it.readBytes().decodeToString() }
        } catch (e: Exception) {
            "ring read failed: ${e.javaClass.simpleName}"
        } finally {
            runCatching { app.unbindService(connection) }
        }
    }
}
