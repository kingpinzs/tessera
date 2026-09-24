package app.tileshell.calculator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tileshell.calc.convert.ConverterCategory
import app.tileshell.calc.convert.ConverterState
import app.tileshell.calc.convert.ConverterUnit
import app.tileshell.calc.date.DateCalculatorState
import app.tileshell.calc.engine.CalcMode
import app.tileshell.calc.engine.Calculator
import app.tileshell.diag.Diagnostics
import java.time.LocalDate

/** The Programmer page's two keypad tabs (4.7): the full keypad and the bit toggles. */
enum class ProgrammerTab { KEYPAD, BITS }

/**
 * The one state behind the Calculator's screens: ONE [Calculator] for the app (modes switched on it, so the display
 * value carries across modes — E12), one [ConverterState] and one [DateCalculatorState]. The engine objects are not
 * observable, so every change to them bumps [tick], which the composables read; nothing here computes — every answer
 * is the engine's (the brief: "the UI never computes").
 *
 * Diagnostics (Harness contracts): `[calc] error <kind>` on each error the engine shows (its `errorKind`), and
 * `[calc] date <op> <from> <to|amount> -> <result>` per date computation. Memory and history are written to
 * [CalcStore] after every change that moves them (H11: they persist until cleared).
 */
class CalcModel(private val store: CalcStore, today: LocalDate) {
    val calc = Calculator(CalcMode.STANDARD)
    val converter: ConverterState
    val date = DateCalculatorState(today)

    /** Bumped after every engine change so the composables that read it recompose. */
    var tick by mutableIntStateOf(0)
        private set

    var page by mutableStateOf(CalcLayout.DEFAULT_PAGE)
        private set

    var paneOpen by mutableStateOf(false)
    var historyOpen by mutableStateOf(false)
    var memoryOpen by mutableStateOf(false)
    var aboutOpen by mutableStateOf(false)
    var programmerTab by mutableStateOf(ProgrammerTab.KEYPAD)

    /** The date page's own inputs (the engine keeps the difference's and the add / subtract's dates apart). */
    var dateOp by mutableStateOf(DateOp.DIFFERENCE)
        private set

    private var lastErrorCount = 0

    init {
        store.readMemory()?.let { if (!calc.restoreMemory(it)) Diagnostics.add("calc", "memory file ignored (malformed)") }
        store.readHistory()?.let { if (!calc.restoreHistory(it)) Diagnostics.add("calc", "history file ignored (malformed)") }
        converter = ConverterState(store.readConverter())
        Diagnostics.add("calc", "restored memory=${calc.memory.size} history=${calc.history.size} converter=${converter.category.label}")
        lastErrorCount = calc.errorCount
    }

    // ------------------------------------------------------------------ the calculator modes

    /** Presses one vocabulary key on the engine. */
    fun press(key: String): Boolean {
        val ok = calc.press(key)
        afterEngine()
        return ok
    }

    /** The bit-toggle keypad (4.7): flips bit [bit] of the Programmer value. */
    fun toggleBit(bit: Int) {
        if (calc.toggleBit(bit)) afterEngine()
    }

    /** MR on memory item [index] (0 = the newest), from the memory flyout. */
    fun memoryRecall(index: Int) {
        calc.memoryRecall(index)
        afterEngine()
    }

    fun memoryClearAll() {
        calc.memoryClearAll()
        afterEngine()
    }

    fun clearHistory() {
        calc.clearHistory()
        Diagnostics.add("calc", "history cleared (${calc.mode.name.lowercase()})")
        afterEngine()
    }

    /**
     * A history entry tapped: Windows loads it back into the display; the port has no replay, so its result is
     * typed through the engine's paste route (digit grouping stripped). A result paste can refuse nothing the
     * engine displayed as a number, so the display then shows that value.
     */
    fun recallHistory(index: Int) {
        val item = calc.history.getOrNull(index) ?: return
        calc.paste(item.result.replace(",", ""))
        afterEngine()
    }

    /** The paste route (`calc_paste`, T15-57): the clipboard's text through the engine's Windows paste rules. */
    fun paste(text: String?): Boolean {
        if (text == null) {
            Diagnostics.add("calc", "paste: clipboard empty")
            return false
        }
        val ok = calc.paste(text)
        Diagnostics.add("calc", "paste \"${text.take(64)}\" -> ${if (ok) calc.displayText else "refused: " + calc.displayText}")
        afterEngine()
        return ok
    }

