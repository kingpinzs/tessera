package app.tileshell.calc.convert

/** The converter pad's keys, by the names the app and the harness use. */
enum class ConverterKey(val keyName: String, internal val command: EngineCommand) {
    D0("0", EngineCommand.ZERO),
    D1("1", EngineCommand.ONE),
    D2("2", EngineCommand.TWO),
    D3("3", EngineCommand.THREE),
    D4("4", EngineCommand.FOUR),
    D5("5", EngineCommand.FIVE),
    D6("6", EngineCommand.SIX),
    D7("7", EngineCommand.SEVEN),
    D8("8", EngineCommand.EIGHT),
    D9("9", EngineCommand.NINE),
    DECIMAL("decimal", EngineCommand.DECIMAL),
    BACKSPACE("backspace", EngineCommand.BACKSPACE),
    CLEAR("clear", EngineCommand.CLEAR),
    NEGATE("negate", EngineCommand.NEGATE);

    companion object {
        /** The key called [name] ("0"–"9", "decimal", "backspace", "clear", "negate"), or null. */
        fun fromName(name: String): ConverterKey? = entries.firstOrNull { it.keyName == name }
    }
}

/** One "About equal to" item: [value] as displayed (en-US grouping) and its [unit] (shown by its abbreviation). */
data class SupplementaryResult(val value: String, val unit: ConverterUnit)

/**
 * The Converter page's state: a port of the non-currency parts of microsoft/calculator@4fd3fc5
 * src/Calculator.ViewModels/UnitConverterViewModel.cs driving [UnitConverterEngine].
 *
 * The page has two value rows, [value1] over [unit1] and [value2] over [unit2]. One of them is the source being typed
 * into ([value1Active]); [switchActive] moves typing to the other row (Windows' tap on the inactive value), and the
 * from / to accessors follow it. Every value string is formatted as Windows shows it in en-US ("523,535",
 * "325,338.7", "3." while a decimal is being typed).
 *
 * Persist [saveUserPreferences] after each category or unit change (Windows writes it in `OnUnitChanged`) and pass it
 * back to the constructor to reopen on the last-used category and units; with no saved string the Converter opens
 * on Volume, Milliliters → Teaspoons (US).
 */
class ConverterState(userPreferences: String? = null) {
    private val engine = UnitConverterEngine()

    /** The twelve categories in pane order. */
    val categories: List<ConverterCategory> = ConverterCategory.entries

    var category: ConverterCategory = ConverterCategory.VOLUME
        private set

    /** The unit pickers' list for [category] (whimsical units excluded). */
    var units: List<ConverterUnit> = emptyList()
        private set

    /** Top row's unit. */
    var unit1: ConverterUnit
        private set

    /** Bottom row's unit. */
    var unit2: ConverterUnit
        private set

    /** Top row's value as displayed. */
    var value1: String = "0"
        private set

    /** Bottom row's value as displayed. */
    var value2: String = "0"
        private set

    /** True while the top row is the source being typed into. */
    var value1Active: Boolean = true
        private set

    /** "About equal to": the nearest-magnitude conversions to the other units, then at most one whimsical unit. */
    var supplementaryResults: List<SupplementaryResult> = emptyList()
        private set

    /** Increments each time a digit was refused because the value already has 15 digits (Windows announces it). */
    val maxDigitsReachedCount: Int get() = engine.maxDigitsReachedCount

    private var valueFromUnlocalized = "0"
    private var valueToUnlocalized = "0"

    init {
        if (userPreferences != null) engine.restoreUserPreferences(userPreferences)
        category = engine.currentCategory
        val first = UnitTables.pickerUnits(category).first()
        unit1 = first
        unit2 = first
        resetCategory()
    }

    /** The unit being converted from (the active row's unit). */
    val fromUnit: ConverterUnit get() = if (value1Active) unit1 else unit2

    /** The unit being converted to. */
    val toUnit: ConverterUnit get() = if (value1Active) unit2 else unit1

    /** The value being typed, as displayed. */
    val fromText: String get() = if (value1Active) value1 else value2

    /** The converted value, as displayed. */
    val toText: String get() = if (value1Active) value2 else value1

    /** Opens [newCategory] on the units it last used this session, or its defaults. */
    fun selectCategory(newCategory: ConverterCategory) {
        if (newCategory == category) return
        category = newCategory
        resetCategory()
    }

    /** Picks the top row's unit. */
    fun selectUnit1(unit: ConverterUnit) {
        require(unit in units) { "${unit.name} is not a ${category.label} unit" }
        if (unit == unit1) return
        unit1 = unit
        onUnitChanged()
    }

    /** Picks the bottom row's unit. */
    fun selectUnit2(unit: ConverterUnit) {
        require(unit in units) { "${unit.name} is not a ${category.label} unit" }
        if (unit == unit2) return
        unit2 = unit
        onUnitChanged()
    }

    /** Presses a pad key. `negate` does nothing in a category without negative values. */
    fun press(key: ConverterKey) {
        engine.sendCommand(key.command)
        updateFromEngine()
    }

    /** Presses the key called [keyName] ("0"–"9", "decimal", "backspace", "clear", "negate"). */
    fun press(keyName: String) {
        press(requireNotNull(ConverterKey.fromName(keyName)) { "unknown converter key '$keyName'" })
    }

    /**
     * Makes the other row the one being typed into (`OnSwitchActive`). Both rows keep their text until the next key;
     * the next digit, decimal, backspace or clear starts a new number, while `negate` changes the sign of the value
     * the row already shows.
     */
    fun switchActive() {
        value1Active = !value1Active
        val from = valueFromUnlocalized
        valueFromUnlocalized = valueToUnlocalized
        valueToUnlocalized = from
        engine.switchActive(valueFromUnlocalized)
    }

    /** Windows' `SaveUserPreferences` string: the from unit, the to unit and the category. */
    fun saveUserPreferences(): String = engine.saveUserPreferences()

    private fun resetCategory() {
        val (_, from, to) = engine.setCurrentCategory(category)
        units = UnitTables.pickerUnits(category)
        setUnitFrom(findUnitInList(from))
        setUnitTo(findUnitInList(to))
        onUnitChanged()
    }

    private fun findUnitInList(target: ConverterUnit?): ConverterUnit = units.firstOrNull { it.id == target?.id } ?: units[0]

    private fun setUnitFrom(unit: ConverterUnit) {
        if (value1Active) unit1 = unit else unit2 = unit
    }

    private fun setUnitTo(unit: ConverterUnit) {
        if (value1Active) unit2 = unit else unit1 = unit
    }

    private fun onUnitChanged() {
        engine.setCurrentUnitTypes(fromUnit, toUnit)
        updateFromEngine()
    }

    private fun updateFromEngine() {
        valueFromUnlocalized = engine.displayFrom
        valueToUnlocalized = engine.displayTo
        val fromDisplayed = ConverterNumberFormat.localize(engine.displayFrom, true)
        val toDisplayed = ConverterNumberFormat.localize(engine.displayTo, true)
        if (value1Active) {
            value1 = fromDisplayed
            value2 = toDisplayed
        } else {
            value2 = fromDisplayed
            value1 = toDisplayed
        }
        val results = ArrayList<SupplementaryResult>()
        val whimsicals = ArrayList<SupplementaryResult>()
        for ((value, unit) in engine.suggestions) {
            val result = SupplementaryResult(ConverterNumberFormat.localize(value, false), unit)
            if (unit.isWhimsical) whimsicals += result else results += result
        }
        if (whimsicals.isNotEmpty()) results += whimsicals[0]
        supplementaryResults = results
    }
}
