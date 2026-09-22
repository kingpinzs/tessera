package app.tileshell.ime

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.geometry.Offset
import app.tileshell.diag.Diagnostics
import app.tileshell.ime.engine.ShiftState
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Touch in, text out. The host view hands every MotionEvent that starts in the key block to [onTouch];
 * this turns it into key presses, Word Flow gestures, the long-press popups, the space-bar move and the
 * cursor-control dot, and turns those into edits through [editor].
 *
 * Touches are handled here rather than in Compose because a keyboard is multi-touch by nature — a fast
 * typist's next finger lands before the last one lifts — and every rule below needs the exact event
 * times Android gives the MotionEvent (the 300-ms double-tap window, the long-press timeout, the 150-ms
 * caret step). Compose only draws what [state] says.
 */
class KeyboardController(
    private val state: KeyboardState,
    private val editor: Editor,
    private val brain: () -> TextBrain?,
    private val feedback: KeyFeedback,
    private val store: ImeStore,
    private val host: Host,
) {
    /** What the controller needs from the service that owns it. */
    interface Host {
        val editorInfo: EditorInfo?
        fun openEmoji()
        fun startVoice()
        /** The dock changed: the metrics are rebuilt. */
        fun dimensionsChanged()
        /** The raise changed: nothing is rebuilt, but Android must re-read the insets. */
        fun raiseChanged()
    }

    var metrics: KeyboardMetrics = KeyboardMetrics(1080f, 2340f, 3f)

    private val handler = Handler(Looper.getMainLooper())
    private var touchSlop = 16f
    private var longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    private var doubleTapMs = ViewConfiguration.getDoubleTapTimeout().toLong()

    /** Decisions stand-in (4): the engine's shift rule, fed the platform's double-tap timeout. */
    private var shift = ShiftState(doubleTapMs)

    fun configure(config: ViewConfiguration) {
        touchSlop = config.scaledTouchSlop.toFloat()
        longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
        doubleTapMs = ViewConfiguration.getDoubleTapTimeout().toLong()
        shift = ShiftState(doubleTapMs)
    }

    private fun syncShift() {
        state.shift = when (shift.mode) {
            ShiftState.Mode.OFF -> ShiftMode.OFF
            ShiftState.Mode.ONE_SHOT -> ShiftMode.ONE_SHOT
            ShiftState.Mode.CAPS_LOCK -> ShiftMode.LOCKED
        }
    }

    // ---- per-pointer tracking ---------------------------------------------------------------------

    private enum class Mode { KEY, SWIPE, ALTERNATES, SYMBOL_SLIDE, ONE_HANDED, SPACE_MOVE, CURSOR, SPENT }

    private inner class Track(val id: Int, var key: Key?, val downX: Float, val downY: Float, val downTime: Long) {
        var mode = Mode.KEY
        var x = downX
        var y = downY
        val path = ArrayList<Offset>()
        var raiseAtDown = 0f
        var cursorAxis: Char? = null
        var longPress: Runnable? = null
        var repeat: Runnable? = null
    }

    private val tracks = LinkedHashMap<Int, Track>()

    /** The last time Shift went down, for the diagnostics line only; the rule itself is [shift]'s. */
    private var lastShiftDown = -1L

    /** The last typed word and what autocorrect turned it into, so tapping it shows the original (R6 §2.2.7). */
    private val corrections = ArrayDeque<Pair<String, String>>()

    /** An unknown word committed verbatim from the strip, offered as "+ word" until the next edit. */
    private var pendingAdd: String? = null

    // ---- geometry helpers -------------------------------------------------------------------------

    private fun panelTop(): Float = metrics.panelTop(state.raise)

    fun inKeyBlock(x: Float, y: Float): Boolean {
        if (state.emojiOpen) return false
        val local = y - panelTop()
        if (local < metrics.stripH || local > metrics.panelH) return false
        val band = metrics.freeBand
        return band == null || x < band.first || x > band.second
    }

    private fun keyAt(x: Float, y: Float): Key? {
        val local = y - panelTop()
        return state.layout.hit(metrics.toPhysX(x), metrics.toPhysY(local))
    }

    fun dotCenter(): Offset {
        val physX = if (state.handedness == Handedness.LEFT) KeyGrid.dotLeftHandedX else KeyGrid.dotRightHandedX
        return Offset(metrics.x(physX), panelTop() + metrics.yInPanel(KeyGrid.dotY))
    }

    private fun onDot(x: Float, y: Float): Boolean {
        if (state.layout.layer != Layer.LETTERS && state.layout.layer != Layer.SYMBOLS_1 && state.layout.layer != Layer.SYMBOLS_2) return false
        val c = dotCenter()
        return hypot(x - c.x, y - c.y) <= metrics.h(KeyGrid.DOT_RING_D) / 2f
    }

    // ---- the event stream -------------------------------------------------------------------------

    fun onTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                down(ev.getPointerId(i), ev.getX(i), ev.getY(i), ev.eventTime)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    // Word Flow wants every sample, including the batched history between frames.
                    val id = ev.getPointerId(i)
                    val t = tracks[id] ?: continue
                    for (h in 0 until ev.historySize) move(t, ev.getHistoricalX(i, h), ev.getHistoricalY(i, h), ev.getHistoricalEventTime(h))
                    move(t, ev.getX(i), ev.getY(i), ev.eventTime)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = ev.actionIndex
                tracks[ev.getPointerId(i)]?.let {
                    // Where the finger LIFTS is part of the gesture: the last MOVE can arrive a step short
                    // of it (E9 run 2 measured a 300-px space-bar drag landing 292 px up). The lift point is
                    // applied as a final move — the raise, Word Flow's last sample, the popup cell — first.
                    move(it, ev.getX(i), ev.getY(i), ev.eventTime)
                    up(it, ev.getX(i), ev.getY(i), ev.eventTime)
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float, time: Long) {
        // Rollover: a new finger commits whatever the previous one was pressing and takes over, and the
        // new key's popup replaces the old at once (R6 §2.3.5).
        tracks.values.filter { it.mode == Mode.KEY && it.key?.code == KeyCode.CHAR }.forEach { t ->
            commitKey(t.key!!)
            endTrack(t)
        }
        if (onDot(x, y)) {
            val t = Track(id, null, x, y, time).apply { mode = Mode.CURSOR }
            tracks[id] = t
            state.cursorDrag = CursorDrag(dotCenter(), Offset(x, y))
            feedback.press()
            Diagnostics.add("ime", "cursor dot held")
            return
        }
        val key = keyAt(x, y) ?: return
        val t = Track(id, key, x, y, time)
        tracks[id] = t
        press(key)
        feedback.press()
        when (key.code) {
            KeyCode.CHAR -> {
                t.path += Offset(x, y)
                schedule(t) { longPressChar(t) }
            }
            KeyCode.SHIFT -> shiftDown(time)
            KeyCode.BACKSPACE -> {
                backspace()
                t.repeat = object : Runnable {
                    override fun run() {
                        backspace()
                        handler.postDelayed(this, REPEAT_MS)
                    }
                }.also { handler.postDelayed(it, longPressMs) }
            }
            KeyCode.SYMBOLS -> schedule(t) { openOneHanded(t) }
            KeyCode.SPACE -> {
                t.raiseAtDown = state.raise
                schedule(t) { startSpaceMove(t) }
            }
            else -> Unit
        }
    }

    private fun move(t: Track, x: Float, y: Float, time: Long) {
        t.x = x
        t.y = y
        when (t.mode) {
            Mode.KEY -> {
                val key = t.key ?: return
                val moved = hypot(x - t.downX, y - t.downY)
                when (key.code) {
                    KeyCode.CHAR -> {
                        val canSwipe = state.layout.layer == Layer.LETTERS && state.field.swipeOn && tracks.size == 1 &&
                            key.text.length == 1 && key.text[0].isLetter()
                        if (canSwipe && moved > metrics.w(SWIPE_START_PHYS)) {
                            startSwipe(t)
                            t.path += Offset(x, y)
                            state.trail = Trail(t.path.toList())
                        } else if (!canSwipe) {
                            // No Word Flow here (a symbol, a password field): the press follows the finger.
                            val now = keyAt(x, y)
                            if (now != null && now != key && now.code == KeyCode.CHAR) {
                                release(key)
                                t.key = now
                                press(now)
                                reschedule(t) { longPressChar(t) }
                            }
                        }
                    }
                    KeyCode.SYMBOLS -> if (moved > touchSlop) startSymbolSlide(t)
                    KeyCode.SPACE -> if (moved > touchSlop) cancelLongPress(t)
                    else -> Unit
                }
            }
            Mode.SWIPE -> {
                t.path += Offset(x, y)
                state.trail = Trail(tailOf(t.path))
            }
            Mode.ALTERNATES -> state.alternates?.let { state.alternates = it.copy(selected = alternateIndex(it, x)) }
            Mode.SYMBOL_SLIDE -> {
                val now = keyAt(x, y)
                val current = t.key
                if (now != current) {
                    current?.let { release(it) }
                    t.key = now
                    now?.let { press(it) }
                }
            }
            Mode.ONE_HANDED -> state.oneHanded?.let { state.oneHanded = it.copy(selected = oneHandedCell(it, x, y)) }
            Mode.SPACE_MOVE -> {
                val raise = (t.raiseAtDown + (t.downY - y)).coerceIn(0f, metrics.maxRaise)
                if (raise != state.raise) {
                    // Only the raise changes. E9 run 1 caught this calling dimensionsChanged(), which
                    // rebuilt the metrics and re-read the STORED raise (still 0) on every move, so the
                    // panel never left rest.
                    state.raise = raise
                    host.raiseChanged()
                }
            }
            Mode.CURSOR -> cursorMove(t, x, y)
            Mode.SPENT -> Unit
        }
    }

    private fun up(t: Track, x: Float, y: Float, time: Long) {
        cancelLongPress(t)
        t.repeat?.let { handler.removeCallbacks(it) }
        when (t.mode) {
            Mode.KEY -> {
                val key = t.key
                if (key != null) when (key.code) {
                    KeyCode.CHAR -> commitKey(key)
                    KeyCode.SPACE -> space()
                    KeyCode.ENTER -> enter()
                    KeyCode.SYMBOLS -> switchLayer(Layer.SYMBOLS_1)
                    KeyCode.LETTERS -> switchLayer(Layouts.initialLayer(state.field).let { if (it == Layer.PHONE) Layer.PHONE else Layer.LETTERS })
                    KeyCode.PAGE -> switchLayer(if (state.layout.layer == Layer.SYMBOLS_1) Layer.SYMBOLS_2 else Layer.SYMBOLS_1)
                    KeyCode.EMOJI -> host.openEmoji()
                    KeyCode.SHIFT, KeyCode.BACKSPACE -> Unit
                }
            }
            Mode.SWIPE -> finishSwipe(t)
            Mode.ALTERNATES -> {
                val alt = state.alternates
                if (alt != null) {
                    val cell = alt.cells.getOrNull(alt.selected)
                    if (cell != null) typeText(cell, "alternate")
                }
                state.alternates = null
            }
            Mode.SYMBOL_SLIDE -> {
                // Decisions stand-in (1): lifting over a key commits it and returns to the letters;
                // lifting back on &123 commits nothing.
                val key = t.key
                if (key != null && key.code == KeyCode.CHAR) commitKey(key)
                switchLayer(Layer.LETTERS)
            }
            Mode.ONE_HANDED -> {
                state.oneHanded?.selected?.let { setDock(it) }
                state.oneHanded = null
            }
            Mode.SPACE_MOVE -> {
                store.raise = state.raise / metrics.sy
                Diagnostics.add("ime", "keyboard moved: raise ${state.raise.toInt()} px (${(state.raise / metrics.sy).toInt()} phys)")
            }
            Mode.CURSOR -> {
                state.cursorDrag = null
                Diagnostics.add("ime", "cursor dot released")
            }
            Mode.SPENT -> Unit
        }
        endTrack(t)
    }

    private fun endTrack(t: Track) {
        cancelLongPress(t)
        t.repeat?.let { handler.removeCallbacks(it) }
        t.key?.let { release(it) }
        t.mode = Mode.SPENT
        tracks.remove(t.id)
        if (tracks.isEmpty()) state.popup = null
    }

    fun cancelAll() {
        tracks.values.toList().forEach { endTrack(it) }
        state.alternates = null
        state.oneHanded = null
        state.cursorDrag = null
        state.popup = null
        state.pressed.clear()
    }

    // ---- press visuals ----------------------------------------------------------------------------

    private fun press(key: Key) {
        if (key.id !in state.pressed) state.pressed += key.id
        // R6 §2.3.1 / §2.3.5: letters get the popup, function keys only fill with the accent.
        state.popup = if (key.code == KeyCode.CHAR && key.style == KeyStyle.DARK && key.id != "dotcom") {
            KeyPopup(key, labelFor(key))
        } else null
    }

    private fun release(key: Key) {
        state.pressed.remove(key.id)
        if (state.popup?.key == key) state.popup = null
    }

    fun labelFor(key: Key): String = when {
        key.code != KeyCode.CHAR -> key.text
        state.upper && key.text.length == 1 -> key.text.uppercase()
        else -> key.text
    }

    // ---- long presses -----------------------------------------------------------------------------

    private fun schedule(t: Track, action: () -> Unit) {
        val r = Runnable { if (tracks[t.id] === t) action() }
        t.longPress = r
        handler.postDelayed(r, longPressMs)
    }

    private fun reschedule(t: Track, action: () -> Unit) {
        cancelLongPress(t)
        schedule(t, action)
    }

    private fun cancelLongPress(t: Track) {
        t.longPress?.let { handler.removeCallbacks(it) }
        t.longPress = null
    }

    /** Decisions stand-in (3): after the long-press timeout, the alternates popup (H19). */
    private fun longPressChar(t: Track) {
        val key = t.key ?: return
        if (t.mode != Mode.KEY || key.text.length != 1) return
        val cells = brain()?.alternates(key.text, state.upper) ?: listOf(labelFor(key))
        if (cells.size <= 1) return // a letter with no alternates keeps only the normal press popup
        t.mode = Mode.ALTERNATES
        state.popup = null
        // The plain letter sits over the pressed key and the rest run rightward, unless that would run
        // off the panel, in which case they run leftward from the same first cell.
        val rightEdge = metrics.x(key.centerX) + metrics.w(KeyGrid.PITCH) * (cells.size - 0.5f)
        val leftward = rightEdge > metrics.screenWidthPx
        state.alternates = AlternatesPopup(key, cells, leftward, 0)
        Diagnostics.add("ime", "alternates for ${key.text}: ${cells.joinToString(" ")}")
    }

    private fun alternateIndex(p: AlternatesPopup, x: Float): Int {
        val pitch = metrics.w(KeyGrid.PITCH)
        val first = metrics.x(p.key.centerX)
        val steps = ((if (p.leftward) first - x else x - first) / pitch + 0.5f).toInt()
        return steps.coerceIn(0, p.cells.size - 1)
    }

    /** Decisions stand-in (1)/(2): holding &123 still opens the one-handed options (H20). */
    private fun openOneHanded(t: Track) {
        val key = t.key ?: return
        if (t.mode != Mode.KEY) return
        t.mode = Mode.ONE_HANDED
        state.oneHanded = OneHandedPopup(key, null)
        Diagnostics.add("ime", "one-handed options open")
    }

    /** The cell under the finger: dock left, full width, dock right, or null when outside the popup. */
    private fun oneHandedCell(p: OneHandedPopup, x: Float, y: Float): Dock? {
        val left = metrics.x(p.key.left)
        val pitch = metrics.w(KeyGrid.PITCH)
        val bottom = panelTop() + metrics.yInPanel(p.key.top - KeyGrid.POPUP_LIFT)
        val top = bottom - metrics.h(KeyGrid.POPUP_H)
        if (y < top || y > bottom) return null
        val i = ((x - left) / pitch).toInt()
        if (x < left || i > 2) return null
        return listOf(Dock.LEFT, Dock.FULL, Dock.RIGHT)[i]
    }

    fun setDock(dock: Dock) {
        state.dock = dock
        store.dock = dock
        Diagnostics.add("ime", "dock $dock")
        host.dimensionsChanged()
    }

    private fun startSymbolSlide(t: Track) {
        cancelLongPress(t)
        t.mode = Mode.SYMBOL_SLIDE
        t.key?.let { release(it) }
        t.key = null
        // Decisions stand-in (1): the symbol layer shows at once.
        switchLayer(Layer.SYMBOLS_1)
        Diagnostics.add("ime", "&123 slide-to-type")
    }

    private fun startSpaceMove(t: Track) {
        if (t.mode != Mode.KEY) return
        t.mode = Mode.SPACE_MOVE
        Diagnostics.add("ime", "space-bar move started")
    }

    // ---- Word Flow (R6 §2.4, LOW, H3) --------------------------------------------------------------

    private fun startSwipe(t: Track) {
        cancelLongPress(t)
        t.mode = Mode.SWIPE
        t.key?.let { release(it) }
        state.popup = null
    }

    /** R6 §2.4.3: a comet — only the recent part of the path shows, the tail erased behind the finger. */
    private fun tailOf(path: List<Offset>): List<Offset> {
        val keep = metrics.w(TRAIL_LENGTH_PHYS)
        var length = 0f
        var i = path.size - 1
        while (i > 0 && length < keep) {
            length += (path[i] - path[i - 1]).getDistance()
            i--
        }
        return path.subList(i, path.size).toList()
    }

    private fun finishSwipe(t: Track) {
        val phys = t.path.map { metrics.toPhysX(it.x) to metrics.toPhysY(it.y - panelTop()) }
        val words = brain()?.decodeSwipe(phys).orEmpty()
        retractTrail(tailOf(t.path))
        Diagnostics.add("ime", "word flow ${t.path.size} samples -> ${words.take(5)}")
        val word = words.firstOrNull() ?: return
        val before = editor.before(2)
        val needsSpace = before.isNotEmpty() && (before.last().isLetterOrDigit() || before.last() in ".,!?;:)\"'")
        val shaped = shapeCase(word)
        editor.commit((if (needsSpace) " " else "") + shaped, "word flow")
        brain()?.committed(word, state.field)
        afterWordCommit()
        // R6 §2.4.5: on lift the strip shows the swiped word's candidates, bold first.
        state.strip = words.take(STRIP_MAX).mapIndexed { i, w -> StripItem(shapeCase(w), bold = i == 0) }
        swipeCandidates = words.take(STRIP_MAX).map { shapeCase(it) }
        swipeCommitted = shaped
    }

    private var swipeCandidates: List<String> = emptyList()
    private var swipeCommitted: String? = null

    /** R6 §2.4.4: the trail stays ≈100 ms after lift, then retracts over ≈250 ms toward the lift point. */
    private fun retractTrail(points: List<Offset>) {
        state.trail = Trail(points, 0f)
        val start = SystemClock.uptimeMillis() + TRAIL_HOLD_MS
        val tick = object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                val f = ((now - start).toFloat() / TRAIL_RETRACT_MS).coerceIn(0f, 1f)
                if (f >= 1f) {
                    state.trail = null
                } else {
                    state.trail = Trail(points, f)
                    handler.postDelayed(this, 16)
                }
            }
        }
        handler.postDelayed(tick, TRAIL_HOLD_MS)
    }

    // ---- the cursor-control dot (R6 §2.5) ----------------------------------------------------------

    /**
     * R6 §2.5.8 (LOW, H5): a direction-locked joystick, not a trackpad. Once the finger leaves the dot's
     * ring the axis locks; holding left or right steps the caret one character every ≈150 ms, holding
     * above or below one line every ≈750 ms (inside §2.5.8's 0.5–1 s).
     */
    private fun cursorMove(t: Track, x: Float, y: Float) {
        val c = dotCenter()
        state.cursorDrag = CursorDrag(c, Offset(x, y))
        val dx = x - c.x
        val dy = y - c.y
        val dead = metrics.h(KeyGrid.DOT_RING_D) / 2f
        if (t.cursorAxis == null && hypot(dx, dy) > dead) {
            t.cursorAxis = if (abs(dx) >= abs(dy)) 'x' else 'y'
            val stepper = object : Runnable {
                override fun run() {
                    if (tracks[t.id] !== t) return
                    val cc = dotCenter()
                    val ddx = t.x - cc.x
                    val ddy = t.y - cc.y
                    val interval = if (t.cursorAxis == 'x') STEP_CHAR_MS else STEP_LINE_MS
                    if (t.cursorAxis == 'x' && abs(ddx) > dead) {
                        editor.sendKey(if (ddx < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
                    } else if (t.cursorAxis == 'y' && abs(ddy) > dead) {
                        editor.sendKey(if (ddy < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN)
                    }
                    handler.postDelayed(this, interval)
                }
            }
            t.repeat = stepper
            handler.post(stepper)
            Diagnostics.add("ime", "cursor axis ${t.cursorAxis}")
        }
    }

    // ---- shift (Decisions stand-in (4), H22) --------------------------------------------------------

    /**
     * The touch-down time is the MotionEvent's own, so the 300-ms window is judged on the times Android
     * recorded, not on when this code happened to run.
     */
    private fun shiftDown(time: Long) {
        val sinceLast = if (lastShiftDown < 0) -1L else time - lastShiftDown
        shift.tapShift(time)
        syncShift()
        lastShiftDown = time
        Diagnostics.add("ime", "shift -> ${state.shift} (${sinceLast} ms since the last shift down)")
    }

    // ---- editing ----------------------------------------------------------------------------------

    private fun commitKey(key: Key) {
        if (key.code != KeyCode.CHAR) return
        typeText(labelFor(key), "key ${key.id}")
    }

    /** One typed character (or ".com", or an alternate). Punctuation that ends a word runs autocorrect first. */
    private fun typeText(text: String, why: String) {
        pendingAdd = null
        if (text.length == 1 && text[0] in WORD_ENDERS) autocorrectBefore()
        editor.commit(text, why)
        if (text.any { it.isLetter() }) {
            shift.typeLetter()
            syncShift()
        }
        refreshStrip(typing = true)
        autoShift()
    }

    private fun backspace() {
        pendingAdd = null
        editor.backspace()
        refreshStrip(typing = true)
        autoShift()
    }

    private fun space() {
        pendingAdd = null
        val field = state.field
        val before = editor.before(64)
        val since = SystemClock.uptimeMillis() - editor.lastSpaceAt
        if (editor.lastSpaceAt > 0 && brain()?.doubleSpacePeriod(before, since, field) == true) {
            // Decisions stand-in (5): the first space becomes ". ".
            editor.replaceAround(1, 0, ". ", "double-space period")
        } else {
            autocorrectBefore()
            editor.commit(" ", "space")
        }
        refreshStrip(typing = true)
        autoShift()
    }

    private fun enter() {
        pendingAdd = null
        val field = state.field
        val info = host.editorInfo
        autocorrectBefore()
        when (field.enterAction) {
            FieldAction.NONE -> if (field.multiLine) editor.commit("\n", "enter") else editor.sendKey(KeyEvent.KEYCODE_ENTER)
            else -> editor.performAction((info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION)
        }
        refreshStrip(typing = true)
        autoShift()
    }

    /** R6 §2.2.6: the bold first suggestion replaces the word just typed when a word ends. */
    private fun autocorrectBefore() {
        val field = state.field
        if (!field.suggestionsOn) return
        val word = wordBeforeCaret(editor.before(READ_WINDOW))
        if (word.isEmpty()) return
        val b = brain() ?: return
        val offer = b.offer(word, previousWord(), field)
        val fix = offer.items.firstOrNull()
        if (offer.autoCorrect && fix != null && fix != word) {
            editor.replaceAround(word.length, 0, fix, "autocorrect")
            corrections.addLast(fix to word)
            while (corrections.size > 8) corrections.removeFirst()
            b.committed(fix, field)
        } else {
            b.committed(word, field)
        }
    }

    /** Called when the strip's item [index] is tapped. */
    fun stripTapped(item: StripItem) {
        val b = brain()
        when (item.kind) {
            StripItem.Kind.ADD -> {
                b?.add(item.text)
                Diagnostics.add("ime", "dictionary + ${item.text}")
                pendingAdd = null
                refreshStrip(typing = false)
                return
            }
            StripItem.Kind.REMOVE -> {
                b?.remove(item.text)
                Diagnostics.add("ime", "dictionary - ${item.text}")
                refreshStrip(typing = false)
                return
            }
            else -> Unit
        }
        val before = editor.before(64)
        val after = editor.after(64)
        val (left, right) = if (swipeCommitted != null && before.endsWith(swipeCommitted!!)) {
            swipeCommitted!!.length to 0
        } else {
            wordBeforeCaret(before).length to leadingWord(after).length
        }
        val chosen = item.text
        // D1 l.1558–1560 and R6 §2.2.7: picking the word as typed keeps it and offers "+ word" for it.
        editor.replaceAround(left, right, "$chosen ", "suggestion ${item.kind}")
        swipeCommitted = null
        b?.committed(chosen, state.field)
        pendingAdd = if (item.kind == StripItem.Kind.VERBATIM && b != null && !b.isKnown(chosen) && state.field.learningOn) chosen else null
        refreshStrip(typing = false)
        autoShift()
    }

    // ---- the strip --------------------------------------------------------------------------------

    /** The selection moved: [own] is true when it was the keyboard's own edit. */
    fun selectionMoved(own: Boolean) {
        if (!own) {
            swipeCommitted = null
            refreshStrip(typing = false)
            autoShift()
        }
    }

    /**
     * Rebuild the strip from the text around the caret. While typing it is the dictionary's offer for the
     * word being typed, bold first when it will autocorrect (R6 §2.2.6). When the caret has been put in a
     * word (a tap in the text), it is that word's original spelling if autocorrect changed it, "+ word" /
     * "– word", and alternatives (R6 §2.2.7).
     */
    fun refreshStrip(typing: Boolean) {
        val field = state.field
        state.notice = null
        if (!field.suggestionsOn) {
            state.strip = emptyList()
            return
        }
        pendingAdd?.let { state.strip = listOf(StripItem(it, kind = StripItem.Kind.ADD)); return }
        val b = brain() ?: run { state.strip = emptyList(); return }
        val before = editor.before(READ_WINDOW)
        val after = editor.after(READ_WINDOW)
        val prefix = wordBeforeCaret(before)
        val suffix = leadingWord(after)
        val items = mutableListOf<StripItem>()
        if (typing && suffix.isEmpty()) {
            if (prefix.isEmpty()) {
                state.strip = emptyList()
                return
            }
            val offer = b.offer(prefix, previousWord(), field)
            offer.items.take(STRIP_MAX).forEachIndexed { i, w ->
                items += StripItem(w, bold = i == 0 && offer.autoCorrect, kind = if (w == prefix) StripItem.Kind.VERBATIM else StripItem.Kind.WORD)
            }
            if (items.none { it.text == prefix }) items += StripItem(prefix, kind = StripItem.Kind.VERBATIM)
        } else {
            val word = prefix + suffix
            if (word.isEmpty()) {
                state.strip = emptyList()
                return
            }
            corrections.lastOrNull { it.first == word }?.let { items += StripItem(it.second, kind = StripItem.Kind.ORIGINAL) }
            when {
                b.isLearned(word) -> items += StripItem(word, kind = StripItem.Kind.REMOVE)
                !b.isKnown(word) && field.learningOn -> items += StripItem(word, kind = StripItem.Kind.ADD)
            }
            b.offer(word, null, field).items.filter { w -> items.none { it.text == w } }.take(STRIP_MAX).forEach { items += StripItem(it) }
        }
        state.strip = items
    }

    private fun previousWord(): String? {
        val before = editor.before(64).trimEnd()
        val stripped = before.dropLastWhile { isWordChar(it) }.trimEnd()
        return trailingWord(stripped).ifEmpty { null }
    }

    // ---- layers, capitals, case -------------------------------------------------------------------

    fun switchLayer(layer: Layer) {
        if (state.layout.layer == layer) return
        state.layout = Layouts.build(layer, state.field)
        Diagnostics.add("ime", "layer $layer")
    }

    /**
     * Sentence capitals through the platform's own rule for this field (getCursorCapsMode), which reads
     * the field's capitalisation flags. A one-shot shift the user tapped is left alone; one this put there
     * is taken away again when the caret leaves the sentence start.
     */
    fun autoShift() {
        val info = host.editorInfo ?: return
        if (editor.capsMode(info.inputType)) shift.autoCapitalise() else shift.cancelAutoCapital()
        syncShift()
    }

    /** A dictionary word in the case the user is typing in: capitals follow shift and caps lock. */
    private fun shapeCase(word: String): String = when {
        state.shift == ShiftMode.LOCKED -> word.uppercase()
        state.shift == ShiftMode.ONE_SHOT -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    /** A swiped word spends a one-shot shift the way a typed letter does. */
    private fun afterWordCommit() {
        shift.typeLetter()
        syncShift()
    }

    fun reset() {
        cancelAll()
        lastShiftDown = -1L
        pendingAdd = null
        swipeCommitted = null
        corrections.clear()
        // Edge case "caps lock then switching fields": nothing carries into the next field.
        shift.reset()
        syncShift()
    }

    companion object {
        /** Movement past half a key pitch from a letter starts Word Flow rather than a slide. */
        const val SWIPE_START_PHYS = 72f

        /** R6 §2.4.3 (LOW): the comet shows ≈240 video px of path, ≈1000 phys-eq. */
        const val TRAIL_LENGTH_PHYS = 1000f
        const val TRAIL_HOLD_MS = 100L
        const val TRAIL_RETRACT_MS = 250L

        /** R6 §2.5.8 (LOW): ≈150 ms per character; 0.5–1 s per line. */
        const val STEP_CHAR_MS = 150L
        const val STEP_LINE_MS = 750L

        /** Backspace auto-repeat once held past the long-press timeout (approximation, H2). */
        const val REPEAT_MS = 50L

        const val STRIP_MAX = 8

        private val WORD_ENDERS = setOf('.', ',', '!', '?', ';', ':')

        /** How much text the keyboard reads either side of the caret. */
        const val READ_WINDOW = 64

        /**
         * The word just before the caret, or "" when there is none — or when the letters run all the way
         * back to the start of the [READ_WINDOW] the field returned, so the word's real start is unknown.
         * EDGE1 run 2 caught the keyboard counting a 64-letter slice of a long run toward learning.
         */
        fun wordBeforeCaret(before: String): String {
            val w = trailingWord(before)
            return if (before.length >= READ_WINDOW && w.length >= before.length - 1) "" else w
        }

        fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '-'
        fun trailingWord(s: String) = s.takeLastWhile { isWordChar(it) }.trimStart('\'', '-')
        fun leadingWord(s: String) = s.takeWhile { isWordChar(it) }.trimEnd('\'', '-')
    }
}
