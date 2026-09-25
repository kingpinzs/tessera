package app.tileshell.calc.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The category list (E12, Q4 A, r11/calculator.md 3.9) and the unit tables as the US loader builds them. */
class UnitTablesTest {

    @Test fun twelveCategoriesInW10mOrderAndNoCurrency() {
        assertEquals(
            listOf("Volume", "Length", "Weight and Mass", "Temperature", "Energy", "Area", "Speed", "Time", "Power", "Data",
                "Pressure", "Angle"),
            ConverterState().categories.map { it.label },
        )
        assertFalse(ConverterCategory.entries.any { it.label.contains("Currency") || it.name.contains("CURRENCY") })
        assertEquals(listOf(4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15), ConverterCategory.entries.map { it.id })
        assertEquals(
            setOf(ConverterCategory.TEMPERATURE, ConverterCategory.POWER, ConverterCategory.ANGLE),
            ConverterCategory.entries.filter { it.supportsNegative }.toSet(),
        )
    }

    @Test fun unitCountsAndDefaultPairs() {
        // category → picker units, units including whimsical, default from, default to (host-parsed from the loader).
        val expected = mapOf(
            ConverterCategory.VOLUME to listOf(20, 23, "Milliliters", "Teaspoons (US)"),
            ConverterCategory.LENGTH to listOf(12, 15, "Centimeters", "Inches"),
            ConverterCategory.WEIGHT to listOf(14, 18, "Kilograms", "Pounds"),
            ConverterCategory.TEMPERATURE to listOf(3, 3, "Celsius", "Fahrenheit"),
            ConverterCategory.ENERGY to listOf(8, 11, "Joules", "Food calories"),
            ConverterCategory.AREA to listOf(10, 14, "Square meters", "Square feet"),
            ConverterCategory.SPEED to listOf(7, 10, "Kilometers per hour", "Miles per hour"),
            ConverterCategory.TIME to listOf(8, 8, "Hours", "Minutes"),
            ConverterCategory.POWER to listOf(5, 8, "Kilowatts", "Horsepower (US)"),
            ConverterCategory.DATA to listOf(35, 38, "Gigabytes", "Megabytes"),
            ConverterCategory.PRESSURE to listOf(6, 6, "Atmospheres", "Bars"),
            ConverterCategory.ANGLE to listOf(3, 3, "Degrees", "Radians"),
        )
        for ((category, e) in expected) {
            assertEquals("$category picker", e[0], UnitTables.pickerUnits(category).size)
            assertEquals("$category all", e[1], UnitTables.orderedUnits(category).size)
            assertEquals("$category from", e[2], UnitTables.defaultFrom(category).name)
            assertEquals("$category to", e[3], UnitTables.defaultTo(category).name)
            val state = ConverterState()
            state.selectCategory(category)
            assertEquals(e[2], state.unit1.name)
            assertEquals(e[3], state.unit2.name)
        }
    }

    @Test fun pickerOrderFollowsTheLoaderOrderNumbers() {
        assertEquals(
            listOf("Milliliters", "Cubic centimeters", "Liters", "Cubic meters", "Teaspoons (US)", "Tablespoons (US)",
                "Fluid ounces (US)", "Cups (US)", "Pints (US)", "Quarts (US)", "Gallons (US)", "Cubic inches", "Cubic feet",
                "Cubic yards", "Teaspoons (UK)", "Tablespoons (UK)", "Fluid ounces (UK)", "Pints (UK)", "Quarts (UK)",
                "Gallons (UK)"),
            UnitTables.pickerUnits(ConverterCategory.VOLUME).map { it.name },
        )
        // Kilowatt-hours carries order 166, so it sorts last.
        assertEquals(
            listOf("Electron volts", "Joules", "Kilojoules", "Thermal calories", "Food calories", "Foot-pounds",
                "British thermal units", "Kilowatt-hours"),
            UnitTables.pickerUnits(ConverterCategory.ENERGY).map { it.name },
        )
        // Stable sort: order 13/14/15 ties keep the non-whimsical unit first.
        val data = UnitTables.orderedUnits(ConverterCategory.DATA).map { it.name }
        assertEquals(
            listOf("Gigabits", "Gibibits", "floppy disks", "Gigabytes", "CDs", "Gibibytes", "DVDs", "Terabits"),
            data.subList(data.indexOf("Gigabits"), data.indexOf("Terabits") + 1),
        )
        assertEquals(listOf("Celsius", "Fahrenheit", "Kelvin"), UnitTables.pickerUnits(ConverterCategory.TEMPERATURE).map { it.name })
    }

    @Test fun namesAndAbbreviationsAreTheEnUsStrings() {
        val km = UnitTables.pickerUnits(ConverterCategory.LENGTH).single { it.id == 33 }
        assertEquals("Kilometers", km.name)
        assertEquals("km", km.abbreviation)
        val jet = UnitTables.orderedUnits(ConverterCategory.LENGTH).single { it.id == 107 }
        assertEquals("jumbo jets", jet.abbreviation)
        assertTrue(jet.isWhimsical)
        assertEquals("Millimeters of mercury", UnitTables.unit(140)!!.name)
        assertEquals("Food calories", UnitTables.unit(28)!!.name)
        assertFalse(UnitTables.pickerUnits(ConverterCategory.AREA).any { it.name == "Pyeong" }) // not in the US
    }

    @Test fun conversionDataIsTheLoadersRatio() {
        val mi = UnitTables.unit(36)!!
        val km = UnitTables.unit(33)!!
        assertEquals(ConversionData(1609.344 / 1000.0, 0.0, false), UnitTables.conversion(mi, km))
        val f = UnitTables.unit(47)!!
        val k = UnitTables.unit(48)!!
        assertEquals(ConversionData(0.5555555555555556, 459.67, true), UnitTables.conversion(f, k))
    }
}
