package app.tileshell.clock

import android.content.Intent
import android.os.Bundle

/**
 * The real [ExtrasPort]: an intent's extras (or a bundle inside them) exactly as the platform hands them over.
 * Deliberately without logic and allowed to throw — a bundle holding another app's own Parcelable throws when it is
 * unpacked. [SafeExtras] is the only reader, and catches everything.
 */
class AndroidExtras private constructor(private val bundle: () -> Bundle?) : ExtrasPort {
    constructor(intent: Intent?) : this({ intent?.extras })

    @Suppress("DEPRECATION")
    override fun value(key: String): Any? = bundle()?.get(key).let { v -> if (v is Bundle) AndroidExtras { v } else v }
}
