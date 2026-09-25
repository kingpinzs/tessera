package app.tileshell.calc.convert

/**
 * The Converter's twelve categories, in W10M's pane order (docs/plan/r11/calculator.md 3.9; phase 15 Q4 A — no Currency).
 *
 * [id] is Windows Calculator's serialization id for the category (`NavCategoryStates` in
 * microsoft/calculator@4fd3fc5 src/Calculator.ViewModels/Common/NavCategory.cs — "THESE CONSTANTS SHOULD NEVER CHANGE"),
 * which is what the saved preferences string carries. [supportsNegative] is the manifest's `SupportsNegative` flag:
 * only Temperature, Power and Angle accept `negate`.
 *
 * [label] is the text W10M showed. It equals the en-US resource except Weight, which the phone labelled
 * "Weight and Mass" (r11/calculator.md 3.9, T15-15) where the 2019 source's resw says "Weight and mass".
 */
enum class ConverterCategory(val id: Int, val label: String, val supportsNegative: Boolean) {
    VOLUME(4, "Volume", false),
    LENGTH(5, "Length", false),
    WEIGHT(6, "Weight and Mass", false),
    TEMPERATURE(7, "Temperature", true),
    ENERGY(8, "Energy", false),
    AREA(9, "Area", false),
    SPEED(10, "Speed", false),
    TIME(11, "Time", false),
    POWER(12, "Power", true),
    DATA(13, "Data", false),
    PRESSURE(14, "Pressure", false),
    ANGLE(15, "Angle", true);

    companion object {
        /** The category with Windows' serialization [id], or null. */
        fun fromId(id: Int): ConverterCategory? = entries.firstOrNull { it.id == id }
    }
}
