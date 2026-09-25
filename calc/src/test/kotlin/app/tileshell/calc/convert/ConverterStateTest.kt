package app.tileshell.calc.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Converter behaviour against expectations computed on the host (Python, IEEE doubles, tables parsed from
 * microsoft/calculator@4fd3fc5 UnitConverterDataLoader.cs + Resources.resw, algorithm from UnitConverter.cpp) — never
 * from this code. W10M-measured strings (r11/calculator.md 5.6, 5.9, 5.10) are marked where they apply.
 */
class ConverterStateTest {

    private fun unit(category: ConverterCategory, name: String): ConverterUnit =
        UnitTables.pickerUnits(category).single { it.name == name }

    /** A fresh converter on [category], converting [from] → [to], with [typed] entered on the pad. */
    private fun convert(category: ConverterCategory, from: String, to: String, typed: String): ConverterState {
        val state = ConverterState()
        state.selectCategory(category)
        state.selectUnit1(unit(category, from))
        state.selectUnit2(unit(category, to))
        type(state, typed)
        return state
    }

    private fun type(state: ConverterState, typed: String) {
        val negative = typed.startsWith("-")
        for (ch in typed.removePrefix("-")) {
            state.press(if (ch == '.') "decimal" else ch.toString())
        }
        if (negative) state.press("negate")
    }

    private fun assertConversion(
        category: ConverterCategory, from: String, to: String, typed: String, fromText: String, toText: String,
    ): ConverterState {
        val state = convert(category, from, to, typed)
        assertEquals("from text, $typed $from → $to", fromText, state.fromText)
        assertEquals("to text, $typed $from → $to", toText, state.toText)
        return state
    }

    private fun suggestions(state: ConverterState): List<String> =
        state.supplementaryResults.map { "${it.value} ${it.unit.abbreviation}" }

    // ---- The phase's two named conversions (E12) ----

    @Test fun oneMileIsExactly1point609344Kilometers() {
        assertConversion(ConverterCategory.LENGTH, "Miles", "Kilometers", "1", "1", "1.609344")
    }

    @Test fun hundredCelsiusIs212Fahrenheit() {
        assertConversion(ConverterCategory.TEMPERATURE, "Celsius", "Fahrenheit", "100", "100", "212")
    }

    // ---- Every category, at least two values ----

    @Test fun volume() {
        assertConversion(ConverterCategory.VOLUME, "Milliliters", "Teaspoons (US)", "1", "1", "0.202884")
        assertConversion(ConverterCategory.VOLUME, "Liters", "Gallons (US)", "10", "10", "2.641721")
        assertConversion(ConverterCategory.VOLUME, "Cups (US)", "Milliliters", "2.5", "2.5", "591.4706")
    }

    @Test fun length() {
        assertConversion(ConverterCategory.LENGTH, "Centimeters", "Inches", "100", "100", "39.37008")
        assertConversion(ConverterCategory.LENGTH, "Kilometers", "Miles", "123456789", "123,456,789", "76,712,492.2")
        assertConversion(ConverterCategory.LENGTH, "Angstroms", "Kilometers", "1", "1", "0.0000000000001")
    }

    @Test fun weightAndMass() {
        assertConversion(ConverterCategory.WEIGHT, "Kilograms", "Pounds", "1", "1", "2.204623")
        assertConversion(ConverterCategory.WEIGHT, "Pounds", "Kilograms", "150", "150", "68.03886")
    }

    @Test fun temperatureOffsets() {
        assertConversion(ConverterCategory.TEMPERATURE, "Celsius", "Fahrenheit", "-40", "-40", "-40")
        assertConversion(ConverterCategory.TEMPERATURE, "Fahrenheit", "Celsius", "98.6", "98.6", "37")
        assertConversion(ConverterCategory.TEMPERATURE, "Kelvin", "Celsius", "0", "0", "-273.15")
        assertConversion(ConverterCategory.TEMPERATURE, "Celsius", "Kelvin", "-273.15", "-273.15", "0")
    }

    @Test fun energy() {
        assertConversion(ConverterCategory.ENERGY, "Joules", "Food calories", "4184", "4,184", "1")
        assertConversion(ConverterCategory.ENERGY, "Kilowatt-hours", "Joules", "1", "1", "3,600,000")
        // Below 1e-14 the result switches to printf's %e, shown with its six decimals.
        assertConversion(ConverterCategory.ENERGY, "Electron volts", "Kilowatt-hours", "1", "1", "4.450491e-26")
    }

