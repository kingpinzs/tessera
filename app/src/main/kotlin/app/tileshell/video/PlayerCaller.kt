package app.tileshell.video

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * [PlayerCallerPort] over Android, for one `content` source (trust review C-M4). TRUST-TOUCHING: these are the only
 * reads behind [PlayerAccess.callerMayRead]. Each fails closed — an exception, a provider the shell cannot see or an
 * API the phone does not have is "no".
 *
 * `getLaunchedFromUid()` (API 34, the shell's minSdk) names the launcher only when it is the shell's own uid or the
 * launcher chose to share its identity (`ActivityOptions.setShareIdentityEnabled`); for any other app it is
 * `Process.INVALID_UID`. So for most other apps the uid-keyed checks cannot run, and what admits their source is
 * [hadAccessAtLaunch] — Android 15's `ComponentCaller.checkContentUriPermission`, which the platform answers for the
 * launcher without naming it. On Android 14 such a launcher's `content` source is refused.
 */
class AndroidPlayerCaller(private val activity: Activity, private val source: Uri) : PlayerCallerPort {
    override val launchedFromUid: Int
        get() = runCatching { activity.launchedFromUid }.getOrDefault(PlayerAccess.UNKNOWN_UID)

    override fun providerUid(): Int? = runCatching {
        source.authority?.let { activity.packageManager.resolveContentProvider(it, 0)?.applicationInfo?.uid }
    }.getOrNull()

    override fun holdsReadGrant(uid: Int): Boolean = runCatching {
        activity.checkUriPermission(source, -1, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    override fun providerAllowsRead(uid: Int): Boolean {
        if (Build.VERSION.SDK_INT < 35) return false
        return try {
            activity.checkContentUriPermissionFull(source, -1, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    override fun hadAccessAtLaunch(): Boolean {
        if (Build.VERSION.SDK_INT < 35) return false
        return try {
            activity.initialCaller.checkContentUriPermission(source, Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            // Thrown for a source that was not the launch intent's own, and when the shell itself has no access: "no".
            false
        }
    }
}
