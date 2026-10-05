package app.tileshell.video

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.diag.Diagnostics
import app.tileshell.net.CredentialStore
import app.tileshell.net.HeaderText
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.OutlinedField
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.launch

/** Movies & TV's TMDB key setting (Q-17-1 (a); build task 12). */
data object TmdbKeySub : HubSub {
    override val title = "TMDB key"
}

/**
 * The TMDB key setting: enter (typed or pasted), replace, remove. TRUST-TOUCHING.
 *
 * The owner's read token goes from the field straight into the credential store and nowhere else: the field is a
 * secret one (dots on screen and in a UI dump), it is emptied the moment the token is saved, a saved token is never
 * shown again — the page only says that one is saved — and the lines written here say "saved" or "removed", never
 * what. Saving over a saved token replaces it.
 *
 * A value that is not printable ASCII with no space inside (the ends are trimmed) is refused: the page says so
 * (`tmdb_key_error`), the field is emptied and nothing is stored.
 *
 * Tags: `tmdb_key_status`, `tmdb_key_field`, `tmdb_key_error`, `tmdb_key_save`, `tmdb_key_remove`.
 */
@Composable
fun TmdbKeyPage() {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var saved by remember { mutableStateOf<Boolean?>(null) }
    var typed by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { saved = VideoCalls.io("TMDB key read", false) { CredentialStore.of(context).has(CredentialStore.TMDB) } }

    fun save() {
        if (typed.isBlank()) return
        // Printable ASCII with no space inside, or nothing is stored (B-1): a key with a line break in it would be
        // refused as a header by the platform, in an exception that quotes it.
        val token = HeaderText.pastedSecret(typed)
        typed = ""
        focus.clearFocus(force = true)
        keyboard?.hide()
        if (token == null) {
            refused = true
            Diagnostics.add("video", "TMDB key refused: not a key's characters")
            return
        }
        refused = false
        scope.launch {
            val ok = VideoCalls.io("TMDB key save", false) { CredentialStore.of(context).set(CredentialStore.TMDB, token) }
            Diagnostics.add("video", if (ok) "TMDB key saved" else "TMDB key not saved")
            saved = VideoCalls.io("TMDB key read", false) { CredentialStore.of(context).has(CredentialStore.TMDB) }
        }
    }

    // focusable: the page, not the field, is what a cleared focus falls back to (see BrowsePage).
    Column(Modifier.fillMaxSize().focusable().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 16.dp).testTag("hub_page:tmdbkey")) {
        BasicText(
            "Film search uses The Movie Database (TMDB). Paste your own TMDB API read access token here. It is kept encrypted on this phone and is never shown again.",
            style = ShellType.body.copy(color = Color.White),
        )
        BasicText(
            when (saved) { true -> "A key is saved."; false -> "No key is saved."; null -> "" },
            Modifier.padding(top = 16.dp).testTag("tmdb_key_status"), style = ShellType.base.copy(color = if (saved == true) colors.accent else Color.White),
        )
        BasicText(if (saved == true) "Replace it with another key" else "Your key", Modifier.padding(top = 20.dp, bottom = 6.dp), style = ShellType.body.copy(color = Color.White))
        OutlinedField(
            value = typed, onValueChange = { typed = it; if (it.isNotEmpty()) refused = false }, tag = "tmdb_key_field", modifier = Modifier.fillMaxWidth(),
            maxLength = HeaderText.MAX_SECRET, placeholder = "TMDB read access token", onImeAction = { save() }, secret = true,
        )
        if (refused) {
            BasicText(HeaderText.TEXT_BAD_SECRET, Modifier.padding(top = 12.dp).testTag("tmdb_key_error"), style = ShellType.body.copy(color = Color.White))
        }
        Row(Modifier.padding(top = 16.dp)) {
            HubButton(if (saved == true) "Replace" else "Save", "tmdb_key_save", enabled = typed.isNotBlank()) { save() }
            Spacer(Modifier.width(12.dp))
            HubButton("Remove", "tmdb_key_remove", enabled = saved == true) {
                scope.launch {
                    VideoCalls.io("TMDB key remove", false) { CredentialStore.of(context).remove(CredentialStore.TMDB) }
                    Diagnostics.add("video", "TMDB key removed")
                    saved = VideoCalls.io("TMDB key read", false) { CredentialStore.of(context).has(CredentialStore.TMDB) }
                }
            }
        }
    }
}
