package app.tileshell.ime

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import app.tileshell.diag.Diagnostics
import java.text.BreakIterator

/**
 * Every edit the keyboard makes to the app's field goes through here, so the field is touched in one
 * place and every edit leaves a line in the diagnostics ring (the QA rows read those lines to tell the
 * keyboard's own edits from the app's).
 *
 * [pending] holds the caret positions the keyboard's own edits should produce, oldest first. Selection
 * updates arrive asynchronously, so two quick keystrokes can both be in flight before the first update
 * lands: an update matching ANY pending position is the keyboard's own. One that lands anywhere else was
 * the user (a tap in the text, a paste) or the app, which is what turns the strip from "suggestions for
 * the word being typed" into "alternatives for the word you tapped" (R6 §2.2.7).
 */
class Editor(private val ic: () -> InputConnection?) {

    var selStart = -1
        private set
    var selEnd = -1
        private set

    private val pending = ArrayDeque<Int>()

    /**
     * True while the field is a password field: every line this class writes to the diagnostics ring then
     * says how MANY characters went in, never which. The ring is readable through `dumpsys`, and a
     * keyguard password typed with this keyboard was sitting in it in plain text.
     */
    var secret = false

    /** When the last space the keyboard typed went in (for the double-space period). */
    var lastSpaceAt = 0L
        private set

    fun reset(info: EditorInfo?) {
        selStart = info?.initialSelStart ?: -1
        selEnd = info?.initialSelEnd ?: -1
        pending.clear()
        // Android re-reports the unchanged selection once after a (re)start; that report is not the user
        // moving the caret (review MINOR-1).
        if (selStart >= 0 && selStart == selEnd) pending.addLast(selStart)
        lastSpaceAt = 0L
    }

    /** @return true when the update is one of the keyboard's own edits landing where it was expected. */
    fun selectionChanged(newStart: Int, newEnd: Int): Boolean {
        val idx = if (newStart == newEnd) pending.indexOf(newStart) else -1
        if (idx < 0) {
            pending.clear()
            selStart = newStart
            selEnd = newEnd
            return false
        }
        repeat(idx + 1) { pending.removeFirst() }
        // Edits still in flight put the caret further on than this update says; keep the newest.
        val caret = pending.lastOrNull() ?: newStart
        selStart = caret
        selEnd = caret
        return true
    }

    /** Record where an edit leaves the caret, optimistically, so the next edit counts from there. */
    private fun expect(caret: Int) {
        if (caret < 0) { pending.clear(); return }
        pending.addLast(caret)
        selStart = caret
        selEnd = caret
    }

    val hasSelection: Boolean get() = selStart >= 0 && selEnd > selStart

    fun before(n: Int = 64): String = ic()?.getTextBeforeCursor(n, 0)?.toString().orEmpty()
    fun after(n: Int = 64): String = ic()?.getTextAfterCursor(n, 0)?.toString().orEmpty()

    fun commit(text: String, why: String) {
        val c = ic() ?: return
        c.commitText(text, 1)
        // Committing replaces the selection when there is one, so either way the caret lands at the
        // selection's start plus what was typed.
        expect(if (selStart >= 0) selStart + text.length else -1)
        if (text == " ") lastSpaceAt = SystemClock.uptimeMillis()
        // In a password field the reason is withheld too: "key t" would name the letter.
        Diagnostics.add("ime", "commit ${quote(text)} (${if (secret) "password field" else why})")
    }

    /** Replace the [before] chars left of the caret and the [after] chars right of it with [text]. */
    fun replaceAround(before: Int, after: Int, text: String, why: String) {
        val c = ic() ?: return
        c.beginBatchEdit()
        c.deleteSurroundingText(before, after)
        c.commitText(text, 1)
        c.endBatchEdit()
        expect(if (selStart >= 0) selStart - before + text.length else -1)
        Diagnostics.add("ime", "replace -$before/+$after -> ${quote(text)} ($why)")
    }

    /** One backspace: the selection, else one whole grapheme (an emoji sequence is one grapheme). */
    fun backspace() {
        val c = ic() ?: return
        if (hasSelection) {
            c.commitText("", 1)
            expect(selStart)
            Diagnostics.add("ime", "backspace (selection)")
            return
        }
        val text = before(16)
        if (text.isEmpty()) {
            // An empty field, or an app that does not answer getTextBeforeCursor: a real DEL key event
            // is what every editor understands.
            sendKey(KeyEvent.KEYCODE_DEL)
            Diagnostics.add("ime", "backspace (key event)")
            return
        }
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val last = it.last()
        val prev = it.previous().coerceAtLeast(0)
        val n = last - prev
        c.deleteSurroundingText(n, 0)
        expect(if (selStart >= 0) selStart - n else -1)
        Diagnostics.add("ime", "backspace $n char(s)")
    }

    fun sendKey(code: Int) {
        val c = ic() ?: return
        val now = SystemClock.uptimeMillis()
        c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        expect(-1)
    }

    fun performAction(actionId: Int) {
        ic()?.performEditorAction(actionId)
        expect(-1)
        Diagnostics.add("ime", "editor action $actionId")
    }

    fun capsMode(inputType: Int): Boolean = (ic()?.getCursorCapsMode(inputType) ?: 0) != 0

    private fun quote(s: String) = if (secret) "(${s.length} hidden)" else "\"" + s.replace("\n", "\\n") + "\""
}
