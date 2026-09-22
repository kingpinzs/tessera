package app.tileshell.cortana

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** The framework asks this for the session window (phase 03 build task 1). */
class CortanaSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = CortanaSession(this)
}

/**
 * A transparent activity whose only job is to raise Android's unlock prompt for the "Unlock to
 * continue" card (H12). `KeyguardManager.requestDismissKeyguard` needs an Activity, and a
 * VoiceInteractionSession is not one — this is the smallest thing that is.
 */
object UnlockBridge {
    @Volatile
    private var listener: ((Boolean) -> Unit)? = null

    fun await(onResult: (unlocked: Boolean) -> Unit) {
        listener = onResult
    }

    fun deliver(unlocked: Boolean) {
        val current = listener
        listener = null
        current?.invoke(unlocked)
    }

    fun start(context: Context) {
        context.startActivity(
            android.content.Intent(context, CortanaUnlockActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
