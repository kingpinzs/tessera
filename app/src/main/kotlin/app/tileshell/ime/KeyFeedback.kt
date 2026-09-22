package app.tileshell.ime

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import app.tileshell.brand.KeyClick
import app.tileshell.diag.Diagnostics

/**
 * Decisions "Key sounds and vibration" (approximation, H12): the branding module's click on each key
 * press and `HapticFeedbackConstants.KEYBOARD_TAP`, each behind its own Settings toggle, both On by
 * default. The toggles are read from the keyboard's settings every time the keyboard shows.
 */
class KeyFeedback(context: Context, private val view: () -> View?) {
    var sounds = true
    var vibration = true

    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private var clickId = 0
    private var loaded = false

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            loaded = status == 0 && id == clickId
            Diagnostics.add("ime", "key click loaded=$loaded")
        }
        clickId = runCatching { pool.load(KeyClick.file(context).absolutePath, 1) }.getOrElse {
            Diagnostics.add("ime", "key click unavailable: $it")
            0
        }
    }

    /** Presses played, for the QA dump: proves the toggle reaches the sound, not just the setting. */
    var clicks = 0
        private set
    var buzzes = 0
        private set

    fun press() {
        if (sounds && loaded) {
            pool.play(clickId, CLICK_VOLUME, CLICK_VOLUME, 1, 0, 1f)
            clicks++
        }
        if (vibration) {
            if (view()?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) == true) buzzes++
        }
    }

    fun release() = pool.release()

    private companion object {
        const val CLICK_VOLUME = 0.5f
    }
}
