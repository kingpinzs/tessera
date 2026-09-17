package app.tileshell.start

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tileshell.apps.AppCatalog
import app.tileshell.apps.AppEntry
import app.tileshell.bars.BarMetrics
import app.tileshell.brand.Glyph
import app.tileshell.tiles.LayoutStore
import app.tileshell.tiles.ShellTiles
import app.tileshell.tiles.Slot
import app.tileshell.tiles.SlotResolver
import app.tileshell.tiles.TileKey
import app.tileshell.tiles.TileSize
import app.tileshell.tiles.engine.BadgeStore
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.LocalStartTheme
import app.tileshell.ui.motion.Motion
import app.tileshell.ui.tokens.Scale
import app.tileshell.ui.tokens.StartGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a tap on a tile resolves to. */
sealed interface TileTarget {
    data class App(val entry: AppEntry) : TileTarget
    data class Unassigned(val slot: Slot) : TileTarget
    data class Shell(val name: String) : TileTarget
}

data class PlacedTile(val model: TileModel, val target: TileTarget, val xPx: Float, val yPx: Float, val wPx: Float, val hPx: Float)

/** Exit / entrance animation state driven by the host (R3 A11). */
data class StartAnimation(val exitElapsedMs: Float? = null, val exitTappedId: String? = null, val entranceElapsedMs: Float? = null)

/** Bottom tile row height: 1.5 small tiles (INDEX Change Log, Jeremy's second amendment 2026-09-17). */
fun dockTileHeight(grid: StartGrid): Float = grid.smallPx * 1.5f

/** Start's tiles: the scrolling grid and the fixed bottom tile row (INDEX Change Log 2026-09-17). */
data class StartTiles(val grid: List<PlacedTile>, val dock: List<PlacedTile>)

