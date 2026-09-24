package app.tileshell.calc.convert

/**
 * One unit of a [ConverterCategory]. [id] is Windows Calculator's unit id (UnitConverterDataConstants.h), [name] and
 * [abbreviation] its en-US strings (src/Calculator/Resources/en-US/Resources.resw, `UnitName_*` / `UnitAbbreviation_*`).
 * A whimsical unit ("jumbo jets", "bananas") is never in a unit picker; it only appears as the last "About equal to"
 * suggestion.
 */
data class ConverterUnit(
    val id: Int,
    val name: String,
    val abbreviation: String,
    val category: ConverterCategory,
    val isWhimsical: Boolean,
)

/** CalcManager's `ConversionData`: `offsetFirst ? (v + offset) * ratio : v * ratio + offset`, in IEEE doubles. */
data class ConversionData(val ratio: Double, val offset: Double, val offsetFirst: Boolean)

/**
 * Port of microsoft/calculator@4fd3fc5 src/Calculator.ViewModels/DataLoaders/UnitConverterDataLoader.cs `LoadData`
 * for region "US" (the loader's default; the region flags below are evaluated exactly as the loader does).
 *
 * Per category: the units sorted by the loader's order number (LINQ `OrderBy`, stable, so equal order numbers keep
 * insertion order — Data's whimsicals share 13/14/15 with Gibibits/Gigabyte/Gibibytes), each unit's default
 * conversion-source/-target flag, and the ratio table: `sourceFactor / targetFactor` in doubles for factor categories,
 * and the explicit (ratio, offset, offsetFirst) triples for Temperature. The factor literals are copied verbatim, except
 * the thirteen listed at [factors]; a Kotlin double literal rounds to nearest exactly as Roslyn does for the C# literal.
 */
object UnitTables {
    private const val REGION = "US"

    private val useUSCustomaryAndFahrenheit = REGION == "US" || REGION == "FM" || REGION == "MH" || REGION == "PW"
    private val useUSCustomary = useUSCustomaryAndFahrenheit || REGION == "LR"
    private val useSI = !useUSCustomary
    private val useFahrenheit = useUSCustomaryAndFahrenheit || REGION == "BS" || REGION == "KY" || REGION == "LR"
    private val useWattInsteadOfKilowatt = REGION == "GB"
    private val usePyeong = REGION == "JP" || REGION == "TW" || REGION == "KP" || REGION == "KR"

    private class Def(
        val category: ConverterCategory,
        val id: Int,
        val name: String,
        val abbreviation: String,
        val order: Int,
        val isSource: Boolean,
        val isTarget: Boolean,
        val isWhimsical: Boolean,
    )

    private val defs = ArrayList<Def>()

    private fun add(
        category: ConverterCategory, id: Int, name: String, abbreviation: String, order: Int,
        isSource: Boolean = false, isTarget: Boolean = false, isWhimsical: Boolean = false,
    ) {
        defs += Def(category, id, name, abbreviation, order, isSource, isTarget, isWhimsical)
    }

