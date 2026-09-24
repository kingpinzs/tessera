package app.tileshell.calc.convert

import app.tileshell.calc.convert.ConverterNumberFormat.fixed
import app.tileshell.calc.convert.ConverterNumberFormat.numberDigits
import app.tileshell.calc.convert.ConverterNumberFormat.scientific
import app.tileshell.calc.convert.ConverterNumberFormat.trimTrailingZeros
import app.tileshell.calc.convert.ConverterNumberFormat.wholeNumberDigits

/** CalcManager's converter `Command`s that the converter pad sends. */
internal enum class EngineCommand { ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, DECIMAL, NEGATE, BACKSPACE, CLEAR }

/**
 * Port of microsoft/calculator@4fd3fc5 src/CalcManager/UnitConverter.cpp — the converter model, with the static data
 * loader of [UnitTables] and no currency loader. Arithmetic is IEEE double, as the C++ is (`stod`, `Convert`,
 * `log10`); CalcManager's rational engine is not used by the converter.
 *
 * The C++ calls back into the view model after every calculation (`DisplayCallback`, `SuggestedValueCallback`,
 * `MaxDigitsReached`); here the same values land in [displayFrom], [displayTo], [suggestions] and
 * [maxDigitsReachedCount], which [ConverterState] reads after each call.
 */
internal class UnitConverterEngine {
    private val categories: List<ConverterCategory> = ConverterCategory.entries
    private val categoryToUnits: Map<ConverterCategory, List<ConverterUnit>> =
        categories.associateWith { UnitTables.orderedUnits(it) }

    // Unit.isConversionSource / isConversionTarget as held in m_categoryToUnits; SetCurrentCategory rewrites them for
    // the category being left, so a category reopens on the pair last used in it.
    private val isConversionSource = HashMap<Int, Boolean>()
    private val isConversionTarget = HashMap<Int, Boolean>()

    // m_ratioMap: conversions per source unit, plus CalculateSuggested's iteration order over them.
    private val ratioMap: Map<Int, Map<Int, ConversionData>>
    private val suggestionOrder: Map<Int, List<ConverterUnit>>

    var currentCategory: ConverterCategory = categories[0]
        private set
    var fromType: ConverterUnit? = null
        private set
    var toType: ConverterUnit? = null
        private set

    private var currentDisplay = "0"
    private var returnDisplay = ""
    private var currentHasDecimal = false
    private var returnHasDecimal = false

    /** `m_switchedActive`: the next non-negate command starts a fresh number. */
    var isSwitchedActive = false
        private set

    /** Last `DisplayCallback(from, to)` arguments — unlocalized. */
    var displayFrom = "0"
        private set
    var displayTo = "0"
        private set

    /** Last `SuggestedValueCallback` argument: (unlocalized value, unit). */
    var suggestions: List<Pair<String, ConverterUnit>> = emptyList()
        private set

    /** Incremented on every `MaxDigitsReached` callback. */
    var maxDigitsReachedCount = 0
        private set

    init {
        val ratios = HashMap<Int, Map<Int, ConversionData>>()
        val orders = HashMap<Int, List<ConverterUnit>>()
        for (units in categoryToUnits.values) {
            for (unit in units) {
                val ordered = UnitTables.orderedRatios(unit)
                ratios[unit.id] = ordered.associate { (target, data) -> target.id to data }
                val byId = ordered.associate { (target, _) -> target.id to target }
                orders[unit.id] = MsvcOrder.unorderedMapIterationOrderAfterCopy(ordered.map { it.first.id }).map { byId.getValue(it) }
            }
        }
        ratioMap = ratios
        suggestionOrder = orders
        resetCategoriesAndRatios()
        calculate()
    }

    /** `ResetCategoriesAndRatios`: the first category (Volume), default flags, default units. */
    private fun resetCategoriesAndRatios() {
        isSwitchedActive = false
        currentCategory = categories[0]
        isConversionSource.clear()
        isConversionTarget.clear()
        for (units in categoryToUnits.values) {
            for (unit in units) {
                isConversionSource[unit.id] = UnitTables.isDefaultSource(unit)
                isConversionTarget[unit.id] = UnitTables.isDefaultTarget(unit)
            }
        }
        initializeSelectedUnits()
    }