    @Test fun area() {
        assertConversion(ConverterCategory.AREA, "Square meters", "Square feet", "1", "1", "10.76391")
        assertConversion(ConverterCategory.AREA, "Acres", "Hectares", "1", "1", "0.404686")
    }

    @Test fun speedAtTheW10mCapturesInputs() {
        // r11/calculator.md 5.6 (C4) and 5.10 (C5) show 325.0062 and 325,338.7, from Windows' mph of 44.7 cm/s; the
        // shell's mph is exactly 44.704 cm/s (2026-09-24), so 523 km/h is 324.9771 mph.
        assertConversion(ConverterCategory.SPEED, "Kilometers per hour", "Miles per hour", "523", "523", "324.9771")
        assertConversion(ConverterCategory.SPEED, "Kilometers per hour", "Miles per hour", "523535", "523,535", "325,309.6")
    }

    @Test fun time() {
        assertConversion(ConverterCategory.TIME, "Hours", "Minutes", "1.5", "1.5", "90")
        assertConversion(ConverterCategory.TIME, "Days", "Hours", "7", "7", "168")
    }

    @Test fun power() {
        assertConversion(ConverterCategory.POWER, "Kilowatts", "Horsepower (US)", "1", "1", "1.341022")
        assertConversion(ConverterCategory.POWER, "Kilowatts", "Horsepower (US)", "-1", "-1", "-1.341022")
    }

    @Test fun data() {
        assertConversion(ConverterCategory.DATA, "Gigabytes", "Megabytes", "1", "1", "1,000")
        assertConversion(ConverterCategory.DATA, "Bits", "Bytes", "1", "1", "0.125")
        assertConversion(ConverterCategory.DATA, "Kibibytes", "Bytes", "1", "1", "1,024")
        // Past 15 integer digits the result switches to printf's %e.
        assertConversion(ConverterCategory.DATA, "Yottabytes", "Bits", "1", "1", "8.000000e+24")
        assertConversion(ConverterCategory.DATA, "Exabytes", "Bits", "1", "1", "8.000000e+18")
    }

    @Test fun pressure() {
        assertConversion(ConverterCategory.PRESSURE, "Atmospheres", "Bars", "1", "1", "1.01325")
        assertConversion(ConverterCategory.PRESSURE, "Pounds per square inch", "Kilopascals", "30", "30", "206.8427")
    }

    @Test fun angle() {
        assertConversion(ConverterCategory.ANGLE, "Degrees", "Radians", "180", "180", "3.141593")
        assertConversion(ConverterCategory.ANGLE, "Gradians", "Degrees", "100", "100", "90")
    }

    // ---- Value entry and display formatting ----

    @Test fun opensOnVolumeMillilitersToTeaspoonsWithZeros() {
        val state = ConverterState()
        assertEquals(ConverterCategory.VOLUME, state.category)
        assertEquals("Milliliters", state.unit1.name)
        assertEquals("Teaspoons (US)", state.unit2.name)
        assertEquals("0", state.value1)
        assertEquals("0", state.value2)
        assertTrue(state.value1Active)
        assertTrue(state.supplementaryResults.isEmpty())
    }

    @Test fun aTrailingDecimalPointShowsWhileTyping() {
        val state = convert(ConverterCategory.VOLUME, "Milliliters", "Teaspoons (US)", "3.")
        assertEquals("3.", state.fromText)
        assertEquals("0.608652", state.toText)
    }

    @Test fun typedTrailingZerosStayInTheSourceButNotTheResult() {
        val state = convert(ConverterCategory.VOLUME, "Milliliters", "Teaspoons (US)", "2.50")
        assertEquals("2.50", state.fromText)
        assertEquals("0.50721", state.toText)
        // Same-ratio units copy the typed value, trimmed.
        val same = convert(ConverterCategory.VOLUME, "Milliliters", "Cubic centimeters", "2.50")
        assertEquals("2.5", same.toText)
    }

    @Test fun fifteenDigitsIsTheLimit() {
        val state = convert(ConverterCategory.VOLUME, "Milliliters", "Teaspoons (US)", "123456789012345")
        assertEquals(0, state.maxDigitsReachedCount)
        state.press("6")
        assertEquals(1, state.maxDigitsReachedCount)
        assertEquals("123,456,789,012,345", state.fromText)
        assertEquals("25,047,423,998,160.4", state.toText)

        val decimal = convert(ConverterCategory.VOLUME, "Milliliters", "Teaspoons (US)", "1.23456789012345")
        decimal.press("6")
        assertEquals(1, decimal.maxDigitsReachedCount)
        assertEquals("1.23456789012345", decimal.fromText)
        assertEquals("0.2504742399816", decimal.toText)
    }

