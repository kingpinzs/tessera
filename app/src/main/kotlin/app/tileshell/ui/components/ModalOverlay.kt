package app.tileshell.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput

/**
 * L13-2 (review/2026-09-25-L13-2-fix-plan.md, Jeremy's Q1 (A)): an overlay drawn inside a pivot's page — the app list's
 * hold band and jump grid, Music's jump grid — is modal, and it enforces that itself. A gesture that starts on it
 * belongs to it: this root consumes every pointer change of that gesture in the Main pass, after the overlay's own items
 * have seen it and before any ancestor does, so the pivot around it (Start's, Music's) never sees a drag to take. Only
 * a tap on the root, off every item, runs [onTapOff] ([OverlayRootTap]); a drag on it runs nothing and leaves it open.
 *
 * Keyed on nothing, with the callback read through [rememberUpdatedState]: a pointerInput keyed on a lambda restarts
 * whenever its caller recomposes, and a restart mid-gesture would hand the rest of that gesture to the pivot.
 */
@Composable
fun Modifier.modalOverlay(onTapOff: () -> Unit): Modifier {
    val tapOff by rememberUpdatedState(onTapOff)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // An item consumes its own down ([overlayItem]), so a down that arrives consumed is a gesture on an item.
            val tap = OverlayRootTap(downTakenByItem = down.isConsumed, slopPx = viewConfiguration.touchSlop)
            down.consume()
            while (true) {
                val event = awaitPointerEvent()
                event.changes.firstOrNull { it.id == down.id }?.let {
                    tap.move(it.position.x - down.position.x, it.position.y - down.position.y)
                }
                val ended = event.changes.all { !it.pressed }
                event.changes.forEach { it.consume() }
                if (ended) {
                    if (tap.lift()) tapOff()
                    break
                }
            }
        }
    }
}

/**
 * An item inside a [modalOverlay]: pressed from its down while the finger stays on it, ended for good the first time
 * the finger leaves it, run only by a lift on it ([OverlayItemPress]; phase 13 E7(d)'s press contract). It consumes its
 * down, so the root knows the gesture is an item's and the scrim does not read it as a tap off the items. It follows its
 * own bounds instead of `waitForUpOrCancellation`, which treats any consumed change as a cancel — and the root consumes
 * every move, so that helper would end every item's press on the first move.
 */
@Composable
fun Modifier.overlayItem(onPressedChange: (Boolean) -> Unit, onRun: () -> Unit): Modifier {
    val pressedChange by rememberUpdatedState(onPressedChange)
    val run by rememberUpdatedState(onRun)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val press = OverlayItemPress()
            pressedChange(true)
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val onItem = !change.isOutOfBounds(size, extendedTouchPadding)
                    if (change.changedToUpIgnoreConsumed()) {
                        change.consume()
                        if (press.lift(onItem)) {
                            pressedChange(false)
                            run()
                        }
                        break
                    }
                    press.move(onItem)
                    pressedChange(press.pressed)
                }
            } finally {
                press.cancel()
                pressedChange(false)
            }
        }
    }
}

/** An item's press inside a modal overlay (L13-2): held while the finger stays on it, ended for good when it leaves. */
class OverlayItemPress {
    var pressed = true
        private set

    fun move(onItem: Boolean) {
        if (!onItem) pressed = false
    }

    /** The lift: true when it runs the item — the press never ended and the finger is on the item. */
    fun lift(onItem: Boolean): Boolean {
        val runs = pressed && onItem
        pressed = false
        return runs
    }

    fun cancel() {
        pressed = false
    }
}

/**
 * The root's tap off the items (L13-2): a gesture no item took, lifted without ever moving past [slopPx] from its down.
 * A drag that comes back to where it started is still a drag.
 */
class OverlayRootTap(private val downTakenByItem: Boolean, private val slopPx: Float) {
    private var dragged = false

    fun move(dx: Float, dy: Float) {
        if (dx * dx + dy * dy > slopPx * slopPx) dragged = true
    }

    fun lift(): Boolean = !downTakenByItem && !dragged
}
