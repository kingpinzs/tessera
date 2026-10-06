package app.tileshell.camera

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import app.tileshell.diag.RingDumpService

/**
 * The `:camera` process's diagnostics ring (phase 17; Decisions "processes"): `dumpsys activity service
 * app.tileshell/.camera.CameraDumpService`.
 *
 * The process's activities bind it while they live. A capture request the output guard refuses lives for a few
 * milliseconds — its line (`[camera] refused output: no grant`) is written and the activity is gone — so an activity
 * also asks the service to [linger]: started as well as bound, it stays readable for a minute after the last activity
 * left, then stops itself. Not a foreground service, and it holds nothing but the ring.
 */
class CameraDumpService : RingDumpService() {
    override val processLabel = "camera"

    private val handler = Handler(Looper.getMainLooper())
    private val stop = Runnable { stopSelf() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(stop)
        handler.postDelayed(stop, LINGER_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stop)
        super.onDestroy()
    }

    companion object {
        const val LINGER_MS = 60_000L

        /** Keeps the ring readable for [LINGER_MS] from now. Called while an activity of the process is in front. */
        fun linger(context: Context) {
            runCatching { context.startService(Intent(context, CameraDumpService::class.java)) }
        }
    }
}