    init {
        val area = ConverterCategory.AREA
        add(area, 1, "Acres", "ac", 9)
        add(area, 2, "Hectares", "ha", 4)
        add(area, 3, "Square centimeters", "cm²", 2)
        add(area, 4, "Square feet", "ft²", 7, useSI, useUSCustomary)
        add(area, 5, "Square inches", "in²", 6)
        add(area, 6, "Square kilometers", "km²", 5)
        add(area, 7, "Square meters", "m²", 3, useUSCustomary, useSI)
        add(area, 8, "Square miles", "mi²", 10)
        add(area, 9, "Square millimeters", "mm²", 1)
        add(area, 10, "Square yards", "yd²", 8)
        add(area, 118, "hands", "hands", 11, isWhimsical = true)
        add(area, 127, "sheets of paper", "sheets of paper", 12, isWhimsical = true)
        add(area, 99, "soccer fields", "soccer fields", 13, isWhimsical = true)
        add(area, 128, "castles", "castles", 14, isWhimsical = true)
        if (usePyeong) add(area, 165, "Pyeong", "Pyeong", 15)

        val data = ConverterCategory.DATA
        add(data, 11, "Bits", "b", 1)
        add(data, 12, "Bytes", "B", 3)
        add(data, 143, "Exabits", "E", 24)
        add(data, 144, "Exabytes", "EB", 26)
        add(data, 145, "Exbibits", "Ei", 25)
        add(data, 146, "Exbibytes", "EiB", 27)
        add(data, 147, "Gibibits", "Gi", 13)
        add(data, 148, "Gibibytes", "GiB", 15)
        add(data, 13, "Gigabits", "Gb", 12)
        add(data, 14, "Gigabytes", "GB", 14, true, false)
        add(data, 149, "Kibibits", "Ki", 5)
        add(data, 150, "Kibibytes", "KiB", 7)
        add(data, 15, "Kilobits", "Kb", 4)
        add(data, 16, "Kilobytes", "KB", 6)
        add(data, 151, "Mebibits", "Mi", 9)
        add(data, 152, "Mebibytes", "MiB", 11)
        add(data, 17, "Megabits", "Mb", 8)
        add(data, 18, "Megabytes", "MB", 10, false, true)
        add(data, 167, "Nibble", "nybl", 2)
        add(data, 153, "Pebibits", "Pi", 21)
        add(data, 154, "Pebibytes", "PiB", 23)
        add(data, 19, "Petabits", "Pb", 20)
        add(data, 20, "Petabytes", "PB", 22)
        add(data, 155, "Tebibits", "Ti", 17)
        add(data, 156, "Tebibytes", "TiB", 19)
        add(data, 21, "Terabits", "Tb", 16)
        add(data, 22, "Terabytes", "TB", 18)
        add(data, 157, "Yobibits", "Yi", 33)
        add(data, 158, "Yobibytes", "YiB", 35)
        add(data, 159, "Yottabits", "Y", 32)
        add(data, 160, "Yottabytes", "YB", 34)
        add(data, 161, "Zebibits", "Zi", 29)
        add(data, 162, "Zebibytes", "ZiB", 31)
        add(data, 163, "Zetabits", "Z", 28)
        add(data, 164, "Zetabytes", "ZB", 30)
        add(data, 100, "floppy disks", "floppy disks", 13, isWhimsical = true)
        add(data, 101, "CDs", "CDs", 14, isWhimsical = true)
        add(data, 102, "DVDs", "DVDs", 15, isWhimsical = true)

        val energy = ConverterCategory.ENERGY
        add(energy, 23, "British thermal units", "BTU", 7)
        add(energy, 24, "Thermal calories", "cal", 4)
        add(energy, 25, "Electron volts", "eV", 1)
        add(energy, 26, "Foot-pounds", "ft•lb", 6)
        add(energy, 27, "Joules", "J", 2, true, false)
        add(energy, 166, "Kilowatt-hours", "kWh", 166, true, false)
        add(energy, 28, "Food calories", "kcal", 5, false, true)
        add(energy, 29, "Kilojoules", "kJ", 3)
        add(energy, 103, "batteries", "batteries", 8, isWhimsical = true)
        add(energy, 129, "bananas", "bananas", 9, isWhimsical = true)
        add(energy, 130, "slices of cake", "slices of cake", 10, isWhimsical = true)

        val length = ConverterCategory.LENGTH
        add(length, 168, "Angstroms", "A", 1)
        add(length, 30, "Centimeters", "cm", 5, useUSCustomary, useSI)
        add(length, 31, "Feet", "ft", 9)
        add(length, 32, "Inches", "in", 8, useSI, useUSCustomary)
        add(length, 33, "Kilometers", "km", 7)
        add(length, 34, "Meters", "m", 6)
        add(length, 35, "Microns", "µm", 3)
        add(length, 36, "Miles", "mi", 11)
        add(length, 37, "Millimeters", "mm", 4)
        add(length, 38, "Nanometers", "nm", 2)
        add(length, 39, "Nautical miles", "nmi", 12)
        add(length, 40, "Yards", "yd", 10)
        add(length, 105, "paperclips", "paperclips", 13, isWhimsical = true)
        add(length, 131, "hands", "hands", 14, isWhimsical = true)
        add(length, 107, "jumbo jets", "jumbo jets", 15, isWhimsical = true)

        val power = ConverterCategory.POWER
        add(power, 41, "BTUs/minute", "BTU/min", 5)
        add(power, 42, "Foot-pounds/minute", "ft•lb/min", 4)
        add(power, 43, "Horsepower (US)", "hp (US)", 3, false, true)
        add(power, 44, "Kilowatts", "kW", 2, !useWattInsteadOfKilowatt)
        add(power, 45, "Watts", "W", 1, useWattInsteadOfKilowatt)
        add(power, 108, "light bulbs", "light bulbs", 6, isWhimsical = true)
        add(power, 109, "horses", "horses", 7, isWhimsical = true)
        add(power, 132, "train engines", "train engines", 8, isWhimsical = true)

        val temperature = ConverterCategory.TEMPERATURE
        add(temperature, 46, "Celsius", "°C", 1, useFahrenheit, !useFahrenheit)
        add(temperature, 47, "Fahrenheit", "°F", 2, !useFahrenheit, useFahrenheit)
        add(temperature, 48, "Kelvin", "K", 3)

        val time = ConverterCategory.TIME
        add(time, 49, "Days", "d", 6)
        add(time, 50, "Hours", "hr", 5, true, false)
        add(time, 51, "Microseconds", "µs", 1)
        add(time, 52, "Milliseconds", "ms", 2)
        add(time, 53, "Minutes", "min", 4, false, true)
        add(time, 54, "Seconds", "s", 3)
        add(time, 55, "Weeks", "wk", 7)
        add(time, 56, "Years", "yr", 8)

        val speed = ConverterCategory.SPEED
        add(speed, 57, "Centimeters per second", "cm/s", 1)
        add(speed, 58, "Feet per second", "ft/s", 4)
        add(speed, 59, "Kilometers per hour", "km/h", 3, useUSCustomary, useSI)
        add(speed, 60, "Knots", "kn", 6)
        add(speed, 61, "Mach", "M", 7)
        add(speed, 62, "Meters per second", "m/s", 2)
        add(speed, 63, "Miles per hour", "mph", 5, useSI, useUSCustomary)
        add(speed, 121, "turtles", "turtles", 8, isWhimsical = true)
        add(speed, 126, "horses", "horses", 9, isWhimsical = true)
        add(speed, 122, "jets", "jets", 10, isWhimsical = true)

        val volume = ConverterCategory.VOLUME
        add(volume, 64, "Cubic centimeters", "cm³", 2)
        add(volume, 65, "Cubic feet", "ft³", 13)
        add(volume, 66, "Cubic inches", "in³", 12)
        add(volume, 67, "Cubic meters", "m³", 4)
        add(volume, 68, "Cubic yards", "yd³", 14)
        add(volume, 69, "Cups (US)", "cup (US)", 8)
        add(volume, 70, "Fluid ounces (UK)", "fl oz (UK)", 17)
        add(volume, 71, "Fluid ounces (US)", "fl oz (US)", 7)
        add(volume, 72, "Gallons (UK)", "gal (UK)", 20)
        add(volume, 73, "Gallons (US)", "gal (US)", 11)
        add(volume, 74, "Liters", "L", 3)
        add(volume, 75, "Milliliters", "mL", 1, useUSCustomary, useSI)
        add(volume, 76, "Pints (UK)", "pt (UK)", 18)
        add(volume, 77, "Pints (US)", "pt (US)", 9)
        add(volume, 78, "Tablespoons (US)", "tbsp. (US)", 6)
        add(volume, 79, "Teaspoons (US)", "tsp. (US)", 5, useSI, useUSCustomary && REGION != "GB")
        add(volume, 80, "Quarts (UK)", "qt (UK)", 19)
        add(volume, 81, "Quarts (US)", "qt (US)", 10)
        add(volume, 115, "Teaspoons (UK)", "tsp. (UK)", 15, false, useUSCustomary && REGION == "GB")
        add(volume, 116, "Tablespoons (UK)", "tbsp. (UK)", 16)
        add(volume, 124, "coffee cups", "coffee cups", 22, isWhimsical = true)
        add(volume, 111, "bathtubs", "bathtubs", 23, isWhimsical = true)
        add(volume, 125, "swimming pools", "swimming pools", 24, isWhimsical = true)

        val weight = ConverterCategory.WEIGHT
        add(weight, 82, "Carats", "CD", 1)
        add(weight, 83, "Centigrams", "cg", 3)
        add(weight, 84, "Decigrams", "dg", 4)
        add(weight, 85, "Dekagrams", "dag", 6)
        add(weight, 86, "Grams", "g", 5)
        add(weight, 87, "Hectograms", "hg", 7)
        add(weight, 88, "Kilograms", "kg", 8, useUSCustomary, useSI)
        add(weight, 89, "Long tons (UK)", "ton (UK)", 14)
        add(weight, 90, "Milligrams", "mg", 2)
        add(weight, 91, "Ounces", "oz", 10)
        add(weight, 92, "Pounds", "lb", 11, useSI, useUSCustomary)
        add(weight, 93, "Short tons (US)", "ton (US)", 13)
        add(weight, 94, "Stone", "st", 12)
        add(weight, 95, "Metric tonnes", "t", 9)
        add(weight, 113, "snowflakes", "snowflakes", 15, isWhimsical = true)
        add(weight, 133, "soccer balls", "soccer balls", 16, isWhimsical = true)
        add(weight, 114, "elephants", "elephants", 17, isWhimsical = true)
        add(weight, 123, "whales", "whales", 18, isWhimsical = true)

        val pressure = ConverterCategory.PRESSURE
        add(pressure, 137, "Atmospheres", "atm", 1, true, false)
        add(pressure, 138, "Bars", "ba", 2, false, true)
        add(pressure, 139, "Kilopascals", "kPa", 3)
        // The resw value is "Millimeters of mercury " (trailing space); the trailing space is dropped here.
        add(pressure, 140, "Millimeters of mercury", "mmHg", 4)
        add(pressure, 141, "Pascals", "Pa", 5)
        add(pressure, 142, "Pounds per square inch", "psi", 6)

        val angle = ConverterCategory.ANGLE
        add(angle, 134, "Degrees", "deg", 1, true, false)
        add(angle, 135, "Radians", "rad", 2, false, true)
        add(angle, 136, "Gradians", "grad", 3)
    }

