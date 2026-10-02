package app.tileshell.people

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.ShellRoot
import app.tileshell.ui.components.dismissOverlay

/**
 * People (phase 16): the W10M People hub over Android's Contacts provider, an app inside the shell APK as Music and
 * Alarms & Clock are — a launcher activity with its own task, `CATEGORY_APP_CONTACTS`, so the PEOPLE slot can point at
 * it. One activity hosts every page (r3 D11): `singleTask`, so the People tile, an App Shortcut or another app's
 * intent re-routes the running app ([onNewIntent]) instead of stacking a second one.
 *
 * It is exported with VIEW / EDIT / INSERT / INSERT_OR_EDIT / PICK handlers, so any app can start it. What an intent
 * may do is [PeopleIntents]' and nothing more (Decisions "Trust" (c)): it chooses what is shown. An INSERT fills the
 * editor in and saves nothing without the user's tap, on the phone whatever account it named; an EDIT on a contact the
 * write guard keeps read-only opens its card; a PICK hands back the one contact or phone URI the user tapped, and only
 * to a caller that started People for a result — with no caller it is the plain list.
 *
 * `testTagsAsResourceId` on the root is the QA contract every shell-owned window signs.
 */
class PeopleActivity : ComponentActivity() {
    private val nav = PeopleNav()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("people", "PeopleActivity created")
        hideSystemBars()
        route(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            // A Back that closes an overlay is applied at once, so a touch right after it is not hit-tested against
            // what it closed (L13-3's rule).
            override fun handleOnBackPressed() {
                var handled = false
                dismissOverlay { handled = nav.back() }
                // Back out of the last page: a PICK ends cancelled, so pick mode is never left behind.
                if (!handled) { if (nav.route is PeopleRoute.Pick) finishPick(null) else finish() }
            }
        })
        setContent {
            ShellRoot {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    PeopleApp(
                        nav = nav,
                        host = PeopleHost(
                            onBack = { onBackPressedDispatcher.onBackPressed() },
                            onWindows = { goHome() },
                            finishPick = ::finishPick,
                            finish = { finish() },
                        ),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    private fun route(intent: Intent?) {
        val asked = if (intent == null) PeopleRoute.Open(null) else PeopleIntents.route(intent.action, intent.dataString, resolvedType(intent), IntentExtras(intent))
        // A PICK is honoured only for a caller that can receive its result; with none it is the plain list.
        val route = PeopleIntents.honoured(asked, hasCaller = callingActivity != null)
        // The action is the caller's text: the line holds it only when it is one the activity handles (PeopleIntents.loggedAction).
        Diagnostics.add("people", PeopleIntents.openLine(intent?.action, route))
        if (route != asked) Diagnostics.add("people", PeopleIntents.PICK_NO_CALLER)
        nav.open(route)
    }

    /** The intent's own type, else what the Contacts provider says its data is; never another provider's answer. */
    private fun resolvedType(intent: Intent): String? = intent.type
        ?: intent.data?.takeIf { it.scheme == "content" && it.authority == PeopleIntents.AUTHORITY }?.let { runCatching { contentResolver.getType(it) }.getOrNull() }

    /**
     * Ends an `ACTION_PICK` (Trust (c)): the result is the ONE contact lookup URI or phone data URI the user tapped,
     * with a read grant for that URI alone ([PeopleIntents.PICK_RESULT_FLAGS]) and nothing else — no ClipData, no
     * extras. Null — Back — cancels. Pick mode is entered only with a caller ([route]); the result and its "granted"
     * line are held to the same rule here, so neither exists without one.
     */
    private fun finishPick(picked: Uri?) {
        if (picked == null || callingActivity == null) {
            setResult(RESULT_CANCELED)
            Diagnostics.add("people", PeopleIntents.PICK_CANCELLED)
        } else {
            setResult(RESULT_OK, Intent().setData(picked).addFlags(PeopleIntents.PICK_RESULT_FLAGS))
            Diagnostics.add("people", PeopleIntents.pickGrantedLine(phone = picked.pathSegments.firstOrNull() == "data"))
        }
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * The extras of an intent any app may have sent. Reading a Bundle that holds a class this app cannot load throws,
     * so every read is guarded and a value of the wrong type is no value.
     */
    private class IntentExtras(private val intent: Intent) : PeopleIntents.Extras {
        @Suppress("DEPRECATION")
        override fun string(key: String): String? = (runCatching { intent.extras?.get(key) }.getOrNull() as? CharSequence)?.toString()
    }

    companion object {
        /** The same key SettingsActivity.EXTRA_PAGE uses (build task 9's rule). */
        const val EXTRA_PAGE = PeopleIntents.EXTRA_PAGE
    }
}
