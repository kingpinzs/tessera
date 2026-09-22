package app.tileshell.applist

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.tileshell.apps.AppEntry
import app.tileshell.apps.ProfileKind
import app.tileshell.diag.Diagnostics

/**
 * Uninstalling an app from the app list's hold menu (Jeremy, 2026-09-22: "I should be able to long hold
 * app icon to bring up a quick menu to uninstall it"). W10M's hold menu carried "uninstall" under "pin to
 * start"; this is that item.
 *
 * The shell never uninstalls anything itself. It asks Android to, and Android shows its own confirmation
 * — which is the right place for the confirmation to live, because the shell is not entitled to remove an
 * app on a tap and W10M's own item opened a confirmation too.
 *
 * This needs REQUEST_DELETE_PACKAGES in the manifest. Without it the intent still starts, no exception is
 * thrown and the diagnostics record a request that looks fine — and Android's uninstaller finishes
 * silently, so the tap does nothing at all. Found by the UNINSTALL row on 2026-09-22.
 */
object AppUninstall {

    /**
     * Whether the item is offered at all. An item that opens a dialog Android then refuses is worse than
     * no item, so it is shown only where the uninstall can actually happen:
     *
     *  - not an inbox app: a system app has no uninstall, only "disable", which is a Settings page and
     *    not what was asked for;
     *  - not the shell itself: it is the Home app that is drawing the menu, and removing it from inside
     *    itself leaves the phone with no launcher;
     *  - main profile only: a work or private-space app is uninstalled for ITS user, which needs the
     *    per-user uninstall path and the profile's own permission, and is the profile owner's business.
     */
    fun canUninstall(entry: AppEntry, context: Context): Boolean =
        canUninstall(entry.component.packageName, entry.isSystem, entry.profile, context.packageName)

    /**
     * The rule itself, in plain values so it can be proved on the JVM: ComponentName is an unmocked
     * framework class in a unit test and an entry's package name reads back null through it.
     */
    fun canUninstall(packageName: String?, isSystem: Boolean, profile: ProfileKind, selfPackage: String): Boolean =
        !packageName.isNullOrEmpty() &&
            !isSystem &&
            profile == ProfileKind.MAIN &&
            packageName != selfPackage

    /** Hands the uninstall to Android, which asks the user. */
    fun request(context: Context, entry: AppEntry) {
        val packageName = entry.component.packageName
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            Diagnostics.add("applist", "uninstall requested for $packageName")
        } catch (e: ActivityNotFoundException) {
            Diagnostics.add("applist", "no uninstaller for $packageName: $e")
        }
    }
}
