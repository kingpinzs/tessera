package app.tileshell.cortana

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import app.tileshell.diag.Diagnostics

/** How Cortana was opened, which decides what the first frame shows. */
enum class CortanaMode {
    /** R6 §4.2.1: a tap on the Search key opens the home page, not listening. */
    HOME,

    /** R6 §4.2.3: a press-and-hold opens Cortana already listening. */
    LISTENING,
}

/**
 * Cortana as Android's assistant (phase 03 build task 1).
 *
 * Everything that opens Cortana opens the SAME session: the tile, the Search key on the drawn Start
 * bar, the assist gesture, `KEYCODE_ASSIST` and the side key. There is no second Cortana screen — an
 * activity would not be reachable from the keyguard, would not be what the assist gesture starts, and
 * would give two code paths to keep in step.
 *
 * The one exception is the role notice: without the assistant role the system will not show a session
 * at all, so the Search key opens [CortanaRoleNoticeActivity] instead (H30).
 */
class CortanaService : VoiceInteractionService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Diagnostics.add("cortana", "VoiceInteractionService created")
    }

    override fun onReady() {
        super.onReady()
        ready = true
        Diagnostics.add("cortana", "VoiceInteractionService ready")
    }

    override fun onShutdown() {
        ready = false
        Diagnostics.add("cortana", "VoiceInteractionService shutdown")
        super.onShutdown()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        ready = false
        Diagnostics.add("cortana", "VoiceInteractionService destroyed")
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: CortanaService? = null
        @Volatile private var ready: Boolean = false

        /** Whether the assistant service is actually running, for the checklist's liveness row (N-01). */
        val serviceReady: Boolean get() = ready && instance != null

        /** Held (and so callable) only while this app is the default assistant. */
        fun roleHeld(context: Context): Boolean =
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true

        const val EXTRA_MODE = "app.tileshell.cortana.MODE"

        /**
         * Open Cortana. Returns false when the session could not be shown — the caller then has the role
         * notice to fall back to, which [open] does for itself.
         */
        fun open(context: Context, mode: CortanaMode): Boolean {
            val service = instance
            if (service == null || !ready || !roleHeld(context)) {
                Diagnostics.add(
                    "cortana",
                    "open($mode) cannot show a session: instance=${service != null} ready=$ready role=${roleHeld(context)}; showing the role notice",
                )
                context.startActivity(
                    Intent(context, CortanaRoleNoticeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return false
            }
            val args = Bundle().apply { putString(EXTRA_MODE, mode.name) }
            service.showSession(args, 0)
            Diagnostics.add("cortana", "open($mode): session shown")
            return true
        }
    }
}