    /**
     * Factor per unit id, "matches GetConversionData in C++" (loader comment); ratio = source / target.
     *
     * Thirteen factors are not the loader's literals, which are rounded, truncated or out of date there: each is its
     * unit's exact definition, written in full or as the nearest double (INDEX.md Change Log, 2026-09-24: where
     * Windows is wrong, the right answer wins). The loader's value is in brackets.
     * BTU = 1055.05585262 J, the International Table BTU [1055.056]; eV = 1.602176634e-19 J, exact in the 2019 SI
     * [CODATA 2010's 1.602176565e-19]; ft·lb = 0.3048 m × 0.45359237 kg × 9.80665 m/s² [cut to 14 digits]; BTU/min
     * and ft·lb/min = those ÷ 60 [1055.056 / 60; cut to 15 digits]; cup (US) = 1/16 US gallon of 231 in³ [236.588237];
     * knot = 1852 m/h [51.44]; mph = 1609.344 m/h [44.7]; Mach = 340.294 m/s, the ISA sea-level speed of sound
     * (ICAO Doc 7488) [340.3]; radian = 180/π degrees [one ulp high]; kPa = 1000/101325 atm [cut to 14 digits];
     * mmHg = 133.322387415 Pa [133.3 Pa]; psi = 0.45359237 × 9.80665 N / 0.0254² m² [wrong from the 8th digit].
     * Every other real unit's literal already equals its definition to the last bit; the whimsical units keep theirs.
     */
    private val factors: Map<ConverterCategory, Map<Int, Double>> = mapOf(
        ConverterCategory.AREA to mapOf(
            1 to 4046.8564224, 7 to 1.0, 4 to 0.09290304, 10 to 0.83612736, 9 to 0.000001, 3 to 0.0001,
            5 to 0.00064516, 8 to 2589988.110336, 6 to 1000000.0, 2 to 10000.0, 118 to 0.012516104,
            127 to 0.06032246, 99 to 10869.66, 128 to 100000.0, 165 to 400.0 / 121.0,
        ),
        ConverterCategory.DATA to mapOf(
            11 to 0.000000125, 167 to 0.0000005, 12 to 0.000001, 16 to 0.001, 18 to 1.0, 14 to 1000.0,
            22 to 1000000.0, 20 to 1000000000.0, 144 to 1000000000000.0, 164 to 1000000000000000.0,
            160 to 1000000000000000000.0, 15 to 0.000125, 17 to 0.125, 13 to 125.0, 21 to 125000.0,
            19 to 125000000.0, 143 to 125000000000.0, 163 to 125000000000000.0, 159 to 125000000000000000.0,
            147 to 134.217728, 148 to 1073.741824, 149 to 0.000128, 150 to 0.001024, 151 to 0.131072,
            152 to 1.048576, 153 to 140737488.355328, 154 to 1125899906.842624, 155 to 137438.953472,
            156 to 1099511.627776, 145 to 144115188075.855872, 146 to 1152921504606.846976,
            161 to 147573952589676.412928, 162 to 1180591620717411.303424, 157 to 151115727451828646.838272,
            158 to 1208925819614629174.706176, 100 to 1.474560, 101 to 700.0, 102 to 4700.0,
        ),
        ConverterCategory.ENERGY to mapOf(
            24 to 4.184, 28 to 4184.0, 23 to 1055.05585262, 29 to 1000.0, 166 to 3600000.0,
            25 to 1.602176634e-19, 27 to 1.0, 26 to 1.3558179483314004, 103 to 9000.0,
            129 to 439614.0, 130 to 1046700.0,
        ),
        ConverterCategory.LENGTH to mapOf(
            32 to 0.0254, 31 to 0.3048, 40 to 0.9144, 36 to 1609.344, 35 to 0.000001, 37 to 0.001,
            38 to 0.000000001, 168 to 0.0000000001, 30 to 0.01, 34 to 1.0, 33 to 1000.0, 39 to 1852.0,
            105 to 0.035052, 131 to 0.18669, 107 to 76.0,
        ),
        ConverterCategory.POWER to mapOf(
            41 to 17.584264210333334, 42 to 0.02259696580552334, 45 to 1.0, 44 to 1000.0,
            43 to 745.69987158227022, 108 to 60.0, 109 to 745.7, 132 to 2982799.486329081,
        ),
        ConverterCategory.TIME to mapOf(
            49 to 86400.0, 54 to 1.0, 55 to 604800.0, 56 to 31557600.0, 52 to 0.001, 51 to 0.000001,
            53 to 60.0, 50 to 3600.0,
        ),
        ConverterCategory.VOLUME to mapOf(
            69 to 236.5882365, 77 to 473.176473, 76 to 568.26125, 81 to 946.352946, 80 to 1136.5225,
            73 to 3785.411784, 72 to 4546.09, 74 to 1000.0, 79 to 4.92892159375, 78 to 14.78676478125,
            64 to 1.0, 68 to 764554.857984, 67 to 1000000.0, 75 to 1.0, 66 to 16.387064, 65 to 28316.846592,
            71 to 29.5735295625, 70 to 28.4130625, 115 to 5.91938802083333333333, 116 to 17.7581640625,
            124 to 236.5882, 111 to 378541.2, 125 to 3750000000.0,
        ),
        ConverterCategory.WEIGHT to mapOf(
            88 to 1.0, 87 to 0.1, 85 to 0.01, 86 to 0.001, 92 to 0.45359237, 91 to 0.028349523125,
            90 to 0.000001, 83 to 0.00001, 84 to 0.0001, 89 to 1016.0469088, 95 to 1000.0, 94 to 6.35029318,
            82 to 0.0002, 93 to 907.18474, 113 to 0.000002, 133 to 0.4325, 114 to 4000.0, 123 to 90000.0,
        ),
        ConverterCategory.SPEED to mapOf(
            57 to 1.0, 58 to 30.48, 59 to 27.777777777777777777778, 60 to 51.44444444444444, 61 to 34029.4, 62 to 100.0,
            63 to 44.704, 121 to 8.94, 126 to 2011.5, 122 to 24585.0,
        ),
        ConverterCategory.ANGLE to mapOf(134 to 1.0, 135 to 57.29577951308232, 136 to 0.9),
        ConverterCategory.PRESSURE to mapOf(
            137 to 1.0, 138 to 0.9869232667160128, 139 to 0.009869232667160128, 140 to 0.0013157896611398965,
            141 to 9.869232667160128e-6, 142 to 0.06804596390987773,
        ),
    )