    @Test fun theMinusSignCountsTowardTheLimit() {
        val state = convert(ConverterCategory.TEMPERATURE, "Celsius", "Fahrenheit", "-12345678901234")
        state.press("5")
        assertEquals(1, state.maxDigitsReachedCount)
        assertEquals("-12,345,678,901,234", state.fromText)
        assertEquals("-22,222,222,022,189", state.toText)
    }

    @Test fun leadingZerosAreReplaced() {
        val state = convert(ConverterCategory.VOLUME, "Milliliters", "Teaspoons (US)", "0012")
        assertEquals("12", state.fromText)
        assertEquals("2.43461", state.toText)
    }

    @Test fun backspaceAndClear() {
        val state = convert(ConverterCategory.VOLUME, "Milliliters", "Teaspoons (US)", "12.5")
        assertEquals("2.536052", state.toText)
        state.press("backspace")
        assertEquals("12.", state.fromText)
        assertEquals("2.43461", state.toText)
        state.press("backspace")
        assertEquals("12", state.fromText)
        state.press("decimal")
        assertEquals("12.", state.fromText) // the decimal flag was cleared by the backspace over '.'
        state.press("clear")
        assertEquals("0", state.fromText)
        assertEquals("0", state.toText)

        val negative = convert(ConverterCategory.TEMPERATURE, "Celsius", "Fahrenheit", "-5")
        negative.press("backspace")
        assertEquals("0", negative.fromText)
        assertEquals("32", negative.toText)
    }

    @Test fun negateOnlyWhereTheCategoryAllowsIt() {
        val length = convert(ConverterCategory.LENGTH, "Miles", "Kilometers", "5")
        length.press("negate")
        assertEquals("5", length.fromText)

        val temperature = convert(ConverterCategory.TEMPERATURE, "Celsius", "Fahrenheit", "")
        temperature.press("negate")
        assertEquals("-0", temperature.fromText)
        assertEquals("32", temperature.toText)
        temperature.press("5") // "-0" is not "0", so the zero stays inside: "-05", shown as -5
        assertEquals("-5", temperature.fromText)
        assertEquals("23", temperature.toText)
        assertEquals(listOf("268.1 K"), suggestions(temperature))
    }

    @Test fun switchingTheActiveRowTypesIntoTheOtherUnit() {
        val state = convert(ConverterCategory.SPEED, "Kilometers per hour", "Miles per hour", "523")
        state.switchActive()
        assertFalse(state.value1Active)
        // Nothing changes until the next key.
        assertEquals("523", state.value1)
        assertEquals("324.9771", state.value2)
        assertEquals("Miles per hour", state.fromUnit.name)
        state.press("1")
        state.press("0")
        state.press("0")
        assertEquals("100", state.value2)
        assertEquals("160.9344", state.value1)
        assertEquals(listOf("0.13 M", "44.7 m/s", "86.9 kn", "146.7 ft/s", "4,470 cm/s", "2.22 horses"), suggestions(state))
    }

    @Test fun negateAfterASwitchKeepsTheShownValue() {
        val state = convert(ConverterCategory.TEMPERATURE, "Celsius", "Fahrenheit", "100")
        state.switchActive()
        state.press("negate")
        assertEquals("-212", state.value2)
        assertEquals("-135.5556", state.value1)
        assertEquals(listOf("137.6 K"), suggestions(state))
    }

    @Test fun changingTheFromUnitStartsTheNextNumberFresh() {
        val state = convert(ConverterCategory.LENGTH, "Centimeters", "Inches", "12")
        assertEquals("4.724409", state.toText)
        state.selectUnit1(unit(ConverterCategory.LENGTH, "Meters"))
        assertEquals("12", state.fromText)
        assertEquals("472.4409", state.toText)
        state.press("3")
        assertEquals("3", state.fromText)
        assertEquals("118.1102", state.toText)
    }

    @Test fun categoryChangeKeepsTheValueAndDropsANegativeSign() {
        val state = ConverterState()
        state.selectCategory(ConverterCategory.LENGTH)
        type(state, "5")
        state.selectCategory(ConverterCategory.WEIGHT)
        assertEquals("Kilograms", state.fromUnit.name)
        assertEquals("Pounds", state.toUnit.name)
        assertEquals("5", state.fromText)
        assertEquals("11.02311", state.toText)

        val temperature = ConverterState()
        temperature.selectCategory(ConverterCategory.TEMPERATURE)
        type(temperature, "-40")
        temperature.selectCategory(ConverterCategory.LENGTH)
        assertEquals("40", temperature.fromText)
        assertEquals("15.74803", temperature.toText)
    }

