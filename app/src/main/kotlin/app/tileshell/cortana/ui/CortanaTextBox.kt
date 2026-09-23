package app.tileshell.cortana.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.ui.tokens.ShellType

/**
 * Cortana's text box (R6 §3.3) and the listening query box (R6 §3.1.11) — phase 03 build task 9.
 *
 * W10M's Cortana had a real text box, so typed requests run the SAME matcher and reply path as speech
 * (fidelity A4). There is deliberately no other way to put text in: no debug intent, broadcast or
 * provider verb takes a request, which E5 proves from the exported-components list.
 */
object TextBoxValues {
    /** R6 §3.3.1 (HIGH): 48 ± 1.1 epx tall, full width, docked directly on the drawn nav bar. */
    const val HEIGHT_EPX = 48f

    /** R6 §3.3.3 (HIGH): fill (46–48, 46–48, 46–48) as captured. */
    val FILL = Color(0xFF2F2F2F)

    /** R6 §3.3.4 (HIGH): an accent-filled mic button 48 × 48 epx flush right, white mic glyph centred. */
    const val MIC_BUTTON_EPX = 48f

    /** R6 §3.3.6 (HIGH): placeholder in the 15-epx class, light grey, 12 ± 1 epx from the left. */
    const val TEXT_LEFT_EPX = 12f
    val PLACEHOLDER_COLOUR = Color(0xFF9E9E9E)

    /**
     * R6 §3.3.5 (MEDIUM): two screen recordings show "Ask me anything"; one 15063.251 camera capture
     * shows "Type here to search" with no visible cause. The recordings win; H13 judges it.
     */
    const val PLACEHOLDER = "Ask me anything"

    /** R6 §3.1.11: the listening box is taller, white, with a 2-epx accent border. */
    const val LISTENING_HEIGHT_EPX = 52f
    const val LISTENING_BORDER_EPX = 2f
    const val LISTENING_TEXT_LEFT_EPX = 15.7f
    const val LISTENING_SUBMIT_WIDTH_EPX = 16f
    const val LISTENING_SUBMIT_RIGHT_EPX = 16.6f
    const val LISTENING_PLACEHOLDER = "Listening..."

    /** R6 §3.3.9 (MEDIUM): the voice-hint callout above the mic button. */
    const val CALLOUT_HEIGHT_EPX = 26.8f
    const val CALLOUT_RIGHT_EPX = 11f
    const val CALLOUT_TAIL_HEIGHT_EPX = 8.6f
    const val CALLOUT_TAIL_BASE_EPX = 17f
    const val CALLOUT_TAIL_GAP_EPX = 4f

    /** R6 §3.1.14 (MEDIUM, 15063): the page background is black. 14393 was dark grey. */
    val PAGE_BACKGROUND = Color(0xFF000000)
}

/**
 * Which form the bar takes (R6 §3.3.7).
 *
 * R6 §3.3.7 (a) measured a third form — after a send the grey bar kept the query with a "✕" — and it is
 * deliberately NOT built: Jeremy, 2026-09-22, "It should auto clear when it gets auto sent". A sent
 * request, typed or spoken, returns the bar to the empty box at once; the answer card stays on the page.
 */
enum class TextBoxMode {
    /** The placeholder, with the accent mic button. Also the form right after a send. */
    IDLE,

    /** A confirm card waiting for a spoken yes / no: the bar is empty, grey mic glyph, no accent fill. */
    AWAITING_ANSWER,
}

@Composable
fun CortanaTextBox(
    mode: TextBoxMode,
    accent: Color,
    onSubmit: (String) -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        modifier
            .fillMaxWidth()
            .height(TextBoxValues.HEIGHT_EPX.dp)
            .background(TextBoxValues.FILL)
            .testTag("cortana_text_box"),
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).fillMaxHeight().padding(start = TextBoxValues.TEXT_LEFT_EPX.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (mode) {
                    TextBoxMode.AWAITING_ANSWER -> Unit
                    TextBoxMode.IDLE -> BasicTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        textStyle = ShellType.body.copy(color = Color.White),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val text = typed
                            typed = ""
                            // The field stays on screen after a send, so the keyboard is put away here
                            // or it would cover the answer card.
                            keyboard?.hide()
                            focus.clearFocus()
                            onSubmit(text)
                        }),
                        modifier = Modifier.fillMaxWidth().testTag("cortana_text_box_field"),
                        decorationBox = { inner ->
                            if (typed.isEmpty()) {
                                BasicText(
                                    TextBoxValues.PLACEHOLDER,
                                    style = ShellType.body.copy(color = TextBoxValues.PLACEHOLDER_COLOUR),
                                    modifier = Modifier.testTag("cortana_text_box_placeholder"),
                                )
                            }
                            inner()
                        },
                    )
                }
            }
            when (mode) {
                TextBoxMode.AWAITING_ANSWER -> Box(
                    Modifier.size(TextBoxValues.MIC_BUTTON_EPX.dp).testTag("cortana_text_box_mic_idle"),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(Glyph.MIC, style = iconStyle(Color(0xFF9E9E9E), 20))
                }
                TextBoxMode.IDLE -> Box(
                    Modifier.size(TextBoxValues.MIC_BUTTON_EPX.dp).background(accent)
                        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onMic)
                        .testTag("cortana_text_box_mic"),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(Glyph.MIC_FILLED, style = iconStyle(Color.White, 20))
                }
            }
        }
    }
}

