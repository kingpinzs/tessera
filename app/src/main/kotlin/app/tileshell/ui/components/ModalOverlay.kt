package app.tileshell.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout

/**
 * L13-3: whether the overlay this point of the tree is drawn in is still open, read from the state its dismissal writes
 * (the band's `menu`, a grid's `gridOpen`, Music's `menu` / `more` / `naming`). Provided by [OverlayLayer]; outside
 * one it is always true.
 */
val LocalOverlayActive = staticCompositionLocalOf<() -> Boolean> { { true } }

/**
 * L13-3: the layer an overlay is drawn in over a page (the app list's band and jump grid, Music's jump grid; since L13-6
 * also Music's hold menu, Now Playing's more menu and the playlist name box). It places its content only while [active]
 * is true, and it reads [active] while placing, so a dismissal takes the overlay out of the layout at the next layout
 * pass. Compose runs that pass before it hit-tests each touch, and it hit-tests only
 * placed nodes, so a down that follows the dismissal reaches the page instead of the stale scrim. Before this, a
 * dismissal only wrote the state; the overlay stayed placed until it left the composition a frame later, and a down in
 * that frame landed on its full-screen scrim and was lost.
 *
 * The overlay is out of that down's hit path altogether, so its leaving cancels nothing of the page's gesture. The
 * first fix (b2d44b7d) had the stale overlay share the down with the page instead. Compose then puts the page's nodes
 * under the overlay's in that pointer's hit path, and a node that leaves cancels everything under it, so the hold was
 * cancelled anyway (qa/phase-13/L13-3-diag2-row-events-5d1a37d5/).
 *
 * Measured like a Box's children: the constraints loosened, the content at the top-left.
 */
@Composable
fun OverlayLayer(active: () -> Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(
        content = { CompositionLocalProvider(LocalOverlayActive provides active, content = content) },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = maxOf(constraints.minWidth, placeables.maxOfOrNull { it.width } ?: 0)
        val height = maxOf(constraints.minHeight, placeables.maxOfOrNull { it.height } ?: 0)
        layout(width, height) {
            if (active()) placeables.forEach { it.place(0, 0) }
        }
    }
}

/**
 * L13-3: writes an overlay's dismissal and applies it at once, so the [OverlayLayer] reading it is marked for layout
 * before the next touch. Left to Compose, a write outside composition is applied at the next main-thread message, after
 * any touch that is already queued.
 */
fun dismissOverlay(write: () -> Unit) {
    write()
    Snapshot.sendApplyNotifications()
}

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
    val active by rememberUpdatedState(LocalOverlayActive.current)
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
                        // L13-5: Compose ends a gesture whose node leaves (the overlay removed under the finger) with a
                        // synthetic up that arrives already consumed; a real lift reaches the item unconsumed (the root
                        // consumes after its items, in the same Main pass). Only a real lift runs the item, and only
                        // while its overlay is open (L13-3): once the OverlayLayer unplaces the overlay the item gets no
                        // events at all, but a dismissal whose write has not reached layout yet leaves it placed.
                        val lifted = !change.isConsumed
                        change.consume()
                        if (press.lift(onItem, lifted, open = active())) {
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

    /**
     * The lift: true when it runs the item — the press never ended, the finger is on the item, it is a real lift
     * ([lifted] false: the synthetic end of a gesture whose overlay was removed under the finger, L13-5), and the
     * overlay is still open ([open] false: it was dismissed under the finger and is still placed, because the write has
     * not reached layout when the lift is dispatched, L13-3).
     */
    fun lift(onItem: Boolean, lifted: Boolean = true, open: Boolean = true): Boolean {
        val runs = pressed && onItem && lifted && open
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
