package app.tileshell.net

import android.content.ClipboardManager
import android.content.Context
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import app.tileshell.diag.Diagnostics

/*
 * The Android side of the owner's ruling D2 (a) (the rules are `CredentialScreen.kt`'s). TRUST-TOUCHING and deliberately
 * without logic: each step is one platform call that carries out the rule's answer. `CredentialScreenWiringScanTest`
 * holds this file, the secret field and the two credential pages to this form.
 */

/** This process's windows and the credential pages showing in each (`:video` and the main process each have their own). */
private val secureWindows = SecureWindows<Window>()

/**
 * The FIRST statement of a page that takes a credential (D2 (a) 1): while the page is in the composition, its activity's
 * window carries FLAG_SECURE — no screenshot, no screen recording, and a blank Recents preview. Set as the page appears,
 * cleared as it leaves, by [SecureWindows]' count: never while another credential page is still up in the same window,
 * and nothing left on the window once the last has gone. Keyed on the window, so a recomposition does not touch it, and
 * an activity recreated by a configuration change has the page composed into its new window set.
 *
 * Its lines are written under [tag], the page's own (`video` or `music`).
 */
@Composable
fun SecurePage(tag: String) {
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        if (window == null) {
            Diagnostics.add(tag, "credential page shown: no window, screen capture NOT blocked")
            onDispose { }
        } else {
            secure(window, secureWindows.shown(window), tag)
            onDispose { secure(window, secureWindows.left(window), tag) }
        }
    }
}

/** The count's answer, carried out on the window: one flag call, and the step's line. */
private fun secure(window: Window, step: SecureStep, tag: String) {
    when (step) {
        SecureStep.SET -> window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        SecureStep.CLEAR -> window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        SecureStep.KEEP -> Unit
    }
    Diagnostics.add(tag, SecureWindows.line(step))
}

/**
 * The real [ClipPort]: one ClipboardManager call per method. What they throw is caught by [PastedCredential.afterEdit],
 * which names its class alone. The text is read as Compose's own paste reads it (the first item's text), so what is
 * compared is what was pasted; whether there is text is asked of the clip's description as Compose asks it (any text MIME type).
 */
class AndroidClip(private val context: Context) : ClipPort {
    override fun describesText(): Boolean = manager().primaryClipDescription?.hasMimeType("text/*") == true

    override fun text(): String? = manager().primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0).text?.toString() else null }

    override fun clear() = manager().clearPrimaryClip()

    private fun manager(): ClipboardManager = checkNotNull(context.getSystemService(ClipboardManager::class.java))
}

/**
 * A secret field's Clipboard (D2 (a) 2, the D2 review's F1): what Compose's own paste reads is told to the field's
 * [SecretEdits] as it is read. In foundation 1.11.4 the field (`BasicTextField(String)`, the legacy CoreTextField) takes
 * `LocalClipboard.current`, and every paste into it — the context menu's or toolbar's Paste, Ctrl+V or a keyboard's
 * paste key, accessibility's paste — is `TextFieldSelectionManager.paste()`: `getClipEntry()`, the first item's text,
 * and the edit made at once, in the same call. Nothing else in that field reads the clip (whether Paste is offered comes
 * from the clip's description). So the text told here, taken as Compose takes it, is what that paste put in. Copy and
 * cut, which a password field does not offer, go to the real clipboard unchanged.
 */
class PasteWatch(private val clipboard: Clipboard, private val edits: SecretEdits) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = clipboard.getClipEntry().also { edits.pasteRead(it?.clipData?.let { c -> if (c.itemCount > 0) c.getItemAt(0).text?.toString() else null }) }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) = clipboard.setClipEntry(clipEntry)

    override val nativeClipboard: ClipboardManager get() = clipboard.nativeClipboard
}

/**
 * A secret field's edit (D2 (a) 2), weighed by its [SecretEdits] — against its text just before the edit and what
 * Compose's paste read for it — and [PastedCredential.afterEdit] against the real clipboard. The line says whether the
 * clip was cleared and why not — never with what, never how long.
 */
fun afterSecretEdit(tag: String, edits: SecretEdits, after: String, clip: ClipPort) {
    PastedCredential.line(edits.edited(after, clip))?.let { Diagnostics.add(tag, it) }
}
