package app.tileshell.testclient

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Phase 11 QA (tile quick actions): the target of tileclient-a's five static App Shortcuts (res/xml/shortcuts.xml,
 * declared on the launcher activity VerbActivity; this activity is only their target, T11-13). It shows the `qa_id`
 * extra the shortcut carried, and nothing else, as the text of the TextView whose resource id is `shortcut_id`, so a
 * row reads WHICH shortcut ran from a uiautomator dump — `resource-id="app.tileshell.testclient.a:id/shortcut_id"`,
 * `text="qa_three"` — because `dumpsys activity` prints only "(has extras)" (the phase 02 E5 lesson; T11-47).
 *
 * Declared in tileclient-a's manifest only, not exported: `LauncherApps.startShortcut` starts it as its publisher.
 * tileclient-b and -b2 compile it (shared sources) and never declare it.
 */
class ShortcutActivity : Activity() {
    private lateinit var shortcutId: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply {
            text = "$packageName ShortcutActivity"
            setPadding(32, 96, 32, 8)
            textSize = 14f
        }
        shortcutId = TextView(this).apply {
            // The id is declared once in testapps/common/src/main/res/values/ids.xml and compiled into each APK under
            // that APK's own namespace, so this shared source looks it up by name instead of importing one APK's R.
            val res = resources.getIdentifier("shortcut_id", "id", packageName)
            check(res != 0) { "no id/shortcut_id in $packageName (testapps/common/src/main/res missing from its build)" }
            id = res
            setPadding(32, 8, 32, 32)
            textSize = 24f
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(shortcutId)
            },
        )
        show(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    private fun show(intent: Intent?) {
        val qaId = intent?.getStringExtra(EXTRA_QA_ID) ?: "-"
        shortcutId.text = qaId
        Log.i(TAG, "$packageName ShortcutActivity qa_id=$qaId")
    }

    companion object {
        /** The extra every fixture shortcut carries: the shortcut's own id. */
        const val EXTRA_QA_ID = "qa_id"
        private const val TAG = "TileClient"
    }
}
