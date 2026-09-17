package app.tileshell.ui.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import kotlin.math.roundToInt

/**
 * Windows 10 XAML type ramp (R1 §5.1, [UWP]); tile text uses the same ramp. Line height = 125 % of the
 * size rounded to the nearest 4 epx (R1 §5.1; R3 A15 measured body 20 epx, caption lines 16 epx).
 * Units are epx because [ShellDensity] maps 1.sp to 1 epx.
 */
object ShellType {
    private fun lineHeight(size: Float): Float = ((size * 1.25f) / 4f).roundToInt() * 4f

    private fun style(size: Float, weight: FontWeight) = TextStyle(
        fontFamily = Brand.uiFont,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight(size).sp,
    )

    val header = style(46f, FontWeight.Light)
    val subheader = style(34f, FontWeight.Light)
    val title = style(24f, Brand.semiLight)
    val subtitle = style(20f, FontWeight.Normal)
    val base = style(15f, FontWeight.SemiBold)
    val body = style(15f, FontWeight.Normal)
    val caption = style(12f, FontWeight.Normal)
}
