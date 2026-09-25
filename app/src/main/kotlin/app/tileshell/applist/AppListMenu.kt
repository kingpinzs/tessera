package app.tileshell.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.start.Edit
import app.tileshell.ui.fluent.AcrylicLayer
import app.tileshell.ui.fluent.FluentSurface
import app.tileshell.ui.fluent.Rgb
import app.tileshell.ui.fluent.revealLights
import androidx.compose.ui.draw.drawWithContent
import android.os.SystemClock
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import app.tileshell.ui.tokens.ShellType
import kotlin.math.roundToInt

/**
 * The app list's long-press context menu (phase 02 build task 3, approximation H21):
 * "a W10M-style context menu drawn over the list, a full-width band in the theme background with its items in
 * the 15-epx body class at 12-epx insets, holding 'Pin to Start'" (phase 02 Decisions 2026-09-16).
 */
object AppMenuMetrics {
    /** H21: 12-epx insets around each item's 15-epx body text. */
    val INSET = 12.dp
}

/**
 * A list row that both launches on a tap and opens the context menu on a hold.
 *
 * The hold threshold is [Edit.HOLD_MS] (783 ms, R6 §1.1.1) — the same one the tile grid uses to enter edit
 * mode, so one hold means one thing everywhere in the shell (agent call; R6 measured no app-list hold).
 * Like the tile grid's hold there is no feedback during it: the menu appearing is the feedback.
 *
 * [PressRow][app.tileshell.ui.components.PressRow]'s press highlight is kept (X19, 15 % white), and the
 * highlight clears the moment the menu opens so the row is not left lit under the band.
 */
@Composable
fun HoldRow(
    onClick: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .pointerInput(onClick, onHold) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    // withTimeoutOrNull returns null for a cancelled gesture as well as for the hold, so the
                    // cancel is recorded: a list scroll cancels the row and must neither launch nor open the menu.
                    var cancelled = false
                    val up = withTimeoutOrNull(Edit.HOLD_MS) {
                        waitForUpOrCancellation().also { if (it == null) cancelled = true }
                    }
                    pressed = false
                    when {
                        up != null -> onClick()
                        cancelled -> Unit
                        // The finger was still down when the threshold passed: awaitEachGesture swallows the
                        // rest of the gesture, so the up that follows cannot also launch the app.
                        else -> onHold()
                    }
                }
            }
            .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent),
        content = content,
    )
}

/**
 * The context menu itself: a full-width band in the theme background over the list, anchored under the row
 * that was held ([anchorPx], in this overlay's coordinates) and kept inside the overlay.
 *
 * A tap anywhere off the band dismisses it; the band's own items consume their taps, so picking one does not
 * also read as a tap outside. Back is handled by the caller.
 */
@Composable
fun PinToStartMenu(
    anchorPx: Float,
    onPin: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * Uninstall (Jeremy, 2026-09-22: "I should be able to long hold app icon to bring up a quick menu to
     * uninstall it"). W10M's own hold menu carried it, under "pin to start", so this is the row's second
     * item and not a new surface.
     *
     * Null when the app cannot be uninstalled — an inbox app, or the shell itself. W10M did not offer an
     * uninstall it could not perform either, and an item that opens a dialog Android then refuses is
     * worse than no item.
     */
    onUninstall: (() -> Unit)? = null,
    /** Phase 13 E8: called once, from the band's first drawn frame, with its uptime (`[motion] applist_menu`). */
    onFirstFrame: (Long) -> Unit = {},
) {
    val colors = LocalShellColors.current
    val firstFrame = remember { booleanArrayOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .testTag("applist_menu_scrim")
            .pointerInput(onDismiss) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (waitForUpOrCancellation() != null) onDismiss()
                }
            },
    ) {
        Layout(
            content = {
                // Phase 13: the band is acrylic over the app list (surface table: T = the theme background, which is
                // also its fallback fill — H21's band as built).
                Box(
                    Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            drawContent()
                            if (!firstFrame[0]) {
                                firstFrame[0] = true
                                onFirstFrame(SystemClock.uptimeMillis())
                            }
                        }
                        .testTag("applist_menu"),
                ) {
                    AcrylicLayer(
                        FluentSurface.APPLIST_MENU,
                        fill = colors.background,
                        tint = Rgb.of(colors.background),
                        modifier = Modifier.matchParentSize(),
                    )
                    Column(Modifier.fillMaxWidth()) {
                        MenuItem("Pin to Start", "applist_menu_pin", onPin)
                        onUninstall?.let { MenuItem("Uninstall", "applist_menu_uninstall", it) }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            // Full width, but only as tall as its items: the incoming constraints are fixed to the overlay's
            // size, and passing them on would stretch the band's background over the whole list.
            val band = measurables[0].measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val room = (constraints.maxHeight - band.height).coerceAtLeast(0)
                band.place(0, anchorPx.roundToInt().coerceIn(0, room))
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, tag: String, onClick: () -> Unit) {
    val colors = LocalShellColors.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(onClick) {
                awaitEachGesture {
                    // Consumed so the scrim behind the band does not read this tap as a tap outside.
                    awaitFirstDown().consume()
                    pressed = true
                    val up = waitForUpOrCancellation()
                    pressed = false
                    if (up != null) {
                        up.consume()
                        onClick()
                    }
                }
            }
            .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent)
            // Phase 13 (Q3 B): the ring and the radial light over the pressed fill, under the label.
            .revealLights()
            .testTag(tag),
    ) {
        BasicText(label, style = ShellType.body.copy(color = colors.text), modifier = Modifier.padding(AppMenuMetrics.INSET))
    }
}
