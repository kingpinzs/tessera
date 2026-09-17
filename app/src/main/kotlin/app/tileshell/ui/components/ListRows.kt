package app.tileshell.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

/** X19 approximation: list rows lighten while pressed with a 15 % white overlay (R3 C5 form). */
const val ROW_PRESS_ALPHA = 0.15f

@Composable
fun PressRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .pointerInput(onClick) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    val up = waitForUpOrCancellation()
                    pressed = false
                    if (up != null) onClick()
                }
            }
            .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent),
        content = content,
    )
}
