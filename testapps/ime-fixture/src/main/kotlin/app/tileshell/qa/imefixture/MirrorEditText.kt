package app.tileshell.qa.imefixture

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText

/**
 * An EditText that reports caret moves. TextWatcher fires on text changes only; the cursor-control
 * dot (E6) moves the caret without changing the text, and only [onSelectionChanged] sees that.
 */
class MirrorEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : EditText(context, attrs) {

    /** Called with (selectionStart, selectionEnd) after every caret or selection move. */
    var onSelectionChangedListener: ((Int, Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        // The framework calls this from the superclass constructor, before this class's fields exist;
        // the listener reads as null then, which is the right answer.
        onSelectionChangedListener?.invoke(selStart, selEnd)
    }
}