    @Test fun aCategoryReopensOnItsLastUnits() {
        val state = ConverterState()
        state.selectCategory(ConverterCategory.LENGTH)
        state.selectUnit1(unit(ConverterCategory.LENGTH, "Miles"))
        state.selectUnit2(unit(ConverterCategory.LENGTH, "Kilometers"))
        state.selectCategory(ConverterCategory.WEIGHT)
        state.selectCategory(ConverterCategory.LENGTH)
        assertEquals("Miles", state.unit1.name)
        assertEquals("Kilometers", state.unit2.name)
    }

    // ---- "About equal to" ----

    @Test fun suggestionsAtTheW10mCapturesInputs() {
        // C4 "0.43 M · 145.3 m/s · 282.4 kn · ✈ 0.59 jets" and C5 "427.3 M · 145,426 m/s · ✈ 591.5 jets" are the
        // leading items that fit the phone's one line, with the whimsical item kept last (r11/calculator.md 5.9).
        // C5's Mach comes from Windows' 340.3 m/s; the shell's is ISA's 340.294 m/s (2026-09-24), so 427.4 M.
        val c4 = convert(ConverterCategory.SPEED, "Kilometers per hour", "Miles per hour", "523")
        assertEquals(listOf("0.43 M", "145.3 m/s", "282.4 kn", "476.6 ft/s", "14,528 cm/s", "0.59 jets"), suggestions(c4))
        val c5 = convert(ConverterCategory.SPEED, "Kilometers per hour", "Miles per hour", "523535")
        assertEquals(listOf("427.4 M", "145,426 m/s", "282,686 kn", "477,121 ft/s", "14,542,639 cm/s", "591.5 jets"), suggestions(c5))
        assertTrue(c5.supplementaryResults.last().unit.isWhimsical)
    }

    @Test fun suggestionsTieInWindowsHashOrder() {
        // Milliliters and Cubic centimeters share a factor; MSVC's map order puts Milliliters first.
        val volume = convert(ConverterCategory.VOLUME, "Liters", "Gallons (US)", "10")
        assertEquals(
            listOf(
                "2.2 gal (UK)", "0.35 ft³", "8.8 qt (UK)", "10.57 qt (US)", "17.6 pt (UK)", "21.13 pt (US)",
                "42.27 cup (US)", "0.01 yd³", "0.01 m³", "338.1 fl oz (US)", "352 fl oz (UK)", "563.1 tbsp. (UK)",
                "610.2 in³", "676.3 tbsp. (US)", "1,689 tsp. (UK)", "2,029 tsp. (US)", "10,000 mL", "10,000 cm³",
                "0.03 bathtubs",
            ),
            suggestions(volume),
        )
        // Negative power values have NaN magnitudes: map order, and "horses" is the first whimsical in it.
        val negative = convert(ConverterCategory.POWER, "Kilowatts", "Horsepower (US)", "-1")
        assertEquals(listOf("-1,000 W", "-44,254 ft•lb/min", "-56.87 BTU/min", "-1.34 horses"), suggestions(negative))
        val zero = convert(ConverterCategory.POWER, "Kilowatts", "Horsepower (US)", "0")
        assertEquals(listOf("0 W", "0 ft•lb/min", "0 BTU/min"), suggestions(zero))
    }

    @Test fun suggestionsDropZerosInPositiveOnlyCategories() {
        val tiny = convert(ConverterCategory.ENERGY, "Electron volts", "Kilowatt-hours", "1")
        assertTrue(tiny.supplementaryResults.isEmpty())
        val bits = convert(ConverterCategory.DATA, "Bits", "Bytes", "1")
        assertEquals(listOf("0.25 nybl"), suggestions(bits))
        val kelvin = convert(ConverterCategory.TEMPERATURE, "Kelvin", "Celsius", "0")
        assertEquals(listOf("-459.7 °F"), suggestions(kelvin))
    }

