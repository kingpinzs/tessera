package app.tileshell.tiles

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import app.tileshell.apps.AppCatalog
import app.tileshell.apps.AppEntry
import app.tileshell.apps.ProfileKind
import app.tileshell.diag.Diagnostics

/**
 * Resolves a default-layout slot to an app (phase 01 Decisions, interview Q1 and the derived line):
 * - an explicit assignment always sticks while its app is still launchable;
 * - role slots follow Android's role holder (default dialer, default SMS app, default browser);
 * - category slots are auto-assigned only when Android resolves exactly one app or a preferred app for the
 *   category intent (not the chooser); otherwise the slot is unassigned.
 */
class SlotResolver(private val context: Context, private val catalog: AppCatalog) {
    private val pm = context.packageManager

    fun resolve(slot: Slot, explicit: Map<Slot, ComponentName>): AppEntry? {
        explicit[slot]?.let { cn ->
            catalog.find(cn)?.let { return it }
            Diagnostics.add("slots", "explicit ${slot.name} -> ${cn.flattenToShortString()} no longer launchable; slot falls back")
        }
        val pkg = when {
            slot.role != null -> roleHolderPackage(slot)
            else -> uniqueHandlerPackage(slot)
        } ?: return null
        return catalog.firstForPackage(pkg)
    }

    /** Apps that handle the slot's intent (for the picker). */
    fun candidates(slot: Slot): List<AppEntry> {
        val packages = pm.queryIntentActivities(intentFor(slot), 0).map { it.activityInfo.packageName }.toSet()
        return catalog.apps.value.filter { it.component.packageName in packages && it.profile == ProfileKind.MAIN }
    }

    private fun roleHolderPackage(slot: Slot): String? = when (slot) {
        Slot.PHONE -> context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        Slot.MESSAGING -> Telephony.Sms.getDefaultSmsPackage(context)
        Slot.BROWSER -> uniqueHandlerPackage(slot)
        else -> null
    }

    private fun intentFor(slot: Slot): Intent = when {
        slot == Slot.BROWSER -> Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        slot.stillImageCamera -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        else -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, slot.category!!)
    }

    /** One handler, or a preferred handler (resolveActivity returns a real app, not the resolver). */
    private fun uniqueHandlerPackage(slot: Slot): String? {
        val intent = intentFor(slot)
        val all = pm.queryIntentActivities(intent, 0)
        if (all.size == 1) return all.first().activityInfo.packageName
        if (all.isEmpty()) return null
        val best = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val pkg = best.activityInfo.packageName
        return if (all.any { it.activityInfo.packageName == pkg }) pkg else null
    }
}