    private fun afterEngine() {
        if (calc.errorCount != lastErrorCount) {
            lastErrorCount = calc.errorCount
            calc.lastErrorKind?.let { Diagnostics.add("calc", "error ${it.logName}") }
        }
        store.save(calc.encodeMemory(), calc.encodeHistory())
        tick++
    }

    // ------------------------------------------------------------------ pages

    /** Shows [target]; a calculator page switches the engine's mode keeping the display value (E12). */
    fun showPage(target: CalcPage) {
        paneOpen = false
        historyOpen = false
        memoryOpen = false
        aboutOpen = false
        if (target == page) return
        target.mode?.let { calc.setMode(it) }
        page = target
        Diagnostics.add("calc", "mode ${target.id}")
        afterEngine()
    }

    /** The pane's category rows and the `converter` shortcut: the Converter on [category]. */
    fun showConverter(category: ConverterCategory) {
        if (converter.category != category) {
            converter.selectCategory(category)
            store.saveConverter(converter.saveUserPreferences())
            Diagnostics.add("calc", "converter ${category.label}")
        }
        showPage(CalcPage.CONVERTER)
        tick++
    }

    /** The `converter` shortcut (T15-43): the last-used category, Volume the first time (the saved preferences). */
    fun showConverterLastUsed() {
        Diagnostics.add("calc", "converter ${converter.category.label} (last used)")
        showPage(CalcPage.CONVERTER)
    }

    // ------------------------------------------------------------------ the converter

    fun converterPress(key: String) {
        converter.press(key)
        tick++
    }

    fun converterSwitchActive() {
        converter.switchActive()
        tick++
    }

    fun converterSelectUnit(top: Boolean, unit: ConverterUnit) {
        if (top) converter.selectUnit1(unit) else converter.selectUnit2(unit)
        store.saveConverter(converter.saveUserPreferences())
        tick++
    }

    // ------------------------------------------------------------------ date calculation

    fun chooseDateOp(op: DateOp) {
        if (op == dateOp) return
        dateOp = op
        date.isDateDiffMode = op == DateOp.DIFFERENCE
        if (op != DateOp.DIFFERENCE) date.isAddMode = op == DateOp.ADD
        logDate()
        tick++
    }

    /** The "From" date of both forms (the engine keeps `fromDate` and `startDate` apart; the page shows one field). */
    fun setDateFrom(value: LocalDate) {
        if (value == date.fromDate && value == date.startDate) return
        date.fromDate = value
        date.startDate = value
        logDate()
        tick++
    }

    fun setDateTo(value: LocalDate) {
        if (value == date.toDate) return
        date.toDate = value
        logDate()
        tick++
    }

    fun setDateAmount(years: Int, months: Int, days: Int) {
        if (years == date.yearsOffset && months == date.monthsOffset && days == date.daysOffset) return
        date.yearsOffset = years
        date.monthsOffset = months
        date.daysOffset = days
        logDate()
        tick++
    }

    /** The result text as the page shows it: one line, or two lines (the total in days second). */
    val dateResultLines: List<String>
        get() = if (dateOp == DateOp.DIFFERENCE) {
            if (date.isDiffInDays) listOf(date.strDateDiffResult) else listOf(date.strDateDiffResult, date.strDateDiffResultInDays)
        } else {
            listOf(date.strDateResult)
        }

    private fun logDate() {
        val op = dateOp.id
        val inputs = if (dateOp == DateOp.DIFFERENCE) "${date.fromDate} ${date.toDate}"
        else "${date.startDate} years=${date.yearsOffset} months=${date.monthsOffset} days=${date.daysOffset}"
        Diagnostics.add("calc", "date $op $inputs -> ${dateResultLines.joinToString(" | ")}")
    }
}

/** The Date calculation page's operations (`calc_date_op:<id>`, T15-16). */
enum class DateOp(val id: String, val label: String) {
    DIFFERENCE("difference", "Difference between dates"),
    ADD("add", "Add"),
    SUBTRACT("subtract", "Subtract"),
}
