package app.tileshell.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * The real [UriAccessPort]. Each answer is the platform's own; a call that throws is "no" (or "the platform does not
 * say"), never "yes".
 */
class AndroidUriAccess(context: Context) : UriAccessPort {
    private val app = context.applicationContext
    private val pm = app.packageManager

    override fun uidOf(packageName: String): Int? = try {
        pm.getPackageUid(packageName, 0)
    } catch (e: Exception) {
        null
    }

    override fun providerOwnerUid(authority: String): Int? =
        runCatching { pm.resolveContentProvider(authority, 0)?.applicationInfo?.uid }.getOrNull()

    override fun holdsWriteGrant(uri: String, uid: Int): Boolean = holdsGrant(uri, uid, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

    override fun holdsReadGrant(uri: String, uid: Int): Boolean = holdsGrant(uri, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun providerSaysMayWrite(uri: String, uid: Int): Boolean? = providerSays(uri, uid, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

    override fun providerSaysMayRead(uri: String, uid: Int): Boolean? = providerSays(uri, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun nameOfUid(uid: Int): String? = runCatching { pm.getNameForUid(uid) }.getOrNull()

    /** `checkUriPermission` with pid -1: the uid's explicit URI grants and nothing else. */
    private fun holdsGrant(uri: String, uid: Int, mode: Int): Boolean =
        runCatching { app.checkUriPermission(Uri.parse(uri), -1, uid, mode) == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    private fun providerSays(uri: String, uid: Int, mode: Int): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return runCatching { app.checkContentUriPermissionFull(Uri.parse(uri), -1, uid, mode) == PackageManager.PERMISSION_GRANTED }.getOrNull()
    }
}