@Composable
fun rememberPlacedTiles(): StartTiles {
    val context = LocalContext.current
    val theme = LocalStartTheme.current
    val catalog = remember { AppCatalog.get(context) }
    val resolver = remember { SlotResolver(context, catalog) }
    val layout by LayoutStore.get(context).layout.collectAsState()
    val apps by catalog.apps.collectAsState()
    val badges by BadgeStore.counts.collectAsState()
    val content by LiveTileEngine.content.collectAsState()
    val widthPx = remember(LocalDensity.current) { Scale.portraitWidthPx(context).toFloat() }
    val grid = StartGrid(widthPx, theme.mediumColumns)
    val topPx = StartGrid.GRID_TOP_EPX * (widthPx / Scale.CANVAS_EPX)

    return remember(layout, apps, badges, content, grid) {
        fun place(key: TileKey, size: TileSize, x: Float, y: Float, idPrefix: String, live: Boolean, wPx: Float? = null, hPx: Float? = null): PlacedTile {
            val w = wPx ?: (size.spanX * grid.smallPitchPx - grid.gutterPx)
            val h = hPx ?: (size.spanY * grid.smallPitchPx - grid.gutterPx)
            val iconPx = (minOf(w, h) * 0.52f).toInt().coerceAtLeast(24)
            return when (key) {
                is TileKey.SlotTile -> {
                    val entry = resolver.resolve(key.slot, layout.explicitSlots)
                    val feedKey = when (key.slot) {
                        Slot.PHOTOS -> LiveTileEngine.PHOTOS
                        Slot.CALENDAR -> LiveTileEngine.CALENDAR
                        Slot.MUSIC -> LiveTileEngine.MUSIC
                        else -> null
                    }
                    val pkgContent = entry?.let { content[LiveTileEngine.packageKey(it.component.packageName)] }
                    val model = TileModel(
                        id = idPrefix + key.id,
                        label = entry?.label ?: key.slot.label,
                        size = size,
                        icon = entry?.let { TileIcons.load(context, it, iconPx) },
                        fallbackGlyph = slotGlyph(key.slot),
                        content = if (live) feedKey?.let { content[it] } ?: pkgContent else null,
                        badge = entry?.let { badges[it.component.packageName] } ?: 0,
                        unassigned = entry == null,
                    )
                    PlacedTile(model, entry?.let { TileTarget.App(it) } ?: TileTarget.Unassigned(key.slot), x, y, w, h)
                }
                is TileKey.AppTile -> {
                    val entry = catalog.find(key.component)
                    val model = TileModel(idPrefix + key.id, entry?.label ?: key.component.packageName, size,
                        entry?.let { TileIcons.load(context, it, iconPx) }, Glyph.APPS,
                        if (live) content[LiveTileEngine.packageKey(key.component.packageName)] else null,
                        badges[key.component.packageName] ?: 0, entry == null)
                    PlacedTile(model, entry?.let { TileTarget.App(it) } ?: TileTarget.Shell("missing"), x, y, w, h)
                }
                is TileKey.ShellTile -> {
                    val (label, glyph, feed) = when (key.name) {
                        ShellTiles.WEATHER -> Triple("Weather", Glyph.WEATHER_PARTLY, LiveTileEngine.WEATHER)
                        ShellTiles.SETTINGS -> Triple("Start settings", Glyph.SETTINGS, null)
                        else -> Triple(key.name, Glyph.APPS, null)
                    }
                    PlacedTile(TileModel(idPrefix + key.id, label, size, null, glyph, if (live) feed?.let { content[it] } else null, 0, false), TileTarget.Shell(key.name), x, y, w, h)
                }
            }
        }
        val gridTiles = layout.placements.map { p -> place(p.key, p.size, grid.unitX(p.x), topPx + grid.unitY(p.y), "", live = true) }
        // Bottom tile row (INDEX Change Log 2026-09-17, amended by Jeremy the same day): the row's tiles share the grid width
        // equally, 1.5 small tiles tall, glyph + badge, no label; live previews flip like other tiles (amendment 3). Their y is set when Start lays out.
        val dockKeys = layout.dock.take(grid.unitsAcross)
        val rowWidth = widthPx - grid.leftMarginPx - grid.rightMarginPx
        val dockW = if (dockKeys.isEmpty()) 0f else (rowWidth - grid.gutterPx * (dockKeys.size - 1)) / dockKeys.size
        val dockTiles = dockKeys.mapIndexed { i, key ->
            place(key, TileSize.SMALL, grid.leftMarginPx + i * (dockW + grid.gutterPx), 0f, "dock:", live = true, wPx = dockW, hPx = dockTileHeight(grid))
        }
        StartTiles(gridTiles, dockTiles)
    }
}

private fun slotGlyph(slot: Slot): String = when (slot) {
    Slot.PHONE -> Glyph.PHONE
    Slot.MESSAGING -> Glyph.CHAT
    Slot.BROWSER -> Glyph.GLOBE
    Slot.MAIL -> Glyph.MAIL
    Slot.MUSIC -> Glyph.MUSIC
    Slot.MAPS -> Glyph.MAP
    Slot.STORE -> Glyph.STORE
    Slot.PHOTOS -> Glyph.IMAGE
    Slot.PEOPLE -> Glyph.PEOPLE
    Slot.CAMERA -> Glyph.CAMERA
    Slot.CALENDAR -> Glyph.CALENDAR
}

