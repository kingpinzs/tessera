package app.tileshell.net

import java.util.WeakHashMap

/*
 * Phase 17, the owner's ruling D2 (a) (2026-10-31; the trust triage's B-11 and B2-L10): the two pages that take a
 * credential — Movies & TV's TMDB key page and the media-server sign-in form, which Music shows too — keep it off the
 * screen's copies and off the clipboard. TRUST-TOUCHING. The rules are here, free of Android types and unit-tested
 * (`SecureWindowsTest`, `PastedCredentialTest`); `AndroidCredentialScreen.kt` is the thin Android side, one platform
 * call per step, held to its form by `CredentialScreenWiringScanTest`.
 */

/** What a window's FLAG_SECURE is to do now. */
enum class SecureStep { SET, CLEAR, KEEP }

/**
 * No screenshot, screen recording or Recents preview while a credential page shows (D2 (a) 1): the number of credential
 * pages showing in each window. The flag follows the COUNT, never a toggle — set when a page appears, cleared only when
 * the last one showing in that window leaves — so a page leaving while another credential page is still up keeps the
 * flag, and once the last has left nothing of it stays on the window. A leave with nothing counted clears (the count
 * is zero, so the flag must be off) and leaves no debt for the next page.
 *
 * A window is a key compared by identity (an `android.view.Window` does not override equals) and held weakly: a window
 * that is gone takes its count with it. One instance per process; called from composition's effects, the main thread.
 */
class SecureWindows<W : Any> {
    private val showing = WeakHashMap<W, Int>()

    /** A credential page appeared in [window]: SET, every time — setting is idempotent. */
    fun shown(window: W): SecureStep {
        showing[window] = showing(window) + 1
        return SecureStep.SET
    }

    /** A credential page left [window]: KEEP while another is still showing there, else CLEAR. */
    fun left(window: W): SecureStep {
        val now = showing(window) - 1
        if (now > 0) {
            showing[window] = now
            return SecureStep.KEEP
        }
        showing.remove(window)
        return SecureStep.CLEAR
    }

    fun showing(window: W): Int = showing[window] ?: 0

    /**
     * How many windows the count holds: only those with a credential page showing. A window's entry goes when its last
     * page leaves, and a window that is gone with a page still counted is not held (the D2 review's R6).
     */
    val tracked: Int get() = showing.size

    companion object {
        /** The step's line: what the window now does, never which page or how many. */
        fun line(step: SecureStep): String = when (step) {
            SecureStep.SET -> "credential page shown: screen capture blocked"
            SecureStep.CLEAR -> "credential page left: screen capture allowed again"
            SecureStep.KEEP -> "credential page left: screen capture still blocked, another credential page is showing"
        }
    }
}

/**
 * The clipboard as the paste rule needs it. The real one is `AndroidClip`, the tests' a fake. Each method is one platform
 * call and may throw; [PastedCredential.afterEdit] catches and names the class.
 */
interface ClipPort {
    /**
     * Whether the primary clip holds text, from its DESCRIPTION alone (`getPrimaryClipDescription()`, as Compose itself
     * asks before it offers Paste): the clip's content is not read, and AOSP's ClipboardService neither notes that call
     * nor answers it with the "pasted from your clipboard" notice — only `getPrimaryClip()` does, once per clip per app.
     */
    fun describesText(): Boolean

    /** The text a paste puts in: the primary clip's first item's text, as Compose's own paste reads it — or null. */
    fun text(): String?

    /** Empties the primary clip. */
    fun clear()
}

/** What a secret field's edit did to the clipboard. */
sealed interface ClipOutcome {
    /**
     * The clipboard is not read: the edit put in fewer than [PastedCredential.MIN_PASTE] characters at once (typing,
     * deleting) and was not Compose's paste, or the clipboard's description says it holds no text.
     */
    data object NotRead : ClipOutcome

    /** Read, and the edit is not the clipboard's text put in: left as it is. */
    data object NotAPaste : ClipOutcome

    data object Cleared : ClipOutcome

    /** A paste, but by the moment of clearing the clipboard held something else: a newer copy is never wiped. */
    data object Newer : ClipOutcome

    /** A read or the clear threw: nothing cleared; [exception] is the thrown class's simple name and nothing else of it. */
    data class Failed(val exception: String) : ClipOutcome
}

/**
 * The clipboard after a paste into a credential field (D2 (a) 2): the shell clears the primary clip when the field's new
 * text is its old text with the clipboard's text put in (in place of a selection, or of nothing) — and only if, at the
 * moment of clearing, the clipboard still holds exactly what was pasted.
 *
 * A paste reaches the field two ways, and each is weighed its own way (the D2 review's F1, F2):
 *  - COMPOSE'S OWN PASTE — the context menu's or toolbar's Paste, Ctrl+V or the keyboard's paste key, accessibility's
 *    paste. Compose reads the clipboard for it through the field's Clipboard, which tells the field what it read
 *    ([SecretEdits.pasteRead]); the edit that puts that text in is the paste, however little of the old text it changes
 *    (a selection pasted over with text one character longer). Nothing is guessed and the clipboard is read once more,
 *    only to check that it still holds what was pasted. Left as they were: a clip of one character (taken for typing,
 *    as on the keyboard's path) and a paste over the very same text (Compose's field makes no edit for it).
 *  - A KEYBOARD'S COMMIT — its clipboard chip commits the text through the input method, and Compose never reads the
 *    clipboard for it, so only the text can tell. That path reads the clipboard as little as it can: never for an edit
 *    that puts in fewer than [MIN_PASTE] characters at once (typing, deleting — no read, so no notice either), and,
 *    for the rest, only when the clip's description, which is no read, says it holds text. THE TRADE-OFF: a keyboard's
 *    commit of two or more characters, or an autofill, while the clipboard holds text costs one read of it; on
 *    Android 12 and later that read may raise the system's "pasted from your clipboard" notice, once for that clip,
 *    for an edit that was not a paste. No call tells such an edit from a chip's paste without reading the clip.
 *
 * The field's RAW edit is what is weighed (before its single-line filter drops a line feed), so a token copied with its
 * line break is still seen as the paste it is.
 *
 * Nothing about the pasted value is ever written: [line] says cleared or not and why — no content, no length, and a
 * failure by its exception's class alone (B-1: a pasted value must never reach a log through an exception's message).
 */
