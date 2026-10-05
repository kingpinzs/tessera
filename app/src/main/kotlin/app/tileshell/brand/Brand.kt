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

    /**
     * The emoji panel's artwork (phase 05 Decisions): Microsoft's MIT-licensed Fluent Emoji, Flat style,
     * fetched by tools/fetch-keyboard.sh into this asset folder with an index.tsv beside it. It is
     * branding like the fonts: a public build swaps the folder, and the panel inserts plain Unicode
     * whatever artwork draws it.
     */
    const val EMOJI_ARTWORK_DIR = "keyboard/emoji"

    /**
     * Phase 12 (T12-7): the theme presets whose names or pictures are Microsoft's live here, so a public build can swap
     * them with the rest of this module. The original preset's two pictures are R12's stock img0 files (Q8 C, ruling
     * (a)), cropped by tools/make-preset-pictures.py into res/drawable-nodpi; the other four are Jeremy's (Q6).
     */
    const val PRESET_W10M_NAME = "Windows 10 Mobile (original)"
    const val PRESET_LUMIA_NAME = "Lumia"

    /** A preset picture's drawable name, e.g. `preset_hal`, `preset_w10m_hero`. */
    fun presetPicture(name: String) = "preset_$name"

    const val PRODUCT_NAME = "Tessera"

    /**
     * Phase 17: the video app's name — W10M's en-US "Movies & TV" (en-GB shipped "Films & TV"); swappable here like
     * every Microsoft name (H7). The launcher label is the string resource `video_name`, which must say the same.
     */
    const val VIDEO_APP_NAME = "Movies & TV"
    const val ASSISTANT_NAME = "Tess"

    /**
     * Phase 14 (R10-Q5, Jeremy: "(a)"): the pager page left of Start is the "pod bay" and its cards are "pods" — the
     * HAL lineage Tess's lens already carries. The page title, the Settings entry, the empty-bay line and Tess's phrase
     * set all read these, so the whole HAL layer (lens, name, line) swaps together in a public build.
     */
    const val POD_BAY_NAME = "pod bay"
    const val POD_NAME = "pod"

    /** Tess's answer to "open the pod bay doors" — spoken as is, then she opens it anyway (the film's line, not a mark). */
    const val POD_BAY_DOORS_REPLY = "I'm afraid I can't do that, Dave."

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
