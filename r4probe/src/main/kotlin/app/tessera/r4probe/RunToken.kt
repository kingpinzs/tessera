package app.tessera.r4probe

import android.content.Context
import java.io.File
import java.security.SecureRandom

/**
 * The secret a `run` extra must carry. ProbeActivity is exported (it is the launcher entry) and the extras drive
 * shell-uid work, so another app must not be able to start an unattended run; Android 15+ only names an activity's
 * caller when the caller opts in, which `am start` does not. The token lives in this app's private files: the app puts it
 * into the commands it composes itself (the planned restart in part 4a), and test tooling reads it with run-as (debug
 * builds only). Nothing else can.
 */
object RunToken {
    fun get(ctx: Context): String {
        val f = File(ctx.filesDir, "run-token")
        runCatching { f.readText().trim() }.getOrNull()?.takeIf { it.length == 32 }?.let { return it }
        val t = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        f.writeText(t)
        return t
    }
}