@Composable
fun StartPage(
    tiles: StartTiles,
    scroll: ScrollState,
    animation: StartAnimation,
    onTileTap: (PlacedTile) -> Unit,
) {
    val context = LocalContext.current
    val theme = LocalStartTheme.current
    val colors = LocalShellColors.current
    val density = LocalDensity.current
    val widthPx = Scale.portraitWidthPx(context).toFloat()
    val grid = StartGrid(widthPx, theme.mediumColumns)
    val pitchPx = grid.pitchPx
    // Bottom tile row: one small tile tall with one small-tile gutter above and below (INDEX Change Log 2026-09-17).
    val dockHeightPx = if (tiles.dock.isEmpty()) 0f else dockTileHeight(grid) + grid.gutterPx * 2
    val contentHeightPx = (tiles.grid.maxOfOrNull { it.yPx + it.hPx } ?: 0f) + grid.gutterPx + dockHeightPx

    val background = rememberBackground(context, theme.backgroundUri)
    val tileAlpha = if (background != null) 1f - theme.transparency * 0.8f else 1f

    // Start exit scale (R3 A11) and entrance scale/alpha.
    val exit = animation.exitElapsedMs
    val entrance = animation.entranceElapsedMs
    val gridScale = when {
        exit != null -> Motion.sampleFrames(Motion.exitScaleFrames, exit)
        entrance != null -> Motion.sampleFrames(Motion.entranceScaleFrames, entrance)
        else -> 1f
    }
    val gridAlpha = entrance?.let { Motion.sampleFrames(Motion.entranceAlphaFrames, it) } ?: 1f

    fun exitAlpha(row: Int, id: String): Float = exit?.let { elapsed ->
        val extra = if (id == animation.exitTappedId) Motion.EXIT_TAPPED_EXTRA_MS else 0
        val start = minOf(row * Motion.EXIT_ROW_STAGGER_MS, Motion.EXIT_TOTAL_MS - Motion.EXIT_ROW_FADE_MS) + extra
        1f - ((elapsed - start) / Motion.EXIT_ROW_FADE_MS).coerceIn(0f, 1f)
    } ?: 1f

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).testTag("start_page")) {
        val pageHeightPx = with(density) { maxHeight.toPx() }
        if (background != null) {
            Image(
                background, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    // Parallax 0.25 of the tile scroll (R3 A5/A6, X15), same direction as the tiles.
                    translationY = -scroll.value * 0.25f
                    scaleY = 1.3f; scaleX = 1.3f
                    alpha = gridAlpha
                },
            )
        }
        Box(
            // The layer sits on the viewport (before verticalScroll), so scaling is about the screen centre (R3 A11).
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = gridScale; scaleY = gridScale
                alpha = gridAlpha
            }.verticalScroll(scroll),
        ) {
            Box(Modifier.fillMaxWidth().height(with(density) { contentHeightPx.toDp() })) {
                tiles.grid.forEach { t ->
                    val row = ((t.yPx - scroll.value) / pitchPx).toInt().coerceAtLeast(0)
                    TileView(
                        model = t.model,
                        widthDp = with(density) { t.wPx.toDp() },
                        heightDp = with(density) { t.hPx.toDp() },
                        accent = colors.accent,
                        tileAlpha = tileAlpha,
                        pressStyle = theme.pressStyle,
                        onTap = { if (exit == null) onTileTap(t) },
                        modifier = Modifier
                            .offset { IntOffset(t.xPx.toInt(), t.yPx.toInt()) }
                            .graphicsLayer { alpha = exitAlpha(row, t.model.id) },
                    )
                }
            }
        }
        // The fixed bottom tile row: does not scroll; fades with the last visible row on exit.
        if (tiles.dock.isNotEmpty()) {
            val dockY = pageHeightPx - grid.gutterPx - dockTileHeight(grid)
            val lastRow = (pageHeightPx / pitchPx).toInt()
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = gridScale; scaleY = gridScale; alpha = gridAlpha }.testTag("bottom_tile_row")) {
                tiles.dock.forEach { t ->
                    TileView(
                        model = t.model,
                        widthDp = with(density) { t.wPx.toDp() },
                        heightDp = with(density) { t.hPx.toDp() },
                        accent = colors.accent,
                        tileAlpha = tileAlpha,
                        pressStyle = theme.pressStyle,
                        onTap = { if (exit == null) onTileTap(t.copy(yPx = dockY)) },
                        modifier = Modifier
                            .offset { IntOffset(t.xPx.toInt(), dockY.toInt()) }
                            .graphicsLayer { alpha = exitAlpha(lastRow, t.model.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberBackground(context: Context, uri: String?): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
                val target = 2400
                var sample = 1
                while (opts.outHeight / sample > target * 2) sample *= 2
                context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}

@Suppress("unused")
private val transparent = Color.Transparent