    /** Temperature's explicit conversions, in the loader's dictionary order (Celsius, Fahrenheit, Kelvin). */
    private val explicitConversions: Map<Int, List<Pair<Int, ConversionData>>> = mapOf(
        46 to listOf(
            46 to ConversionData(1.0, 0.0, false),
            47 to ConversionData(1.8, 32.0, false),
            48 to ConversionData(1.0, 273.15, false),
        ),
        47 to listOf(
            46 to ConversionData(0.55555555555555555555555555555556, -32.0, true),
            47 to ConversionData(1.0, 0.0, false),
            48 to ConversionData(0.55555555555555555555555555555556, 459.67, true),
        ),
        48 to listOf(
            46 to ConversionData(1.0, -273.15, true),
            47 to ConversionData(1.8, -459.67, false),
            48 to ConversionData(1.0, 0.0, false),
        ),
    )

    private val sortedDefs: Map<ConverterCategory, List<Def>> =
        ConverterCategory.entries.associateWith { c -> defs.filter { it.category == c }.sortedBy { it.order } }

    private val unitsByCategory: Map<ConverterCategory, List<ConverterUnit>> = sortedDefs.mapValues { (_, list) ->
        list.map { ConverterUnit(it.id, it.name, it.abbreviation, it.category, it.isWhimsical) }
    }

