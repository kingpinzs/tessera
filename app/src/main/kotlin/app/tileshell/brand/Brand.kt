package app.tileshell.brand

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.tileshell.R

/**
 * The swappable branding module (A10). Every Microsoft-derived name, font and glyph lives here so a
 * public build can replace this one file set. Selawik (OFL 1.1) stands in for Segoe UI (Q8); the
 * Fluent UI System Icons resizable font (MIT) stands in for Segoe MDL2 Assets.
 */
object Brand {
    val uiFont = FontFamily(
        Font(R.font.selawik_light, FontWeight.Light),
        Font(R.font.selawik_semilight, FontWeight.ExtraLight),
        Font(R.font.selawik_regular, FontWeight.Normal),
        Font(R.font.selawik_semibold, FontWeight.SemiBold),
        Font(R.font.selawik_bold, FontWeight.Bold),
    )

    /** Selawik ships SemiLight as its own file; Compose has no SemiLight weight, so ExtraLight maps to it. */
    val semiLight = FontWeight.ExtraLight

    val iconFont = FontFamily(Font(R.font.fluent_icons))

    const val PRODUCT_NAME = "Windows"
    const val ASSISTANT_NAME = "Cortana"
}