    @Test fun suggestionsAcrossCategories() {
        assertEquals(
            listOf("0.87 nmi", "1,609 m", "1,760 yd", "5,280 ft", "63,360 in", "160,934 cm", "1,609,344 mm",
                "1,609,344,000 µm", "1,609,344,000,000 nm", "16,093,440,000,000 A", "21.18 jumbo jets"),
            suggestions(convert(ConverterCategory.LENGTH, "Miles", "Kilometers", "1")),
        )
        assertEquals(
            listOf("0.16 st", "10 hg", "35.27 oz", "100 dag", "1,000 g", "5,000 CD", "10,000 dg", "100,000 cg",
                "1,000,000 mg", "2.31 soccer balls"),
            suggestions(convert(ConverterCategory.WEIGHT, "Kilograms", "Pounds", "1")),
        )
        assertEquals(
            listOf("0.93 GiB", "7.45 Gi", "8 Gb", "0.01 Tb", "0.01 Ti", "953.7 MiB", "7,629 Mi", "8,000 Mb",
                "976,562 KiB", "1,000,000 KB", "7,812,500 Ki", "8,000,000 Kb", "1,000,000,000 B", "2,000,000,000 nybl",
                "8,000,000,000 b", "1.43 CDs"),
            suggestions(convert(ConverterCategory.DATA, "Gigabytes", "Megabytes", "1")),
        )
        assertEquals(
            listOf("3.97 BTU", "4.18 kJ", "1,000 cal", "3,086 ft•lb", "26,114,473,967,543,832,805,376 eV", "0.46 batteries"),
            suggestions(convert(ConverterCategory.ENERGY, "Joules", "Food calories", "4184")),
        )
        assertEquals(listOf("14.7 psi", "101.3 kPa", "760 mmHg", "101,325 Pa"),
            suggestions(convert(ConverterCategory.PRESSURE, "Atmospheres", "Bars", "1")))
        assertEquals(listOf("200 grad"), suggestions(convert(ConverterCategory.ANGLE, "Degrees", "Radians", "180")))
        assertEquals(listOf("0.06 d", "0.01 wk", "5,400 s", "5,400,000 ms", "5,400,000,000 µs"),
            suggestions(convert(ConverterCategory.TIME, "Hours", "Minutes", "1.5")))
        assertEquals(listOf("1.2 yd²", "1,550 in²", "10,000 cm²", "1,000,000 mm²", "16.58 sheets of paper"),
            suggestions(convert(ConverterCategory.AREA, "Square meters", "Square feet", "1")))
    }

    // ---- Last-used category ----

    @Test fun preferencesUseWindowsFormatAndRestoreTheCategory() {
        val fresh = ConverterState()
        assertEquals("75;Milliliters;mL;0;0;0;|79;Teaspoons (US);tsp. (US);0;0;0;|4;0;Volume;|", fresh.saveUserPreferences())

        val state = ConverterState()
        state.selectCategory(ConverterCategory.SPEED)
        state.selectUnit1(unit(ConverterCategory.SPEED, "Miles per hour"))
        state.selectUnit2(unit(ConverterCategory.SPEED, "Knots"))
        val saved = state.saveUserPreferences()
        assertEquals("63;Miles per hour;mph;0;0;0;|60;Knots;kn;0;0;0;|10;0;Speed;|", saved)

        val reopened = ConverterState(saved)
        assertEquals(ConverterCategory.SPEED, reopened.category)
        assertEquals("Miles per hour", reopened.unit1.name)
        assertEquals("Knots", reopened.unit2.name)
        assertEquals("0", reopened.value1)

        val weight = ConverterState()
        weight.selectCategory(ConverterCategory.WEIGHT)
        assertEquals("88;Kilograms;kg;0;0;0;|92;Pounds;lb;0;0;0;|6;0;Weight and Mass;|", weight.saveUserPreferences())
        assertEquals(ConverterCategory.WEIGHT, ConverterState(weight.saveUserPreferences()).category)
    }

    @Test fun aDamagedPreferenceOpensVolume() {
        for (bad in listOf("", "garbage", "1;x;y;0;0;0;|2;x;y;0;0;0;|99;0;Nope;|", "75;Milliliters;mL;0;0;0;|36;Miles;mi;0;0;0;|4;0;Volume;|")) {
            val state = ConverterState(bad)
            assertEquals(bad, ConverterCategory.VOLUME, state.category)
            assertEquals("Milliliters", state.unit1.name)
            assertEquals("Teaspoons (US)", state.unit2.name)
        }
    }

    @Test fun unknownKeyNamesAreRejected() {
        val state = ConverterState()
        try {
            state.press("equals")
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("equals"))
        }
        for (name in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "decimal", "backspace", "clear", "negate")) {
            assertEquals(name, ConverterKey.fromName(name)?.keyName)
        }
    }
}
