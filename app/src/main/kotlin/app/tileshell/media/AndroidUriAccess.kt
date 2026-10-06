package app.tileshell.media

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Process

/**
 * The real [UriAccessPort], for the activity another app started. TRUST-TOUCHING and deliberately without logic: each
 * method is ONE platform call inside [ask], which hands back the platform's raw answer or the class of what it threw.
 * No answer is compared, defaulted or turned into a yes or a no here — `UriAccessRules` does that, under unit tests;
 * `media/UriAccessWiringScanTest` holds this file to that form.
 */
class AndroidUriAccess(private val activity: Activity) : UriAccessPort {
    override val apiLevel: Int get() = Build.VERSION.SDK_INT

    override fun shellUid(): Int = Process.myUid()

    override fun uidOf(packageName: String): Platform<Int> = ask { activity.packageManager.getPackageUid(packageName, 0) }

    override fun provider(authority: String): Platform<ProviderFacts?> =
        ask { activity.packageManager.resolveContentProvider(authority, 0)?.let { ProviderFacts(it.applicationInfo.uid, it.forceUriPermissions) } }

    override fun holdsGrant(uri: String, uid: Int, modeFlag: Int): Platform<Int> = ask { activity.checkUriPermission(Uri.parse(uri), -1, uid, modeFlag) }

    override fun providerSays(uri: String, uid: Int, modeFlag: Int): Platform<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return Platform.Threw(BELOW_API_35)
        return ask { activity.checkContentUriPermissionFull(Uri.parse(uri), -1, uid, modeFlag) }
    }

    override fun launchAnswer(uri: String, modeFlag: Int): Platform<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return Platform.Threw(BELOW_API_35)
        return ask { activity.initialCaller.checkContentUriPermission(Uri.parse(uri), modeFlag) }
    }

    override fun launchedFromUid(): Platform<Int> = ask { activity.launchedFromUid }

    override fun nameOfUid(uid: Int): Platform<String?> = ask { activity.packageManager.getNameForUid(uid) }

    /** The platform's answer to [question], or the class of what it threw — an Error too: nothing here fails open. */
    private inline fun <T> ask(question: () -> T): Platform<T> = try {
        Platform.Said(question())
    } catch (e: Throwable) {
        Platform.Threw(e.javaClass.simpleName)
    }

    private companion object {
        const val BELOW_API_35 = "BelowApi35"
    }
}