    /** `SetCurrentCategory`: returns the category's ordered units and the from / to units it opens on. */
    fun setCurrentCategory(input: ConverterCategory): Triple<List<ConverterUnit>, ConverterUnit?, ConverterUnit?> {
        if (currentCategory != input) {
            for (unit in categoryToUnits.getValue(currentCategory)) {
                isConversionSource[unit.id] = unit.id == fromType?.id
                isConversionTarget[unit.id] = unit.id == toType?.id
            }
            currentCategory = input
            if (!currentCategory.supportsNegative && currentDisplay.first() == '-') {
                currentDisplay = currentDisplay.substring(1)
            }
        }
        val newUnitList = categoryToUnits.getValue(input)
        initializeSelectedUnits()
        return Triple(newUnitList, fromType, toType)
    }

    /** `SetCurrentUnitTypes`: a new from unit makes the next digit start a fresh number. */
    fun setCurrentUnitTypes(from: ConverterUnit, to: ConverterUnit) {
        if (fromType?.id != from.id) isSwitchedActive = true
        fromType = from
        toType = to
        calculate()
    }

    /**
     * `SwitchActive`: typing moves to the other field. [newValue] is the view model's unlocalized value of the field
     * being activated. No callback — the values have not changed.
     */
    fun switchActive(newValue: String) {
        val from = fromType
        fromType = toType
        toType = from
        val hasDecimal = currentHasDecimal
        currentHasDecimal = returnHasDecimal
        returnHasDecimal = hasDecimal
        returnDisplay = currentDisplay
        currentDisplay = newValue
        currentHasDecimal = currentDisplay.indexOf('.') >= 0
        isSwitchedActive = true
    }

    /** `SendCommand`. */
    fun sendCommand(command: EngineCommand) {
        var clearFront: Boolean
        var clearBack: Boolean
        if (command != EngineCommand.NEGATE && isSwitchedActive) {
            clearValues()
            isSwitchedActive = false
            clearFront = true
            clearBack = false
        } else {
            clearFront = currentDisplay == "0"
            clearBack = (currentHasDecimal && currentDisplay.length - 1 >= MAXIMUM_DIGITS_ALLOWED) ||
                (!currentHasDecimal && currentDisplay.length >= MAXIMUM_DIGITS_ALLOWED)
        }

        when (command) {
            EngineCommand.ZERO -> currentDisplay += '0'
            EngineCommand.ONE -> currentDisplay += '1'
            EngineCommand.TWO -> currentDisplay += '2'
            EngineCommand.THREE -> currentDisplay += '3'
            EngineCommand.FOUR -> currentDisplay += '4'
            EngineCommand.FIVE -> currentDisplay += '5'
            EngineCommand.SIX -> currentDisplay += '6'
            EngineCommand.SEVEN -> currentDisplay += '7'
            EngineCommand.EIGHT -> currentDisplay += '8'
            EngineCommand.NINE -> currentDisplay += '9'
            EngineCommand.DECIMAL -> {
                clearFront = false
                clearBack = false
                if (!currentHasDecimal) {
                    currentDisplay += '.'
                    currentHasDecimal = true
                }
            }
            EngineCommand.BACKSPACE -> {
                clearFront = false
                clearBack = false
                if ((currentDisplay.first() != '-' && currentDisplay.length > 1) || currentDisplay.length > 2) {
                    if (currentDisplay.last() == '.') currentHasDecimal = false
                    currentDisplay = currentDisplay.substring(0, currentDisplay.length - 1)
                } else {
                    currentDisplay = "0"
                    currentHasDecimal = false
                }
            }
            EngineCommand.NEGATE -> {
                clearFront = false
                clearBack = false
                if (currentCategory.supportsNegative) {
                    currentDisplay = if (currentDisplay.first() == '-') currentDisplay.substring(1) else "-$currentDisplay"
                }
            }
            EngineCommand.CLEAR -> {
                clearFront = false
                clearBack = false
                clearValues()
            }
        }

        if (clearFront) currentDisplay = currentDisplay.substring(1)
        if (clearBack) {
            currentDisplay = currentDisplay.substring(0, currentDisplay.length - 1)
            maxDigitsReachedCount++
        }
        calculate()
    }

    private fun clearValues() {
        currentHasDecimal = false
        returnHasDecimal = false
        currentDisplay = "0"
    }

    /** `InitializeSelectedUnits`, including its fallback to the empty unit when only one of the pair is valid. */
    private fun initializeSelectedUnits() {
        val curUnits = categoryToUnits.getValue(currentCategory)
        val isFromUnitValid = fromType != null && curUnits.any { it.id == fromType?.id }
        val isToUnitValid = toType != null && curUnits.any { it.id == toType?.id }
        if (isFromUnitValid && isToUnitValid) return

        var conversionSourceSet = false
        var conversionTargetSet = false
        for (cur in curUnits) {
            if (!conversionSourceSet && isConversionSource.getValue(cur.id) && !isFromUnitValid) {
                fromType = cur
                conversionSourceSet = true
            }
            if (!conversionTargetSet && isConversionTarget.getValue(cur.id) && !isToUnitValid) {
                toType = cur
                conversionTargetSet = true
            }
            if (conversionSourceSet && conversionTargetSet) return
        }
        fromType = null
        toType = null
    }