    private val unitsById: Map<Int, ConverterUnit> = unitsByCategory.values.flatten().associateBy { it.id }

    /**
     * Every conversion out of a unit, in the order `LoadOrderedRatios` returns them (the category's sorted unit order,
     * or Temperature's dictionary order) — the order the interop bridge inserts them into the engine's map.
     */
    private val ratiosByUnit: Map<Int, List<Pair<ConverterUnit, ConversionData>>> = buildMap {
        for ((category, units) in unitsByCategory) {
            for (source in units) {
                val explicit = explicitConversions[source.id]
                if (explicit != null) {
                    put(source.id, explicit.mapNotNull { (id, data) -> unitsById[id]?.let { it to data } })
                    continue
                }
                val categoryFactors = factors.getValue(category)
                val sourceFactor = categoryFactors[source.id] ?: continue
                put(source.id, units.mapNotNull { target ->
                    val targetFactor = categoryFactors[target.id]
                    if (targetFactor != null && targetFactor > 0) {
                        target to ConversionData(sourceFactor / targetFactor, 0.0, false)
                    } else {
                        null
                    }
                })
            }
        }
    }

    /** The engine's unit list for [category]: sorted, whimsical units included (`GetOrderedUnits`). */
    fun orderedUnits(category: ConverterCategory): List<ConverterUnit> = unitsByCategory.getValue(category)