/**
 * R6 §3.1.11: while listening, the grey bar is replaced by a white box with a 2-epx accent border,
 * 52 epx tall, its bottom edge on the nav bar. The placeholder is in the accent and the recognised text
 * is dark; the waveform glyph sits right after the last recognised word (§3.1.1: absent while the box
 * still shows the placeholder).
 */
@Composable
fun ListeningQueryBox(
    text: String,
    level: Float,
    accent: Color,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TextBoxValues.LISTENING_HEIGHT_EPX.dp)
            .background(Color.White)
            .border(TextBoxValues.LISTENING_BORDER_EPX.dp, accent)
            .testTag("cortana_listening_box"),
    ) {
        Row(
            Modifier.fillMaxWidth().fillMaxHeight().padding(start = TextBoxValues.LISTENING_TEXT_LEFT_EPX.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (text.isBlank()) {
                BasicText(
                    TextBoxValues.LISTENING_PLACEHOLDER,
                    style = ShellType.body.copy(color = accent),
                    modifier = Modifier.testTag("cortana_listening_placeholder"),
                )
            } else {
                BasicText(
                    text,
                    style = ShellType.body.copy(color = Color(0xFF1A1A1A)),
                    modifier = Modifier.testTag("cortana_listening_text"),
                )
                Spacer(Modifier.width(PersonaValues.WAVE_AFTER_TEXT_EPX.dp))
                ListeningWaveform(level, Modifier.padding(bottom = PersonaValues.WAVE_CENTRE_ABOVE_X_HEIGHT_EPX.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .padding(end = TextBoxValues.LISTENING_SUBMIT_RIGHT_EPX.dp)
                    .width(TextBoxValues.LISTENING_SUBMIT_WIDTH_EPX.dp)
                    .fillMaxHeight()
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onSubmit)
                    .testTag("cortana_listening_submit"),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(Glyph.ARROW_RIGHT, style = iconStyle(Color(0xFF9E9E9E), 16))
            }
        }
    }
}

/**
 * R6 §3.3.9: an accent rectangle 26.8 epx tall with white 15-epx text, its right edge 11 epx from the
 * screen's right and its width following the text, with a downward tail 8.6 epx tall on a 17-epx base
 * whose tip sits 4 epx above the bar, over the mic glyph.
 */
@Composable
fun VoiceHintCallout(text: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(end = TextBoxValues.CALLOUT_RIGHT_EPX.dp)
            .testTag("cortana_voice_hint"),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            Modifier
                .padding(bottom = (TextBoxValues.CALLOUT_TAIL_HEIGHT_EPX + TextBoxValues.CALLOUT_TAIL_GAP_EPX).dp)
                .height(TextBoxValues.CALLOUT_HEIGHT_EPX.dp)
                .background(accent)
                .padding(horizontal = 8.dp)
                .drawBehind {
                    // The tail hangs from the rectangle's bottom edge, its base centred over the mic button.
                    val tailHeight = TextBoxValues.CALLOUT_TAIL_HEIGHT_EPX.dp.toPx()
                    val tailBase = TextBoxValues.CALLOUT_TAIL_BASE_EPX.dp.toPx()
                    val right = size.width + 8.dp.toPx()
                    val path = Path().apply {
                        moveTo(right - tailBase, size.height)
                        lineTo(right, size.height)
                        lineTo(right - tailBase / 2f, size.height + tailHeight)
                        close()
                    }
                    drawPath(path, accent)
                    @Suppress("UNUSED_EXPRESSION") Offset.Zero
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text, style = ShellType.body.copy(color = Color.White))
        }
    }
}

internal fun iconStyle(color: Color, sizeEpx: Int) =
    TextStyle(fontFamily = Brand.iconFont, fontSize = sizeEpx.sp, color = color)
