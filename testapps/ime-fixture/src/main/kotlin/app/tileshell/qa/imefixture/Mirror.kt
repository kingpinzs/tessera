package app.tileshell.qa.imefixture

import android.app.Activity
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView

/**
 * Mirrors the focused [MirrorEditText] into the six mirror TextViews of the mirror block, so that a
 * `uiautomator dump` (which has no selection attributes and masks password text) can read the raw
 * text, selection, length, inputType and last editor action of any field.
 *
 * Refreshes on every text change (TextWatcher), every caret move (MirrorEditText.onSelectionChanged),
 * every focus change (global focus listener on the root) and every editor action.
 */
class Mirror(private val activity: Activity, private val root: ViewGroup) {

    private val focusView: TextView = root.findViewById(R.id.mirror_focus)
    private val textView: TextView = root.findViewById(R.id.mirror_text)
    private val selView: TextView = root.findViewById(R.id.mirror_sel)
    private val lenView: TextView = root.findViewById(R.id.mirror_len)
    private val actionView: TextView = root.findViewById(R.id.mirror_action)
    private val inputTypeView: TextView = root.findViewById(R.id.mirror_inputtype)

    private var lastAction = "none"

    fun attach() {
        for (field in fields(root)) {
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (field.isFocused) refresh()
                }
            })
            field.onSelectionChangedListener = { _, _ -> if (field.isFocused) refresh() }
            field.setOnEditorActionListener { v, actionId, _ ->
                lastAction = "${actionName(actionId)}@${idName(v)}"
                refresh()
                // false: the default handling (Next moves focus, Done hides the keyboard) still runs,
                // as it would in any app.
                false
            }
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, _ -> refresh() }
        refresh()
    }

    /** Focuses the field named by its id (e.g. `field_email`); returns it, or null if there is none. */
    fun focusField(name: String): MirrorEditText? {
        val id = activity.resources.getIdentifier(name, "id", activity.packageName)
        if (id == 0) return null
        val field = activity.findViewById<View>(id) as? MirrorEditText ?: return null
        field.requestFocus()
        refresh()
        return field
    }

    /** Asks the IME to show for [field]; call once the window has focus or the IMM ignores it. */
    fun showKeyboard(field: View) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(field, 0)
    }

    fun refresh() {
        val focused = activity.currentFocus as? MirrorEditText
        if (focused == null) {
            focusView.text = "none"
            textView.text = "[]"
            selView.text = "0,0"
            lenView.text = "0"
            inputTypeView.text = "0x0"
        } else {
            val raw = focused.text?.toString() ?: ""
            focusView.text = idName(focused)
            textView.text = "[" + raw.replace("\n", "\\n") + "]"
            selView.text = "${focused.selectionStart},${focused.selectionEnd}"
            lenView.text = raw.length.toString()
            inputTypeView.text = "0x" + Integer.toHexString(focused.inputType)
        }
        actionView.text = lastAction
    }

    private fun idName(v: View): String =
        if (v.id == View.NO_ID) "noid" else v.resources.getResourceEntryName(v.id)

    private fun fields(group: ViewGroup): List<MirrorEditText> {
        val out = ArrayList<MirrorEditText>()
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is MirrorEditText -> out.add(child)
                is ViewGroup -> out.addAll(fields(child))
            }
        }
        return out
    }

    companion object {
        fun actionName(actionId: Int): String = when (actionId) {
            EditorInfo.IME_ACTION_UNSPECIFIED -> "IME_ACTION_UNSPECIFIED"
            EditorInfo.IME_ACTION_NONE -> "IME_ACTION_NONE"
            EditorInfo.IME_ACTION_GO -> "IME_ACTION_GO"
            EditorInfo.IME_ACTION_SEARCH -> "IME_ACTION_SEARCH"
            EditorInfo.IME_ACTION_SEND -> "IME_ACTION_SEND"
            EditorInfo.IME_ACTION_NEXT -> "IME_ACTION_NEXT"
            EditorInfo.IME_ACTION_DONE -> "IME_ACTION_DONE"
            EditorInfo.IME_ACTION_PREVIOUS -> "IME_ACTION_PREVIOUS"
            else -> "IME_ACTION_$actionId"
        }
    }
}