    /** The unit picker's list for [category]: [orderedUnits] without the whimsical ones (the view model's `BuildUnitList`). */
    fun pickerUnits(category: ConverterCategory): List<ConverterUnit> = orderedUnits(category).filter { !it.isWhimsical }

    /** The loader's `IsConversionSource` flag (the category's default "from" unit is the first unit that has it). */
    fun isDefaultSource(unit: ConverterUnit): Boolean = def(unit).isSource

    /** The loader's `IsConversionTarget` flag (the category's default "to" unit is the first unit that has it). */
    fun isDefaultTarget(unit: ConverterUnit): Boolean = def(unit).isTarget

    /** The category's default from unit for the US region. */
    fun defaultFrom(category: ConverterCategory): ConverterUnit = orderedUnits(category).first { isDefaultSource(it) }

    /** The category's default to unit for the US region. */
    fun defaultTo(category: ConverterCategory): ConverterUnit = orderedUnits(category).first { isDefaultTarget(it) }

    /** The unit with Windows' unit [id], or null. */
    fun unit(id: Int): ConverterUnit? = unitsById[id]

    /** The conversion from [from] to [to] (same category), as the loader computes it. */
    fun conversion(from: ConverterUnit, to: ConverterUnit): ConversionData =
        ratiosByUnit.getValue(from.id).first { it.first.id == to.id }.second

    /** Every conversion out of [from], in `LoadOrderedRatios` order. */
    internal fun orderedRatios(from: ConverterUnit): List<Pair<ConverterUnit, ConversionData>> = ratiosByUnit.getValue(from.id)

    private fun def(unit: ConverterUnit): Def = defs.first { it.id == unit.id }
}