    /** `Calculate`, then `UpdateViewModel`. */
    fun calculate() {
        val from = fromType
        val to = toType
        if (from == null || to == null) {
            returnDisplay = trimTrailingZeros(currentDisplay)
            returnHasDecimal = currentHasDecimal
            updateViewModel()
            return
        }
        val conversion = ratioMap.getValue(from.id).getValue(to.id)
        if (conversion.ratio == 1.0 && conversion.offset == 0.0) {
            returnDisplay = trimTrailingZeros(currentDisplay)
            returnHasDecimal = currentHasDecimal
        } else {
            val currentValue = currentDisplay.toDouble()
            val returnValue = convert(currentValue, conversion)
            val numPreDecimal = wholeNumberDigits(returnValue)
            if (numPreDecimal > MAXIMUM_DIGITS_ALLOWED || (returnValue != 0.0 && Math.abs(returnValue) < MINIMUM_DECIMAL_ALLOWED)) {
                returnDisplay = scientific(returnValue)
            } else {
                val currentNumberSignificantDigits = numberDigits(currentDisplay)
                val precision = if (Math.abs(returnValue) < OPTIMAL_DECIMAL_ALLOWED) {
                    MAXIMUM_DIGITS_ALLOWED
                } else {
                    // Fewer digits are needed after the point when the integer part is long.
                    val digits = maxOf(OPTIMAL_DIGITS_ALLOWED, minOf(MAXIMUM_DIGITS_ALLOWED, currentNumberSignificantDigits))
                    if (digits > numPreDecimal) digits - numPreDecimal else 0
                }
                returnDisplay = trimTrailingZeros(fixed(returnValue, precision))
            }
            returnHasDecimal = returnDisplay.indexOf('.') >= 0
        }
        updateViewModel()
    }

    private fun updateViewModel() {
        displayFrom = currentDisplay
        displayTo = returnDisplay
        suggestions = calculateSuggested()
    }

    private fun convert(value: Double, data: ConversionData): Double =
        if (data.offsetFirst) (value + data.offset) * data.ratio else (value * data.ratio) + data.offset

    private class Intermediate(val magnitude: Double, val value: Double, val type: ConverterUnit)

    /** `CalculateSuggested`: every other unit's value, nearest magnitude first, then the best whimsical one. */
    private fun calculateSuggested(): List<Pair<String, ConverterUnit>> {
        val from = fromType ?: return emptyList()
        val to = toType
        val ratios = ratioMap.getValue(from.id)
        val intermediate = ArrayList<Intermediate>()
        val intermediateWhimsical = ArrayList<Intermediate>()
        for (unit in suggestionOrder.getValue(from.id)) {
            if (unit.id != from.id && unit.id != to?.id) {
                val converted = convert(currentDisplay.toDouble(), ratios.getValue(unit.id))
                val entry = Intermediate(StrictMath.log10(converted), converted, unit)
                if (unit.isWhimsical) intermediateWhimsical += entry else intermediate += entry
            }
        }

        val byMagnitude = { first: Intermediate, second: Intermediate ->
            if (Math.abs(first.magnitude) == Math.abs(second.magnitude)) {
                first.magnitude > second.magnitude
            } else {
                Math.abs(first.magnitude) < Math.abs(second.magnitude)
            }
        }

        val result = ArrayList<Pair<String, ConverterUnit>>()
        MsvcOrder.insertionSort(intermediate, byMagnitude)
        for (entry in intermediate) {
            val rounded = roundSuggestion(entry.value)
            if (rounded.toDouble() != 0.0 || currentCategory.supportsNegative) {
                result += trimTrailingZeros(rounded) to entry.type
            }
        }

        MsvcOrder.insertionSort(intermediateWhimsical, byMagnitude)
        val whimsical = ArrayList<Pair<String, ConverterUnit>>()
        for (entry in intermediateWhimsical) {
            val rounded = roundSuggestion(entry.value)
            if (rounded.toDouble() != 0.0) whimsical += trimTrailingZeros(rounded) to entry.type
        }
        // "Pickup the 'best' whimsical value - currently the first one".
        if (whimsical.isNotEmpty()) result += whimsical.first()
        return result
    }

