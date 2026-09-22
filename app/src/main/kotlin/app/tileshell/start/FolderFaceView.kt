package app.tileshell.start

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.tiles.TileSize
import app.tileshell.ui.tokens.ShellType
import app.tileshell.ui.tokens.StartGrid

/**
 * A live folder's collapsed face (R6 §1.6.3 medium, §1.6.4 wide, §1.6.5 expanded — all LOW candidates,
 * judged in H12 / H13 / H14):
 * - member icons as mini tiles in a 3-column grid from the top-left (mini ≈0.20, pitch ≈0.31, inset ≈0.07
 *   of the tile side; a 4th member starts the second row, i.e. 3 + 1);
 * - the folder name as the normal tile label, bottom-left;
 * - wide: the mini grid on the left, one member's live content on the right, plus a numeric badge;
 * - expanded: only a centred "^" chevron on the folder's tint.
 */
@Composable
internal fun FolderFaceView(model: TileModel, face: FolderFace, widthDp: Dp, heightDp: Dp) {
    val side = minOf(widthDp, heightDp)
    Box(Modifier.fillMaxSize()) {
        if (face.expanded) {
            // The chevron glyph set has no "up" arrow: the right chevron turned a quarter turn is the "^".
            BasicText(
                Glyph.CHEVRON_RIGHT,
                style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = (side.value * 0.30f).sp, color = Color.White, textAlign = TextAlign.Center),
                modifier = Modifier.align(Alignment.Center).graphicsLayer { rotationZ = -90f },
            )
            return@Box
        }
        val mini = side * Edit.MINI_TILE
        val pitch = side * Edit.MINI_PITCH
        val inset = side * Edit.MINI_INSET
        face.minis.take(9).forEachIndexed { i, m ->
            val col = i % Edit.MINI_COLUMNS
            val row = i / Edit.MINI_COLUMNS
            Box(
                Modifier
                    .offset(x = inset + pitch * col, y = inset + pitch * row)
                    .size(mini)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                if (m.icon != null) {
                    Image(m.icon.bitmap, contentDescription = null, modifier = Modifier.fillMaxSize().padding(mini * 0.12f))
                } else if (m.glyph != null) {
                    BasicText(m.glyph, style = ShellType.caption.copy(fontFamily = Brand.iconFont, fontSize = (mini.value * 0.7f).sp, color = Color.White, textAlign = TextAlign.Center))
                }
            }
        }
        // A wide folder shows one member's live content on the right of the mini grid (R6 §1.6.4, H13).
        val liveFace = face.live?.faces?.firstOrNull()
        if (model.size == TileSize.WIDE && liveFace != null) {
            Box(Modifier.align(Alignment.CenterEnd).size(width = widthDp / 2, height = heightDp)) {
                LiveFace(liveFace, model.copy(label = "", folder = null), widthDp / 2, heightDp)
            }
        }
        if (model.size != TileSize.SMALL) {
            BasicText(
                model.label.ifEmpty { "Folder" },
                style = ShellType.caption.copy(color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = StartGrid.LABEL_INSET_EPX.dp, end = 28.dp, bottom = 5.dp),
            )
            // R6 §1.6.4: the wide folder tile carries a numeric badge (member count).
            if (model.size == TileSize.WIDE) {
                BasicText(
                    face.count.toString(),
                    style = ShellType.caption.copy(color = Color.White),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = StartGrid.LABEL_INSET_EPX.dp, bottom = 5.dp).testTag("foldercount:${model.id}"),
                )
            }
        }
    }
}
