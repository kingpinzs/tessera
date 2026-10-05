package app.tileshell.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Condition (d) of [CaptureOutputGuard]: may the app that asked for a capture write the output URI ITSELF? Asked of
 * the platform, never inferred from the intent's flags — an intent's grant flag is not checked against the caller when
 * the shell already holds the access (the guard's doc says why). TRUST: under the adversarial review with the guard.
 */
object CaptureCallerAccess {
    /**
     * True only when [callingPackage] (`Activity.getCallingPackage()`, the platform's own word for who started the
     * activity for a result) resolves to a uid and either
     *  - the provider behind [output] is declared by an app of that same uid (its own FileProvider: the owner of a
     *    provider may always write it), or
     *  - the platform says that uid may write [output]: it holds a write grant for it, or the provider itself answers
     *    that this uid may (MediaStore does for a row the caller owns).
     * A URI of another user's provider, an unknown authority or an unknown package is false.
     */
    fun callerMayWrite(context: Context, output: Uri, callingPackage: String?): Boolean {
        if (callingPackage == null || output.scheme != "content") return false
        val authority = output.authority ?: return false
        if (authority.isEmpty() || '@' in authority) return false
        val pm = context.packageManager
        val callerUid = try {
            pm.getPackageUid(callingPackage, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
        val owner = pm.resolveContentProvider(authority, 0)?.applicationInfo?.uid
        if (owner != null && owner == callerUid) return true
        return context.checkUriPermission(output, -1, callerUid, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}
