package app.tileshell.ime

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.ui.tokens.CapMetrics

/**
 * R6 §2.6.1–2.6.2 (LOW, H6): the emoji panel replaces the four key rows and the strip stays above it.
 * Four rows of cells fill key rows 1–3 and scroll sideways with ≈7.5 columns showing; a ten-cell
 * category row sits where key row 4 was, at key pitch: abc · recent · smileys · people · celebration ·
 * food · travel · symbols · text emoticons · backspace. The active category is accent-filled, abc and
 * backspace are function grey.
 *
 * Cell geometry (R6 §2.6.4 UNMEASURED, approximation H7): four equal rows across the key-row 1–3 height
 * (≈159 phys pitch) and columns at width / 7.5 (192 phys).
 */
@Composable
fun EmojiPanel(state: KeyboardState, m: KeyboardMetrics, panelTop: Float, accent: Color, catalog: EmojiCatalog, actions: KeyboardActions) {
    val gridTop = panelTop + m.yInPanel(KeyGrid.rowTop(0))
    val gridH = m.h(KeyGrid.rowTop(2) + KeyGrid.KEY_H)
    val cellW = m.w(KeyGrid.PANEL / COLUMNS_VISIBLE)
    val cellH = gridH / ROWS
    val category = state.emojiCategory
    val recent = state.recentEmoji
    val items: List<String> = remember(category, recent, catalog) {
        when (category) {
            EmojiCategory.RECENT -> recent
            EmojiCategory.TEXT -> EmojiCatalog.TEXT_EMOTICONS
            else -> catalog.byCategory[category].orEmpty().map { it.text }
        }
    }
    val byText = remember(catalog) { catalog.byCategory.values.flatten().associateBy { it.text } }
    val gridState = rememberLazyGridState()
    LaunchedEffect(category) { gridState.scrollToItem(0) }
    Box(Modifier.at(m.offsetX, gridTop, m.screenWidthPx * m.dockFactor, gridH).testTag("kb_emoji_panel")) {
        LazyHorizontalGrid(rows = GridCells.Fixed(ROWS), state = gridState) {
            itemsIndexed(items) { i, text ->
                val emoji = byText[text]
                Box(
                    Modifier
                        .size(cellW.dp, cellH.dp)
                        .testTag("kb_emoji_cell_$i")
                        .semantics { contentDescription = text }
                        .pointerInput(text) { detectTapGestures { actions.emoji(EmojiAction.Insert(text)) } },
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = emoji?.let { catalog.bitmap(it) }
                    if (bmp != null) {
                        val side = cellH * EMOJI_FRACTION
                        Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(side.dp))
                    } else {
                        // Text emoticons, and a recent emoji the catalog cannot draw, are shown as text.
                        BasicText(text, style = TextStyle(fontFamily = Brand.uiFont, fontSize = (m.h(KeyGrid.LABEL_CAP_HEIGHT) / CapMetrics.CAP_RATIO * 0.8f).sp, color = KeyColors.label))
                    }
                }
            }
        }
    }
    val cells = listOf<Pair<String, EmojiAction>>(
        "abc" to EmojiAction.Letters,
        "recent" to EmojiAction.Category(EmojiCategory.RECENT),
        "smileys" to EmojiAction.Category(EmojiCategory.SMILEYS),
        "people" to EmojiAction.Category(EmojiCategory.PEOPLE),
        "celebration" to EmojiAction.Category(EmojiCategory.CELEBRATION),
        "food" to EmojiAction.Category(EmojiCategory.FOOD),
        "travel" to EmojiAction.Category(EmojiCategory.TRAVEL),
        "symbols" to EmojiAction.Category(EmojiCategory.SYMBOLS),
        "text" to EmojiAction.Category(EmojiCategory.TEXT),
        "bksp" to EmojiAction.Backspace,
    )
    val glyphs = mapOf(
        "recent" to Layouts.GLYPH_CLOCK, "smileys" to Layouts.GLYPH_SMILE, "people" to Layouts.GLYPH_PEOPLE,
        "celebration" to Layouts.GLYPH_BALLOON, "food" to Layouts.GLYPH_PIZZA, "travel" to Layouts.GLYPH_CAR,
        "symbols" to Layouts.GLYPH_HEART, "bksp" to Layouts.GLYPH_BACKSPACE,
    )
    cells.forEachIndexed { i, (name, action) ->
        val active = action is EmojiAction.Category && action.category == category
        val fill = when {
            active -> accent
            name == "abc" || name == "bksp" -> KeyColors.function
            else -> KeyColors.dark
        }
        Box(
            Modifier
                .at(m.x(KeyGrid.row1Left(i)), panelTop + m.yInPanel(KeyGrid.rowTop(3)), m.w(KeyGrid.ROW1_KEY_W), m.h(KeyGrid.KEY_H))
                .background(fill)
                .testTag("kb_emoji_cat_$name")
                .semantics { selected = active }
                .pointerInput(action) { detectTapGestures { actions.emoji(action) } },
            contentAlignment = Alignment.Center,
        ) {
            val glyph = glyphs[name]
            val style = if (glyph != null) {
                TextStyle(fontFamily = Brand.iconFont, fontSize = m.h(ICON_PHYS).sp, color = KeyColors.label)
            } else {
                TextStyle(fontFamily = Brand.uiFont, fontSize = (m.h(KeyGrid.SYMBOLS_LABEL_DIGIT_H) / CapMetrics.CAP_RATIO).sp, color = KeyColors.label)
            }
            BasicText(glyph ?: if (name == "text") ";-)" else name, style = style)
        }
    }
}

private const val ROWS = 4
private const val COLUMNS_VISIBLE = 7.5f

/** The artwork's side as a fraction of the cell height (approximation, H7). */
private const val EMOJI_FRACTION = 0.55f