object PastedCredential {
    /** The fewest characters put in at once that may be a paste: one at a time is typing. */
    const val MIN_PASTE = 2

    /** Whether the edit from [before] to [after] put in at least [MIN_PASTE] characters at once — the only edits the clipboard is read for. */
    fun couldBePaste(before: String, after: String): Boolean {
        val shorter = minOf(before.length, after.length)
        var head = 0
        while (head < shorter && before[head] == after[head]) head++
        var tail = 0
        while (tail < shorter - head && before[before.length - 1 - tail] == after[after.length - 1 - tail]) tail++
        return after.length - head - tail >= MIN_PASTE
    }

    /** Whether [after] is [before] with [clip] put in place of one stretch of it (empty or not): a paste of [clip]. */
    fun isPaste(before: String, after: String, clip: String?): Boolean {
        if (clip == null || clip.length < MIN_PASTE) return false
        val kept = after.length - clip.length
        if (kept < 0 || kept > before.length) return false
        for (head in 0..kept) {
            val tail = kept - head
            if (after.regionMatches(head, clip, 0, clip.length) &&
                after.regionMatches(0, before, 0, head) &&
                after.regionMatches(head + clip.length, before, before.length - tail, tail)
            ) return true
        }
        return false
    }

    /** Clear only what was pasted: the clipboard [now], at the moment of clearing, still holds exactly [pasted]. */
    fun mayClear(pasted: String, now: String?): Boolean = now == pasted

    /**
     * A secret field's edit from [before] to [after]: [pasteRead] is what Compose's own paste read off the clipboard just
     * before it, if it did. That paste, put in by this edit, is weighed as it is; any other edit is a keyboard's, read
     * against the clipboard only when it could be a paste and the clipboard holds text. Cleared only when the edit was a
     * paste and the clipboard has not changed since. Never throws — the field's edit goes on whatever the clipboard does.
     */
    fun afterEdit(before: String, after: String, clip: ClipPort, pasteRead: String? = null): ClipOutcome {
        val composePaste = pasteRead != null && isPaste(before, after, pasteRead)
        if (!composePaste && !couldBePaste(before, after)) return ClipOutcome.NotRead
        return try {
            val pasted = when {
                composePaste -> pasteRead
                clip.describesText() -> clip.text()
                else -> return ClipOutcome.NotRead
            }
            when {
                pasted == null || !isPaste(before, after, pasted) -> ClipOutcome.NotAPaste
                !mayClear(pasted, clip.text()) -> ClipOutcome.Newer
                else -> {
                    clip.clear()
                    ClipOutcome.Cleared
                }
            }
        } catch (e: Throwable) {
            ClipOutcome.Failed(e.javaClass.simpleName)
        }
    }

    /** The outcome's line, or null when the clipboard was not read (typing — nothing is written per keystroke). */
    fun line(outcome: ClipOutcome): String? = when (outcome) {
        ClipOutcome.NotRead -> null
        ClipOutcome.NotAPaste -> "credential field: not the clipboard's text, clipboard left as it is"
        ClipOutcome.Cleared -> "credential pasted: clipboard cleared"
        ClipOutcome.Newer -> "credential pasted: clipboard not cleared, it holds a newer copy"
        is ClipOutcome.Failed -> "credential field: clipboard not cleared (${outcome.exception})"
    }
}

/**
 * One secret field's memory between its edits (D2 (a) 2), held by the field (`remember`), on the main thread.
 *
 * The text an edit is weighed against is the field's text just BEFORE that edit (the D2 review's N2): the last raw edit
 * — two edits can land before the page shows the first, and the page's value is the older one until it recomposes — or
 * the value the page set since (its single-line filter, a reset). Where the page's filter turns an edit back into the
 * value it already had (a lone line feed, a cut at the field's length), the raw edit stays the base for the next edit
 * alone, until the page's value changes again.
 *
 * And what Compose's own paste read off the clipboard (the review's F1), told as it is read by the field's Clipboard;
 * the edit Compose then makes takes it, so it is weighed for that one edit only.
 */
class SecretEdits(value: String) {
    private var page = value
    private var before = value
    private var read: String? = null

    /** The page's value, as each composition gives it: a value the page changed is the field's text from now on. */
    fun shown(value: String) {
        if (value == page) return
        page = value
        before = value
    }

    /** Compose's own paste read [text] off the clipboard (the first item's text, or null): its edit comes next. */
    fun pasteRead(text: String?) {
        read = text
    }

    /** The field's raw edit to [after], weighed against its text just before it and what Compose's paste read for it. */
    fun edited(after: String, clip: ClipPort): ClipOutcome {
        val was = before
        val pasted = read
        before = after
        read = null
        return PastedCredential.afterEdit(was, after, clip, pasted)
    }
}
