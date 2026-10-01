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
 * write guard keeps read-only opens its card; a PICK hands back the one contact or phone URI the user tapped.
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
                if (!handled) finish()
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
        val route = if (intent == null) PeopleRoute.Open(null) else PeopleIntents.route(intent.action, intent.dataString, resolvedType(intent), IntentExtras(intent))
        Diagnostics.add("people", "open ${intent?.action ?: "no action"} -> ${describe(route)}")
        nav.open(route)
    }

    /** The intent's own type, else what the Contacts provider says its data is; never another provider's answer. */
    private fun resolvedType(intent: Intent): String? = intent.type
        ?: intent.data?.takeIf { it.scheme == "content" && it.authority == PeopleIntents.AUTHORITY }?.let { runCatching { contentResolver.getType(it) }.getOrNull() }

    /**
     * Ends an `ACTION_PICK` (Trust (c)): the result is the ONE contact lookup URI or phone data URI the user tapped,
     * with a read grant for that URI alone. Null — Back, or nothing to pick — cancels.
     */
    private fun finishPick(picked: Uri?) {
        if (picked == null) {
            setResult(RESULT_CANCELED)
            Diagnostics.add("people", "pick: cancelled")
        } else {
            setResult(RESULT_OK, Intent().setData(picked).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            Diagnostics.add("people", "pick: one ${if (picked.pathSegments.firstOrNull() == "data") "phone" else "contact"} URI granted (read)")
        }
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The route without what the caller typed into it: a name or a number never reaches the diagnostics ring. */
    private fun describe(route: PeopleRoute): String = when (route) {
        is PeopleRoute.Open -> "open page=${route.page?.id ?: "default"}"
        is PeopleRoute.Card -> "card"
        is PeopleRoute.Edit -> "edit"
        is PeopleRoute.Insert -> "insert (prefilled, unsaved)"
        is PeopleRoute.InsertOrEdit -> "insert or edit (prefilled, unsaved)"
        is PeopleRoute.Pick -> "pick ${route.kind.name.lowercase()}"
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