    private fun roundSuggestion(value: Double): String = when {
        Math.abs(value) < 100 -> fixed(value, 2)
        Math.abs(value) < 1000 -> fixed(value, 1)
        else -> fixed(value, 0)
    }

    /**
     * `SaveUserPreferences`: "from|to|category|", each a ';'-terminated list of quoted fields. The units are the ones
     * the view model passed to `SetCurrentUnitTypes`, whose source / target / whimsical flags are unset (0).
     */
    fun saveUserPreferences(): String {
        val from = fromType ?: return ""
        val to = toType ?: return ""
        return unitToString(from) + "|" + unitToString(to) + "|" + categoryToString(currentCategory) + "|"
    }

    /**
     * `RestoreUserPreferences`. Unlike the C++, a string that does not parse, names another category or names units
     * outside its category is ignored as a whole, so a damaged setting cannot leave the converter without units.
     */
    fun restoreUserPreferences(userPreferences: String): Boolean {
        if (userPreferences.isEmpty()) return false
        val outer = stringToVector(userPreferences, "|")
        if (outer.size != 3) return false
        val fromTokens = stringToVector(outer[0], ";")
        val toTokens = stringToVector(outer[1], ";")
        val categoryTokens = stringToVector(outer[2], ";")
        if (fromTokens.size != SERIALIZED_UNIT_TOKEN_COUNT || toTokens.size != SERIALIZED_UNIT_TOKEN_COUNT ||
            categoryTokens.size != SERIALIZED_CATEGORY_TOKEN_COUNT
        ) {
            return false
        }
        val category = unquote(categoryTokens[0]).toIntOrNull()?.let { ConverterCategory.fromId(it) } ?: return false
        val units = categoryToUnits.getValue(category)
        val from = unquote(fromTokens[0]).toIntOrNull()?.let { id -> units.firstOrNull { it.id == id } } ?: return false
        val to = unquote(toTokens[0]).toIntOrNull()?.let { id -> units.firstOrNull { it.id == id } } ?: return false
        currentCategory = category
        fromType = from
        toType = to
        return true
    }

    private fun unitToString(u: ConverterUnit): String =
        quote(u.id.toString()) + ";" + quote(u.name) + ";" + quote(u.abbreviation) + ";0;0;0;"

    private fun categoryToString(c: ConverterCategory): String =
        quote(c.id.toString()) + ";" + quote(if (c.supportsNegative) "1" else "0") + ";" + quote(c.label) + ";"

    companion object {
        const val MAXIMUM_DIGITS_ALLOWED = 15
        const val OPTIMAL_DIGITS_ALLOWED = 7
        const val OPTIMAL_DECIMAL_ALLOWED = 1e-6 // pow(10, -1 * (OPTIMALDIGITSALLOWED - 1))
        const val MINIMUM_DECIMAL_ALLOWED = 1e-14 // pow(10, -1 * (MAXIMUMDIGITSALLOWED - 1))
        private const val SERIALIZED_CATEGORY_TOKEN_COUNT = 3
        private const val SERIALIZED_UNIT_TOKEN_COUNT = 6

        private val quoteConversions = mapOf(
            '|' to "{p}", '[' to "{lc}", ']' to "{rc}", ':' to "{co}", ',' to "{cm}", ';' to "{sc}", '{' to "{lb}", '}' to "{rb}",
        )
        private val unquoteConversions = quoteConversions.entries.associate { (k, v) -> v to k }

        /** `StringToVector` without the remainder: the tokens before each delimiter. */
        fun stringToVector(w: String, delimiter: String): List<String> {
            val tokens = ArrayList<String>()
            var start = 0
            var index = w.indexOf(delimiter)
            while (index >= 0) {
                tokens += w.substring(start, index)
                start = index + delimiter.length
                index = w.indexOf(delimiter, start)
            }
            return tokens
        }

        /** `Quote`: escape the delimiter characters. */
        fun quote(s: String): String = buildString { for (ch in s) append(quoteConversions[ch] ?: ch.toString()) }

        /** `Unquote`: undo [quote]; an unterminated escape ends the string, an unknown one yields U+0000 as in C++. */
        fun unquote(s: String): String = buildString {
            var i = 0
            while (i < s.length) {
                if (s[i] == '{') {
                    val end = s.indexOf('}', i)
                    if (end < 0) break
                    append(unquoteConversions[s.substring(i, end + 1)] ?: '\u0000')
                    i = end + 1
                } else {
                    append(s[i])
                    i++
                }
            }
        }
    }
}
