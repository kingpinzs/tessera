package app.tileshell.testclient

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

/**
 * Phase 11 QA (tile quick actions): tileclient-b's dynamic App Shortcuts, set by verbs so each row starts from the
 * exact shortcut state it asserts. ONE implementation, reached two ways:
 *
 *   adb shell am start -n app.tileshell.testclient.b/app.tileshell.testclient.VerbActivity --es verb reset
 *   adb shell am broadcast -n app.tileshell.testclient.b/app.tileshell.testclient.ShortcutVerbReceiver --es verb disable
 *
 * The broadcast (ShortcutVerbReceiver, declared in tileclient-b's manifest only) brings no window over Start, so a verb
 * sent while a burst is open leaves StartActivity resumed (T11-15). Both log the result line (tag "TileClient"); the
 * activity also shows it and the receiver returns it as the broadcast's result data.
 *
 * Verbs:
 *   reset       setDynamicShortcuts([qa_dyn]): qa_dyn alone, so qa_noicon and qa_dead are gone. Seeding runs it once
 *               after install: a dynamic shortcut exists only after its app has run (T11-30)
 *   disable     disableShortcuts([qa_dyn]); Android deletes a disabled dynamic shortcut that is not pinned (T11-15)
 *   badicon     setDynamicShortcuts([qa_dyn, qa_noicon]): qa_noicon ("No icon", rank 1) carries a content-URI icon that
 *               resolves to nothing — TestImageProvider throws FileNotFoundException for missing.png (T11-14, T11-33)
 *   deadtarget  setDynamicShortcuts([qa_dyn, qa_dead]): qa_dead ("Dead", rank 1) opens a component declared nowhere,
 *               so startShortcut fails with ActivityNotFoundException (T11-21)
 *   labels      setDynamicShortcuts([qa_dyn, qa_blank, qa_long]): qa_blank (rank 1) has a whitespace short label — the
 *               nearest a publisher can come to none, since setShortLabel rejects an empty one and the manifest parser
 *               drops a shortcut without shortcutShortLabel; qa_long (rank 2) has a label far wider than a satellite's
 *               (the phase 11 EDGE row: "no short label" and "very long labels")
 * qa_dyn is "Dyn", rank 0, with a resource icon (res/drawable/ic_qa_dyn, T11-33). Every intent is ACTION_VIEW to an
 * explicit component with qa_id = the shortcut's id, and every shortcut calls setActivity(tileclient-b's launcher
 * activity) (C-21), so the shell's per-activity query finds them on tileclient-b's tile.
 *
 * The verbs act only in app.tileshell.testclient.b: the three test APKs compile one shared VerbActivity, and a
 * tileclient-a run that published qa_dyn would attach it to tileclient-a's launcher activity (E1 would read 6, T11-30).
 *
 * setDynamicShortcuts is rate-limited while the app has no foreground activity (the broadcast route): the result then
 * reads `ok=false (rate-limited)`, and `adb shell cmd shortcut reset-throttling` clears it. A freshly installed or
 * force-stopped tileclient-b is in the stopped state, in which a broadcast is not delivered: the first verb after an
 * install goes through `am start` (as Seeding does).
 */
object ShortcutVerbs {
    const val TAG = "TileClient"
    const val PACKAGE = "app.tileshell.testclient.b"
    const val EXTRA_QA_ID = ShortcutActivity.EXTRA_QA_ID
    const val DYN = "qa_dyn"
    const val NO_ICON = "qa_noicon"
    const val DEAD = "qa_dead"
    const val BLANK = "qa_blank"
    const val LONG = "qa_long"
    const val LONG_LABEL = "An unusually long shortcut label for the QA row"
    const val MISSING_ICON_URI = "content://app.tileshell.testclient.b.images/missing.png"
    const val MISSING_CLASS = "app.tileshell.testclient.Missing"
    val VERBS = setOf("reset", "disable", "badicon", "deadtarget", "labels")

    fun perform(context: Context, verb: String): String {
        if (context.packageName != PACKAGE) {
            return "$verb: shortcut verbs run only in $PACKAGE, not ${context.packageName}; nothing published"
        }
        val sm = context.getSystemService(ShortcutManager::class.java)
        return when (verb) {
            "reset" -> publish(sm, verb, listOf(dyn(context)))
            "disable" -> {
                sm.disableShortcuts(listOf(DYN))
                "$verb: ok=true disabled=[$DYN] dynamic=${dynamicIds(sm)}"
            }
            "badicon" -> publish(sm, verb, listOf(dyn(context), noIcon(context)))
            "deadtarget" -> publish(sm, verb, listOf(dyn(context), dead(context)))
            "labels" -> publish(sm, verb, listOf(dyn(context), labelled(context, BLANK, " ", 1), labelled(context, LONG, LONG_LABEL, 2)))
            else -> "unknown verb $verb"
        }
    }

    private fun publish(sm: ShortcutManager, verb: String, shortcuts: List<ShortcutInfo>): String {
        val ok = sm.setDynamicShortcuts(shortcuts)
        return "$verb: ok=$ok${if (ok) "" else " (rate-limited)"} dynamic=${dynamicIds(sm)}"
    }

    private fun dynamicIds(sm: ShortcutManager) = sm.dynamicShortcuts.sortedBy { it.rank }.map { it.id }

    /** tileclient-b's launcher activity: every dynamic shortcut is attached to it (C-21). */
    private fun launcher(context: Context) = ComponentName(context, VerbActivity::class.java)

    private fun open(target: ComponentName, id: String) =
        Intent(Intent.ACTION_VIEW).setComponent(target).putExtra(EXTRA_QA_ID, id)

    private fun dyn(context: Context): ShortcutInfo {
        val icon = context.resources.getIdentifier("ic_qa_dyn", "drawable", context.packageName)
        check(icon != 0) { "no drawable/ic_qa_dyn in ${context.packageName}" }
        return ShortcutInfo.Builder(context, DYN)
            .setShortLabel("Dyn")
            .setRank(0)
            .setIcon(Icon.createWithResource(context, icon))
            .setIntent(open(launcher(context), DYN))
            .setActivity(launcher(context))
            .build()
    }

    private fun noIcon(context: Context): ShortcutInfo = ShortcutInfo.Builder(context, NO_ICON)
        .setShortLabel("No icon")
        .setRank(1)
        .setIcon(Icon.createWithContentUri(MISSING_ICON_URI))
        .setIntent(open(launcher(context), NO_ICON))
        .setActivity(launcher(context))
        .build()

    private fun labelled(context: Context, id: String, label: String, rank: Int): ShortcutInfo = ShortcutInfo.Builder(context, id)
        .setShortLabel(label)
        .setRank(rank)
        .setIntent(open(launcher(context), id))
        .setActivity(launcher(context))
        .build()

    private fun dead(context: Context): ShortcutInfo = ShortcutInfo.Builder(context, DEAD)
        .setShortLabel("Dead")
        .setRank(1)
        .setIntent(open(ComponentName(PACKAGE, MISSING_CLASS), DEAD))
        .setActivity(launcher(context))
        .build()
}
