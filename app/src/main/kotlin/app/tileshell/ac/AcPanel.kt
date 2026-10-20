package app.tileshell.ac

import android.app.PendingIntent
import android.content.Intent

/** The `[ac] closed by <word>` reasons (build task 5; R7 §4.2, Y8, round 4 D6 / D9). */
enum class CloseReason(val word: String) {
    FLICK("flick"),
    DRAG("drag"),
    BACK("back"),
    TAP("tap"),
    NOTIFICATION("notification"),
    ACTION("action"),
    SYSTEM("system"),
}

/**
 * What the action center's parts (the quick-action grid, build task 6; the notification list, task 7) may ask of the
 * open panel (task 5). CONTRACT (docs/plan/qa/phase-04/BUILD-CONTRACT.md): task 5 implements it; tasks 6 and 7 call it.
 */
interface AcPanelControl {
    /** RV10's px per epx in the panel's window (3 on the 1080-px AVD). */
    val pxPerEpx: Float

    /** Whether the quick-action grid is expanded (4 × n) or collapsed (the first four). */
    val expanded: Boolean

    /**
     * The tap-close wipe (R7 §4.2.5–4.2.6, Y8): the handle vanishes, the background wipes to the top in 216 ms; then
     * [then] runs (an app or a Settings page opening). Logs `[ac] closed by <reason.word>`.
     */
    fun closeWithWipe(reason: CloseReason, then: () -> Unit = {})

    /** Starts [intent] (an activity) from the panel, closing it with the tap wipe first (a quick action that opens something). */
    fun launch(intent: Intent, reason: CloseReason = CloseReason.TAP)

    /**
     * Sends another app's [pendingIntent] (a notification's content, action or reply) with the background-start opt-in
     * (build task 1 f). When it starts an activity (`isActivity`) the panel closes with the tap wipe first
     * (`closed by <reason>`); otherwise the panel stays open (round 4 D6). Returns false when the send failed (a
     * cancelled PendingIntent: `[notif] action failed`, the panel stays).
     */
    fun send(pendingIntent: PendingIntent, fillIn: Intent? = null, reason: CloseReason): Boolean

    /**
     * The reply box gained or lost focus (build task 7): while [focused] the panel window takes key focus for the
     * keyboard and stops above the keyboard's insets (task 1 h).
     */
    fun replyFocus(focused: Boolean)
}
