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
 * - an explicit assignment always sticks; while its app is not launchable the slot is unassigned rather than
 *   falling back to another app (Decisions: "assigned app uninstalled or disabled -> slot becomes unassigned");
 * - role slots follow Android's role holder (default dialer, default SMS app, default browser);
 * - category slots are auto-assigned only when Android resolves exactly one app or a preferred app for the
 *   category intent (not the chooser); otherwise the slot is unassigned.
 */
class SlotResolver(private val context: Context, private val catalog: AppCatalog) {
    private val pm = context.packageManager

    fun resolve(slot: Slot, explicit: Map<Slot, ComponentName>): AppEntry? {
        explicit[slot]?.let { cn ->
            catalog.find(cn)?.let { return it }
            // Decisions: "assigned app uninstalled or disabled -> slot becomes unassigned". The assignment is kept, so
            // the slot shows that app again if it comes back, but nothing is chosen for the user in the meantime.
            Diagnostics.add("slots", "explicit ${slot.name} -> ${cn.flattenToShortString()} is not launchable; the slot is unassigned until it returns")
            return null
        }
        if (slot.role != null && slot != Slot.BROWSER) {
            return roleHolderPackage(slot)?.let { catalog.firstForPackage(it) }
        }
        return uniqueHandler(slot)?.let { entryFor(it) }
    }

    /**
     * Apps that handle the slot's intent (for the picker) — matched by the handler's COMPONENT, not its package (phase 16
     * build task 1, r3 D3). The shell is one package with many launcher activities: matched by package, the CALENDAR
     * picker listed every shell app (as MUSIC's did). A handler that is not itself a launcher entry — a browser's VIEW
     * activity, a camera's still-image activity, an app whose category sits on another activity — still stands for its
     * app, through the package's launcher entry.
     */
    fun candidates(slot: Slot): List<AppEntry> =
        pm.queryIntentActivities(intentFor(slot), 0)
            .mapNotNull { entryFor(ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
            .filter { it.profile == ProfileKind.MAIN }
            .distinctBy { it.key }

    /** The catalog entry a handler activity stands for: itself when it is a launcher entry, else its package's first. */
    private fun entryFor(handler: ComponentName): AppEntry? =
        catalog.find(handler) ?: catalog.firstForPackage(handler.packageName)

    private fun roleHolderPackage(slot: Slot): String? = when (slot) {
        Slot.PHONE -> context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        Slot.MESSAGING -> Telephony.Sms.getDefaultSmsPackage(context)
        else -> null
    }

    private fun intentFor(slot: Slot): Intent = when {
        slot == Slot.BROWSER -> Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        slot.stillImageCamera -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        else -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, slot.category!!)
    }

    /** One handler, or a preferred handler (resolveActivity returns a real app, not the resolver). */
    private fun uniqueHandler(slot: Slot): ComponentName? {
        val intent = intentFor(slot)
        val all = pm.queryIntentActivities(intent, 0)
        if (all.size == 1) return all.first().activityInfo.let { ComponentName(it.packageName, it.name) }
        if (all.isEmpty()) return null
        val best = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return null
        return if (all.any { it.activityInfo.packageName == best.packageName }) ComponentName(best.packageName, best.name) else null
    }
}
