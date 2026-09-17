package app.tileshell.ui.tokens

import androidx.compose.ui.graphics.Color

/**
 * The 48-colour Windows 10 accent palette in the Colors page order, 6 columns x 8 rows (R1 §6.1 hex,
 * R3 A16 confirms W10M exposed this palette in this order, MEDIUM).
 */
object Palette {
    val accents: List<Pair<String, Long>> = listOf(
        "Yellow Gold" to 0xFFFFB900, "Pale Red" to 0xFFE74856, "Default Blue" to 0xFF0078D7, "Cool Blue Bright" to 0xFF0099BC, "Grey" to 0xFF7A7574, "Overcast" to 0xFF767676,
        "Gold" to 0xFFFF8C00, "Red" to 0xFFE81123, "Navy Blue" to 0xFF0063B1, "Cool Blue" to 0xFF2D7D9A, "Grey Brown" to 0xFF5D5A58, "Storm" to 0xFF4C4A48,
        "Orange Bright" to 0xFFF7630C, "Rose Bright" to 0xFFEA005E, "Purple Shadow" to 0xFF8E8CD8, "Seafoam" to 0xFF00B7C3, "Steel Blue" to 0xFF68768A, "Blue Grey" to 0xFF69797E,
        "Orange Dark" to 0xFFCA5010, "Rose" to 0xFFC30052, "Purple Shadow Dark" to 0xFF6B69D6, "Seafoam Teal" to 0xFF038387, "Metal Blue" to 0xFF515C6B, "Grey Dark" to 0xFF4A5459,
        "Rust" to 0xFFDA3B01, "Plum Light" to 0xFFE3008C, "Iris Pastel" to 0xFF8764B8, "Mint Light" to 0xFF00B294, "Pale Moss" to 0xFF567C73, "Liddy Green" to 0xFF647C64,
        "Pale Rust" to 0xFFEF6950, "Plum" to 0xFFBF0077, "Iris Spring" to 0xFF744DA9, "Mint Dark" to 0xFF018574, "Moss" to 0xFF486860, "Sage" to 0xFF525E54,
        "Brick Red" to 0xFFD13438, "Orchid Light" to 0xFFC239B3, "Violet Red Light" to 0xFFB146C2, "Turf Green" to 0xFF00CC6A, "Meadow Green" to 0xFF498205, "Camouflage Desert" to 0xFF847545,
        "Mod Red" to 0xFFFF4343, "Orchid" to 0xFF9A0089, "Violet Red" to 0xFF881798, "Sport Green" to 0xFF10893E, "Green" to 0xFF107C10, "Camouflage" to 0xFF7E735F,
    )

    /** Out-of-box accent: Windows "Default Blue" (approximation, recorded in the INDEX change log). */
    const val DEFAULT_ACCENT = 0xFF0078D7

    // Theme base colours (R1 §6.2, [UWP]).
    val darkBackground = Color(0xFF000000)
    val darkText = Color(0xFFFFFFFF)
    val lightBackground = Color(0xFFFFFFFF)
    val lightText = Color(0xFF000000)
    val darkChromeMedium = Color(0xFF1F1F1F)
    val lightChromeMedium = Color(0xFFE6E6E6)
    val darkChromeLow = Color(0xFF171717)
    val lightChromeLow = Color(0xFFF2F2F2)
}
