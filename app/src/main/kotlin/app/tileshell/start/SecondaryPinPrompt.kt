package app.tileshell.start

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.tiles.TileSize
import app.tileshell.tiles.api.SecondaryTiles
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.LocalStartTheme
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.Scale
import app.tileshell.ui.tokens.ShellType
import app.tileshell.ui.tokens.StartGrid

/**
 * The secondary-tile pin confirmation (H22 approximation, phase 02 Decisions "Untagged UI"): a W10M-style dialog band
 * at the TOP of the screen holding the requesting app's name, the tile's display name, a preview at the requested
 * size and the buttons "pin" / "cancel". Windows: "the system displays a dialog box asking the user to confirm"
 * (R5 §1.9) — an app can never pin a tile on its own.
 *
 * The band is drawn only when there is a request to answer: the host puts it over Start, between the drawn status bar
 * and nav bar (the bar rule), and passes [SecondaryTiles.pending]'s value. Answering it calls
 * [SecondaryTiles.accept] / [SecondaryTiles.decline] and then [onDone] with what the user chose, so the host can
 * restore whatever it suspended while the band was up.
 */
@Composable
fun SecondaryPinPrompt(
    request: SecondaryTiles.PendingRequest,
    modifier: Modifier = Modifier,
    onDone: (accepted: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val theme = LocalStartTheme.current
    val density = LocalDensity.current
    val widthPx = remember(density) { Scale.portraitWidthPx(context).toFloat() }
    val grid = remember(widthPx, theme.mediumColumns) { StartGrid(widthPx, theme.mediumColumns) }

    // The preview is the real tile at the size the app asked for (R6 §1.1 spans; W10M had no large tile).
    val previewW = when (request.size) {
        TileSize.SMALL -> grid.smallPx
        TileSize.MEDIUM -> grid.mediumPx
        TileSize.WIDE -> grid.widePx
    }
    val previewH = if (request.size == TileSize.SMALL) grid.smallPx else grid.mediumPx
    val logo = remember(request.logoFile?.path, previewW) {
        val info = SecondaryTiles.SecondaryTileInfo(
            request.owner, request.tileId, request.displayName, "", request.size, request.showName, request.logoFile,
        )
        SecondaryTiles.logo(info, (previewW * 0.52f).toInt().coerceAtLeast(24))
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)
            .testTag("secondary_pin_prompt"),
    ) {
        BasicText(
            request.ownerLabel,
            style = ShellType.subtitle.copy(color = colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("secondary_pin_app"),
        )
        Spacer(Modifier.height(2.dp))
        BasicText("wants to pin a tile to Start", style = ShellType.body.copy(color = colors.subtleText))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileView(
                model = TileModel(
                    id = "pinprompt:${request.key.id}",
                    label = if (request.showName) request.displayName else "",
                    size = request.size,
                    icon = logo?.let { TileIcons.Icon(it, monochrome = false) },
                    fallbackGlyph = Glyph.APPS,
                    content = null,
                    badge = 0,
                    unassigned = false,
                ),
                widthDp = with(density) { previewW.toDp() },
                heightDp = with(density) { previewH.toDp() },
                accent = colors.accent,
                tileAlpha = 1f,
                pressStyle = theme.pressStyle,
                onTap = {},
                modifier = Modifier.testTag("secondary_pin_preview"),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                BasicText(
                    request.displayName,
                    style = ShellType.body.copy(color = colors.text),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("secondary_pin_name"),
                )
                BasicText(sizeLabel(request.size), style = ShellType.caption.copy(color = colors.subtleText))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            DialogButton("pin", "secondary_pin_accept", Modifier.weight(1f)) {
                SecondaryTiles.accept()
                onDone(true)
            }
            Spacer(Modifier.width(10.dp))
            DialogButton("cancel", "secondary_pin_cancel", Modifier.weight(1f)) {
                SecondaryTiles.decline()
                onDone(false)
            }
        }
    }
}

private fun sizeLabel(size: TileSize) = when (size) {
    TileSize.SMALL -> "small tile"
    TileSize.MEDIUM -> "medium tile"
    TileSize.WIDE -> "wide tile"
}

/** W10M message-dialog button: a flat rectangle with a 1-epx outline and lowercase body text (H22 approximation). */
@Composable
private fun DialogButton(label: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalShellColors.current
    Box(modifier) {
        PressRow(onClick, Modifier.fillMaxWidth().height(34.dp).border(1.dp, colors.text.copy(alpha = 0.7f)).testTag(tag)) {
            BasicText(
                label,
                style = ShellType.body.copy(color = colors.text, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            )
        }
    }
}
