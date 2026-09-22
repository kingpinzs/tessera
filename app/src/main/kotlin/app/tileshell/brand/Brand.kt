package app.tileshell.brand

import androidx.compose.ui.graphics.Color
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

    const val PRODUCT_NAME = "Tessera"
    const val ASSISTANT_NAME = "Tess"

    /**
     * Tess's eye is HAL 9000's lens (Jeremy, 2026-09-21). Four tones, and they all sit on ONE hue line
     * — each is the rim tone scaled up — so a colour search finds the whole lens the way E4's
     * persona.py found the flat accent disc. Only [LENS_CORE] is off the line: it is the specular
     * highlight, it reads as white rather than red, and the measurement ignores it on purpose.
     */
    val LENS_RIM = Color(0xFF8A1008)
    val LENS_IRIS = Color(0xFFD81810)
    val LENS_GLOW = Color(0xFFFF2D1C)
    val LENS_CORE = Color(0xFFFFE9C8)
}
