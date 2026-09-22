package app.tileshell.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The field rules the edge cases name (imeOptions actions, multi-line, textNoSuggestions, caps flags). */
class FieldInfoTest {

    private fun f(type: Int, options: Int = EditorInfo.IME_ACTION_UNSPECIFIED) = FieldInfo.from(type, options)
    private val text = InputType.TYPE_CLASS_TEXT

    @Test fun `field kinds come from the class and variation`() {
        assertEquals(FieldKind.TEXT, f(text).kind)
        assertEquals(FieldKind.PASSWORD, f(text or InputType.TYPE_TEXT_VARIATION_PASSWORD).kind)
        assertEquals(FieldKind.PASSWORD, f(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD).kind)
        assertEquals(FieldKind.PASSWORD, f(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD).kind)
        assertEquals(FieldKind.PASSWORD, f(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD).kind)
        assertEquals(FieldKind.URL, f(text or InputType.TYPE_TEXT_VARIATION_URI).kind)
        assertEquals(FieldKind.EMAIL, f(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).kind)
        assertEquals(FieldKind.PHONE, f(InputType.TYPE_CLASS_PHONE).kind)
        assertEquals(FieldKind.NUMBER, f(InputType.TYPE_CLASS_NUMBER).kind)
        assertEquals(FieldKind.NUMBER, f(InputType.TYPE_CLASS_DATETIME).kind)
        assertEquals(FieldKind.SEARCH, f(text, EditorInfo.IME_ACTION_SEARCH).kind)
    }

    @Test fun `each imeOptions action maps to the Enter key's action`() {
        assertEquals(FieldAction.GO, f(text, EditorInfo.IME_ACTION_GO).enterAction)
        assertEquals(FieldAction.SEARCH, f(text, EditorInfo.IME_ACTION_SEARCH).enterAction)
        assertEquals(FieldAction.SEND, f(text, EditorInfo.IME_ACTION_SEND).enterAction)
        assertEquals(FieldAction.NEXT, f(text, EditorInfo.IME_ACTION_NEXT).enterAction)
        assertEquals(FieldAction.DONE, f(text, EditorInfo.IME_ACTION_DONE).enterAction)
        assertEquals(FieldAction.NONE, f(text).enterAction)
    }

    @Test fun `IME_FLAG_NO_ENTER_ACTION means Enter is a newline, not the action`() {
        val multi = text or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val field = f(multi, EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        assertEquals(FieldAction.NONE, field.enterAction)
        assertTrue(field.enterInsertsNewline)
        assertTrue(f(multi).enterInsertsNewline)
    }

    @Test fun `suggestions are off in password, textNoSuggestions and digit fields, on in URL and email`() {
        assertFalse(f(text or InputType.TYPE_TEXT_VARIATION_PASSWORD).suggestionsOn)
        assertFalse(f(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).suggestionsOn)
        assertFalse(f(InputType.TYPE_CLASS_PHONE).suggestionsOn)
        assertFalse(f(InputType.TYPE_CLASS_NUMBER).suggestionsOn)
        assertTrue(f(text or InputType.TYPE_TEXT_VARIATION_URI).suggestionsOn)
        assertTrue(f(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).suggestionsOn)
        assertTrue(f(text).suggestionsOn)
    }

    @Test fun `password fields never learn and never swipe`() {
        val pw = f(text or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertTrue(pw.isPassword)
        assertFalse(pw.learningOn)
        assertFalse(pw.swipeOn)
        assertEquals(app.tileshell.ime.engine.FieldKind.PASSWORD, EngineBrain.kindOf(pw))
    }

    @Test fun `the capitalisation flags are read`() {
        assertTrue(f(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).capSentences)
        assertTrue(f(text or InputType.TYPE_TEXT_FLAG_CAP_WORDS).capWords)
        assertTrue(f(text or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS).capCharacters)
        assertFalse(f(text).capSentences)
    }

    @Test fun `phone fields open on the keypad and number fields on the digits page`() {
        assertEquals(Layer.PHONE, Layouts.initialLayer(f(InputType.TYPE_CLASS_PHONE)))
        assertEquals(Layer.SYMBOLS_1, Layouts.initialLayer(f(InputType.TYPE_CLASS_NUMBER)))
        assertEquals(Layer.LETTERS, Layouts.initialLayer(f(text)))
    }
}
