package app.tileshell.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tileshell.ui.tokens.ShellType

/**
 * The W10M editor text box as People's editor has it (r11/people.md P4.4, 10586, HIGH on two devices): 32 epx outer —
 * 34 where an edit button sits beside it — with a 2-epx (133,133,133) border and a black fill. Phase 16's People editor
 * uses it as measured, and its Calendar editor uses the same form (r11/calendar.md U3 proposes it; no Calendar editor
 * capture exists on any build — an approximation there, H15). One component, so the two editors cannot drift apart.
 *
 * The caller lays it out (x 12 → W − 12 in both editors) and draws the label above it (cap top → box top 22.75 epx, P4.5).
 */
object OutlinedFieldMetrics {
    val HEIGHT: Dp = 32.dp
    val HEIGHT_WITH_BUTTON: Dp = 34.dp
    val BORDER: Dp = 2.dp
    val BORDER_COLOR = Color(133, 133, 133)
    val FILL = Color.Black

    /** The text's inset from the box's outer edge: the border plus W10M's TextBox padding (approximation; R11 measured the box, not its text). */
    val TEXT_INSET: Dp = 10.dp
}

/**
 * @param tag the test tag on the field itself, the node that carries the typed text
 * @param maxLength characters kept; a newline is never kept in a single-line field
 * @param placeholder shown in grey while the field is empty
 */
@Composable
fun OutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    height: Dp = OutlinedFieldMetrics.HEIGHT,
    maxLength: Int = 500,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    textStyle: TextStyle = ShellType.body.copy(color = Color.White),
) {
    Box(
        modifier
            .height(height)
            .background(OutlinedFieldMetrics.FILL)
            .border(OutlinedFieldMetrics.BORDER, OutlinedFieldMetrics.BORDER_COLOR),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty() && placeholder != null) {
            BasicText(
                placeholder,
                Modifier.padding(horizontal = OutlinedFieldMetrics.TEXT_INSET),
                style = textStyle.copy(color = OutlinedFieldMetrics.BORDER_COLOR),
                maxLines = 1,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { typed ->
                val kept = if (singleLine) typed.replace("\n", "") else typed
                onValueChange(kept.take(maxLength))
            },
            modifier = Modifier.fillMaxSize().padding(horizontal = OutlinedFieldMetrics.TEXT_INSET).testTag(tag),
            enabled = enabled,
            textStyle = textStyle,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() } },
        )
    }
}
