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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tileshell.apps.AppCatalog
import app.tileshell.apps.AppEntry
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.prefs.ThemeMode
import app.tileshell.tiles.GridPack
import app.tileshell.tiles.LayoutStore
import app.tileshell.tiles.Placement
import app.tileshell.tiles.ShellTiles
import app.tileshell.tiles.Sized
import app.tileshell.tiles.Slot
import app.tileshell.tiles.SlotDefaults
import app.tileshell.tiles.SlotResolver
import app.tileshell.tiles.RecentApp
import app.tileshell.tiles.RecentPromotion
import app.tileshell.tiles.TileKey
import app.tileshell.tiles.TileSize
import app.tileshell.tiles.engine.BadgeStore
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.api.SecondaryTiles
import app.tileshell.tiles.engine.TileContent
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.LocalStartTheme
import app.tileshell.ui.motion.Motion
import app.tileshell.ui.tokens.Scale
import app.tileshell.ui.tokens.ShellType
import app.tileshell.ui.tokens.StartGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What a tap on a tile resolves to. */
sealed interface TileTarget {
    data class App(val entry: AppEntry) : TileTarget
    data class Unassigned(val slot: Slot) : TileTarget
    data class Shell(val name: String) : TileTarget
    /** A live folder: a tap expands or collapses it (R6 §1.6.6). */
    data class Folder(val folderId: String) : TileTarget
    /** An app's secondary tile (phase 02 build task 6): launches its owner with the tile's arguments. */
    data class Secondary(val owner: String, val tileId: String) : TileTarget
}

data class PlacedTile(val key: TileKey, val model: TileModel, val target: TileTarget, val xPx: Float, val yPx: Float, val wPx: Float, val hPx: Float)

/** Exit / entrance animation state driven by the host (R3 A11). */
data class StartAnimation(val exitElapsedMs: Float? = null, val exitTappedId: String? = null, val entranceElapsedMs: Float? = null)

/** Bottom tile row height: 1.5 small tiles (INDEX Change Log, Jeremy's second amendment 2026-09-17). */
fun dockTileHeight(grid: StartGrid): Float = grid.smallPx * 1.5f

/**
 * Builds a tile's drawable model from a key. Phase 01's tile content rules unchanged; phase 02 adds folder
 * faces and secondary tiles.
 */
class TileFactory(
    private val context: Context,
    private val resolver: SlotResolver,
    private val catalog: AppCatalog,
    private val layout: LayoutStore.Layout,
    private val badges: Map<String, Int>,
    private val content: Map<String, TileContent>,
    private val expandedFolder: String?,
    private val secondaries: Map<String, SecondaryTiles.SecondaryTileInfo>,
) {
    fun place(key: TileKey, size: TileSize, x: Float, y: Float, wPx: Float, hPx: Float, idPrefix: String = "", live: Boolean = true): PlacedTile {
        val iconPx = (minOf(wPx, hPx) * 0.52f).toInt().coerceAtLeast(24)
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
                PlacedTile(key, model, entry?.let { TileTarget.App(it) } ?: TileTarget.Unassigned(key.slot), x, y, wPx, hPx)
            }
            is TileKey.AppTile -> {
                val entry = catalog.find(key.component)
                val model = TileModel(idPrefix + key.id, entry?.label ?: key.component.packageName, size,
                    entry?.let { TileIcons.load(context, it, iconPx) }, Glyph.APPS,
                    if (live) content[LiveTileEngine.packageKey(key.component.packageName)] else null,
                    badges[key.component.packageName] ?: 0, entry == null)
                PlacedTile(key, model, entry?.let { TileTarget.App(it) } ?: TileTarget.Shell("missing"), x, y, wPx, hPx)
            }
            is TileKey.FolderTile -> {
                val folder = layout.folders[key.folderId]
                val members = folder?.members.orEmpty()
                val face = FolderFace(
                    minis = members.map { mini(it.key, iconPx) },
                    expanded = expandedFolder == key.folderId,
                    count = members.size,
                    live = members.firstNotNullOfOrNull { contentFor(it.key) },
                )
                val model = TileModel(idPrefix + key.id, folder?.name ?: "", size, null, null, null, 0, false, face)
                PlacedTile(key, model, TileTarget.Folder(key.folderId), x, y, wPx, hPx)
            }
            is TileKey.SecondaryTile -> {
                // Build task 6's seam: the record carries the name, the logo and the requested size; the faces
                // and the badge come out of the same maps every other tile reads, under the seam's keys.
                val info = secondaries[key.id]
                val model = TileModel(
                    id = idPrefix + key.id,
                    label = if (info?.showName == true) info.displayName else "",
                    size = size,
                    icon = info?.let { SecondaryTiles.logo(it, iconPx) }?.let { TileIcons.Icon(it, monochrome = false) },
                    fallbackGlyph = Glyph.APPS,
                    content = if (live) content[SecondaryTiles.contentKey(key.owner, key.tileId)] else null,
                    badge = badges[SecondaryTiles.badgeKey(key.owner, key.tileId)] ?: 0,
                    unassigned = info == null,
                )
                PlacedTile(key, model, TileTarget.Secondary(key.owner, key.tileId), x, y, wPx, hPx)
            }
            is TileKey.ShellTile -> {
                val (label, glyph, feed) = when (key.name) {
                    ShellTiles.WEATHER -> Triple("Weather", Glyph.WEATHER_PARTLY, LiveTileEngine.WEATHER)
                    ShellTiles.SETTINGS -> Triple("Start settings", Glyph.SETTINGS, null)
                    ShellTiles.CORTANA -> Triple(Brand.ASSISTANT_NAME, Glyph.MIC_FILLED, null)
                    else -> Triple(key.name, Glyph.APPS, null)
                }
                PlacedTile(
                    key,
                    TileModel(
                        idPrefix + key.id, label, size, null, glyph,
                        if (live) feed?.let { content[it] } else null, 0, false,
                        shellFace = if (key.name == ShellTiles.CORTANA) ShellTiles.CORTANA else null,
                    ),
                    TileTarget.Shell(key.name), x, y, wPx, hPx,
                )
            }
        }
    }

    private fun contentFor(key: TileKey): TileContent? = when (key) {
        is TileKey.SlotTile -> when (key.slot) {
            Slot.PHOTOS -> content[LiveTileEngine.PHOTOS]
            Slot.CALENDAR -> content[LiveTileEngine.CALENDAR]
            Slot.MUSIC -> content[LiveTileEngine.MUSIC]
            else -> resolver.resolve(key.slot, layout.explicitSlots)?.let { content[LiveTileEngine.packageKey(it.component.packageName)] }
        }
        is TileKey.AppTile -> content[LiveTileEngine.packageKey(key.component.packageName)]
        is TileKey.ShellTile -> if (key.name == ShellTiles.WEATHER) content[LiveTileEngine.WEATHER] else null
        else -> null
    }

    /** One member's mini tile on a folder's face (R6 §1.6.3). */
    fun mini(key: TileKey, iconPx: Int): MiniTile = when (key) {
        is TileKey.SlotTile -> {
            val entry = resolver.resolve(key.slot, layout.explicitSlots)
            MiniTile(entry?.let { TileIcons.load(context, it, iconPx) }, slotGlyph(key.slot))
        }
        is TileKey.AppTile -> MiniTile(catalog.find(key.component)?.let { TileIcons.load(context, it, iconPx) }, Glyph.APPS)
        is TileKey.ShellTile -> MiniTile(null, when (key.name) {
            ShellTiles.WEATHER -> Glyph.WEATHER_PARTLY
            ShellTiles.CORTANA -> Glyph.MIC_FILLED
            else -> Glyph.SETTINGS
        })
        is TileKey.SecondaryTile -> MiniTile(secondaries[key.id]?.let { SecondaryTiles.logo(it, iconPx) }?.let { TileIcons.Icon(it, monochrome = false) }, Glyph.APPS)
        is TileKey.FolderTile -> MiniTile(null, Glyph.APPS)
    }
}

@Composable
fun rememberTileFactory(expandedFolder: String?): Pair<TileFactory, LayoutStore.Layout> {
    val context = LocalContext.current
    val catalog = remember { AppCatalog.get(context) }
    val resolver = remember { SlotResolver(context, catalog) }
    val layout by LayoutStore.get(context).layout.collectAsState()
    val apps by catalog.apps.collectAsState()
    val badges by BadgeStore.counts.collectAsState()
    val content by LiveTileEngine.content.collectAsState()
    // Role slots follow Android's default apps, which change without any package or layout change.
    val defaults by SlotDefaults.generation.collectAsState()
    val secondaries by SecondaryTiles.tiles.collectAsState()
    val factory = remember(layout, apps, badges, content, defaults, expandedFolder, secondaries) {
        TileFactory(context, resolver, catalog, layout, badges, content, expandedFolder, secondaries)
    }
    return factory to layout
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

/** Everything Start needs to hit-test and draw one frame: positions in content px. */
class StartGeometry(
    val grid: StartGrid,
    val topPx: Float,
    val placements: List<Placement>,
    val bandFolder: String?,
    val bandTilePlacement: Placement?,
    val members: List<Placement>,
    /** The band's height RIGHT NOW: the full height times the reveal progress (R6 §1.6.6 / §1.6.7). */
    val bandHeightPx: Float,
    val pageHeightPx: Float,
    val dockKeys: List<TileKey>,
    val dockWidthPx: Float,
    /** The panel's height: the contraction's fixed point is a fraction of the SCREEN, not of this page. */
    val screenHeightPx: Float,
) {
    val fixedPointY: Float get() = screenHeightPx * Edit.FIXED_POINT_Y
    private val pushFromRow: Int? = bandTilePlacement?.let { it.y + it.size.spanY }

    fun xPx(p: Placement): Float = grid.unitX(p.x)
    fun yPx(p: Placement): Float = topPx + grid.unitY(p.y) + if (pushFromRow != null && p.y >= pushFromRow) bandHeightPx else 0f
    fun wPx(size: TileSize): Float = size.spanX.coerceAtMost(grid.unitsAcross) * grid.smallPitchPx - grid.gutterPx
    fun hPx(size: TileSize): Float = size.spanY * grid.smallPitchPx - grid.gutterPx

    /** Top rule of the expanded band, and where its member tiles start (R6 §1.6.5). */
    val bandRuleTopPx: Float
        get() = bandTilePlacement?.let { yPx(it) + hPx(it.size) + grid.mediumPx * Edit.BAND_TOP_RULE } ?: 0f
    val bandMembersTopPx: Float get() = bandRuleTopPx + grid.mediumPx * Edit.BAND_MEMBERS_TOP
    /** The band's bottom rule rides the reveal: at full progress it sits a BAND_BOTTOM_RULE below the members. */
    val bandRuleBottomPx: Float
        get() = bandTilePlacement?.let { yPx(it) + hPx(it.size) + bandHeightPx } ?: 0f
    val bandTopPx: Float get() = bandTilePlacement?.let { yPx(it) + hPx(it.size) } ?: 0f

    fun memberXPx(p: Placement): Float = grid.unitX(p.x)
    fun memberYPx(p: Placement): Float = bandMembersTopPx + grid.unitY(p.y)

    val contentHeightPx: Float
        get() {
            val last = placements.maxOfOrNull { yPx(it) + hPx(it.size) } ?: topPx
            return maxOf(last, bandRuleBottomPx) + grid.gutterPx
        }

    val dockTopPx: Float get() = pageHeightPx - grid.gutterPx - dockTileHeight(grid)

    /** The grid cell a content-space point falls in, skipping the band's own rows. */
    fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val unitX = ((x - grid.leftMarginPx) / grid.smallPitchPx).toInt().coerceIn(0, grid.unitsAcross - 1)
        if (bandTilePlacement != null && y >= bandRuleTopPx && y <= bandRuleBottomPx) return null
        val adjusted = if (pushFromRow != null && y > bandRuleBottomPx) y - bandHeightPx else y
        val unitY = ((adjusted - topPx) / grid.smallPitchPx).toInt()
        if (unitY < 0) return null
        return unitX to unitY
    }

    /** True when the point is inside the expanded band (a drop there joins the folder). */
    fun inBand(y: Float): Boolean = bandTilePlacement != null && y >= bandRuleTopPx && y <= bandRuleBottomPx

    fun bandIndexAt(x: Float, y: Float): Int {
        val unitX = ((x - grid.leftMarginPx) / grid.smallPitchPx).toInt().coerceIn(0, grid.unitsAcross - 1)
        val unitY = ((y - bandMembersTopPx) / grid.smallPitchPx).toInt().coerceAtLeast(0)
        return GridPack.indexAt(members, unitX, unitY) ?: GridPack.insertIndexForEmptyCell(members, unitX, unitY)
    }

    /** Which slot of the bottom tile row a screen-space x falls in. */
    fun dockIndexAt(x: Float): Int {
        if (dockKeys.isEmpty()) return 0
        val i = ((x - grid.leftMarginPx) / (dockWidthPx + grid.gutterPx)).toInt()
        return i.coerceIn(0, dockKeys.size)
    }
}

@Composable
fun StartPage(
    scroll: ScrollState,
    animation: StartAnimation,
    edit: StartEditState,
    onTileTap: (PlacedTile) -> Unit,
) {
    val context = LocalContext.current
    val theme = LocalStartTheme.current
    val colors = LocalShellColors.current
    val density = LocalDensity.current
    val store = remember { LayoutStore.get(context) }
    val (factory, layout) = rememberTileFactory(edit.expandedFolder)
    val widthPx = Scale.portraitWidthPx(context).toFloat()
    val screenHeightPx = Scale.portraitHeightPx(context).toFloat()
    val grid = StartGrid(widthPx, theme.mediumColumns)
    val topPx = StartGrid.GRID_TOP_EPX * (widthPx / Scale.CANVAS_EPX)
    val scope = rememberCoroutineScope()

    val background = rememberBackground(context, theme.backgroundUri)
    val tileAlpha = if (background != null) 1f - theme.transparency * 0.8f else 1f

    // Start exit scale (R3 A11) and entrance scale/alpha.
    val exit = animation.exitElapsedMs
    val entrance = animation.entranceElapsedMs
    val launchScale = when {
        exit != null -> Motion.sampleFrames(Motion.exitScaleFrames, exit)
        entrance != null -> Motion.sampleFrames(Motion.entranceScaleFrames, entrance)
        else -> 1f
    }
    val gridAlpha = entrance?.let { Motion.entranceAlpha(it) } ?: 1f

    // Edit-mode contraction (R6 §1.1.2-§1.1.3): centres to 0.90 of their distance from the fixed point, and
    // every tile but the held one to 0.835 about its own centre. The container carries the 0.90; each tile
    // carries the rest, so both numbers land exactly.
    val pitchScale = 1f - (1f - Edit.PITCH_SCALE) * edit.scaleProgress
    val otherScale = (1f - (1f - Edit.OTHER_TILE_SCALE) * edit.scaleProgress) / pitchScale
    val heldScale = 1f / pitchScale
    val tileDim = Edit.tileDim(theme.theme).let { it.copy(alpha = it.alpha * edit.dimProgress) }
    val wallDim = Edit.wallpaperDim(theme.theme).let { it.copy(alpha = it.alpha * edit.dimProgress) }

    // R6 §1.6.6 / §1.6.7 (H15 / H16): the band is revealed top to bottom in ≈375 ms and folded away in ≈133 ms,
    // and Start scrolls so the band fits, then scrolls back when it closes.
    var shownFolder by remember { mutableStateOf<String?>(null) }
    val bandReveal = remember { Animatable(0f) }
    var scrollBefore by remember { mutableIntStateOf(0) }
    LaunchedEffect(edit.expandedFolder) {
        val target = edit.expandedFolder
        if (target != null) {
            if (shownFolder == null) scrollBefore = scroll.value
            shownFolder = target
            bandReveal.animateTo(1f, tween(Edit.FOLDER_EXPAND_MS))
        } else if (shownFolder != null) {
            bandReveal.animateTo(0f, tween(Edit.FOLDER_COLLAPSE_MS))
            shownFolder = null
            scroll.animateScrollTo(scrollBefore.coerceIn(0, scroll.maxValue), tween(Edit.FOLDER_SCROLL_BACK_MS))
        }
    }

    val drag = edit.drag
    // The last app you opened is shown in the row above the bottom tile row (INDEX Change Log
    // 2026-09-21 item 7). It is applied HERE, on the way to the screen, and never to the stored layout.
    //
    // Not in edit mode, and this is load-bearing rather than a nicety: EditGestures reads the grid as
    // drawn and writes back by INDEX (moveInGrid), so a displayed order that differs from the stored
    // one would move the wrong tile. What you edit is what is saved, always.
    val stored = edit.previewOrder ?: layout.order
    val order =
        if (edit.active) stored
        else remember(stored, RecentApp.promoted) { RecentPromotion.apply(stored, RecentApp.promoted) }
    val placements = remember(order, grid.unitsAcross) { GridPack.pack(order, grid.unitsAcross) }
    val bandFolder = shownFolder?.takeIf { it in layout.folders }
    val bandTile = bandFolder?.let { id -> placements.firstOrNull { (it.key as? TileKey.FolderTile)?.folderId == id } }
    val members = remember(bandFolder, layout.folders, grid.unitsAcross) {
        bandFolder?.let { GridPack.pack(layout.folders[it]!!.members, grid.unitsAcross) }.orEmpty()
    }
    val dockKeys = layout.dock.take(grid.unitsAcross)
    val rowWidth = widthPx - grid.leftMarginPx - grid.rightMarginPx
    val dockW = if (dockKeys.isEmpty()) 0f else (rowWidth - grid.gutterPx * (dockKeys.size - 1)) / dockKeys.size

    // The bottom tile row holds one row: turning "show more tiles" off moves what no longer fits to the end of
    // the grid instead of hiding it (phase 02 Decisions, E9).
    LaunchedEffect(grid.unitsAcross) { store.applyRowCapacity(grid.unitsAcross) }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).testTag("start_page")) {
        val pageHeightPx = with(density) { maxHeight.toPx() }
        val geo = StartGeometry(
            grid, topPx, placements, bandFolder, bandTile, members,
            if (bandTile != null) Edit.bandHeight(grid, GridPack.rowCount(members)) * bandReveal.value else 0f,
            pageHeightPx, dockKeys, dockW, screenHeightPx,
        )
        // Scroll the band into view as it opens (R6 §1.6.2: a new folder is scrolled into view; §1.6.6: expanding
        // auto-scrolls so the band fits).
        LaunchedEffect(bandFolder, geo.bandRuleBottomPx.toInt() / 16) {
            if (bandFolder == null) return@LaunchedEffect
            val visibleBottom = scroll.value + pageHeightPx - (if (dockKeys.isEmpty()) 0f else dockTileHeight(grid) + grid.gutterPx * 2)
            val overshoot = geo.bandRuleBottomPx - visibleBottom
            if (overshoot > 0) scroll.scrollTo((scroll.value + overshoot.toInt()).coerceIn(0, scroll.maxValue))
        }

        val geoState = rememberUpdatedState(geo)
        val pitchState = rememberUpdatedState(pitchScale)
        Box(Modifier.fillMaxSize().startEditGestures(edit, geoState, store, scroll, scope, pitchState)) {
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
        // R6 §1.1.5: in edit mode the wallpaper dims too, further than the tiles do.
        if (edit.dimProgress > 0f) Box(Modifier.fillMaxSize().background(wallDim))

        Box(
            // The layer sits on the viewport (before verticalScroll), so the launch scale is about the screen
            // centre (R3 A11) and the edit contraction about R6 §1.1.3's fixed point.
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = launchScale * pitchScale
                    scaleY = launchScale * pitchScale
                    alpha = gridAlpha
                    // The fixed point is a fraction of the SCREEN's height (R6 §1.1.3); this layer is the page,
                    // which ends above the drawn nav bar, so the fraction is converted into the page's own space.
                    transformOrigin = if (edit.scaleProgress > 0f)
                        TransformOrigin(Edit.FIXED_POINT_X, screenHeightPx * Edit.FIXED_POINT_Y / pageHeightPx)
                    else TransformOrigin.Center
                }
                // Edit mode owns the gestures, so Start scrolls under a drag only when Start decides to.
                .verticalScroll(scroll, enabled = !edit.active),
        ) {
            Box(Modifier.fillMaxWidth().height(with(density) { geo.contentHeightPx.toDp() })) {
                placements.forEach { p ->
                    if (drag != null && p.key == drag.key) return@forEach // the dragged tile is drawn at the finger
                    val tile = factory.place(p.key, p.size, geo.xPx(p), geo.yPx(p), geo.wPx(p.size), geo.hPx(p.size))
                    val held = edit.selected == p.key
                    val row = ((tile.yPx - scroll.value) / grid.pitchPx).toInt().coerceAtLeast(0)
                    GridTile(tile, held, heldScale, otherScale, tileDim, edit, colors.accent, tileAlpha, theme.pressStyle,
                        exitAlpha(animation, row, tile.model.id), onTileTap)
                }
                if (bandTile != null && bandFolder != null) {
                    // The reveal is a clip, so the rows appear top to bottom and the tiles below slide with it.
                    Box(
                        Modifier
                            .offset { IntOffset(0, geo.bandTopPx.toInt()) }
                            .fillMaxWidth()
                            .height(with(density) { geo.bandHeightPx.coerceAtLeast(0f).toDp() })
                            .clipToBounds(),
                    ) {
                        FolderBand(geo, factory, layout.folders[bandFolder]!!, edit, store, scroll.value, colors.accent, tileAlpha, theme.pressStyle, heldScale, otherScale, tileDim, onTileTap)
                    }
                }
                // The dragged tile: 1.00, undimmed, at the finger with its grab offset (R6 §1.3.1).
                if (drag != null) {
                    val size = layout.sizeOf(drag.key) ?: TileSize.MEDIUM
                    val w = if (drag.fromRow) dockW else geo.wPx(size)
                    val h = if (drag.fromRow) dockTileHeight(grid) else geo.hPx(size)
                    val at = Coords(geo, scroll.value, pitchScale).toContent(drag.pointer)
                    val tile = factory.place(drag.key, size, at.x - drag.grab.x * w, at.y - drag.grab.y * h, w, h, idPrefix = "drag:")
                    TileView(
                        model = tile.model,
                        widthDp = with(density) { w.toDp() },
                        heightDp = with(density) { h.toDp() },
                        accent = colors.accent,
                        tileAlpha = tileAlpha,
                        pressStyle = theme.pressStyle,
                        onTap = {},
                        interactive = false,
                        modifier = Modifier
                            .offset { IntOffset(tile.xPx.toInt(), tile.yPx.toInt()) }
                            .graphicsLayer { scaleX = heldScale; scaleY = heldScale },
                    )
                }
                // The discs ride on the held tile and vanish in the exit's first frame (R6 §1.2.7).
                val selectedPlacement = placements.firstOrNull { it.key == edit.selected }
                val selectedMember = members.firstOrNull { it.key == edit.selected }
                if (edit.active && !edit.exiting && drag == null && selectedPlacement != null) {
                    Discs(
                        xPx = geo.xPx(selectedPlacement), yPx = geo.yPx(selectedPlacement),
                        wPx = geo.wPx(selectedPlacement.size), hPx = geo.hPx(selectedPlacement.size),
                        size = selectedPlacement.size, theme = theme.theme, counterScale = heldScale,
                    )
                } else if (edit.active && !edit.exiting && drag == null && selectedMember != null) {
                    // A member of the expanded folder is held: its discs sit on the band tile's corners.
                    Discs(
                        xPx = geo.memberXPx(selectedMember), yPx = geo.memberYPx(selectedMember),
                        wPx = geo.wPx(selectedMember.size), hPx = geo.hPx(selectedMember.size),
                        size = selectedMember.size, theme = theme.theme, counterScale = heldScale,
                    )
                }
            }
        }

        // The fixed bottom tile row: does not scroll; fades with the last visible row on exit.
        if (dockKeys.isNotEmpty()) {
            val lastRow = (pageHeightPx / grid.pitchPx).toInt()
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = launchScale; scaleY = launchScale; alpha = gridAlpha }.testTag("bottom_tile_row")) {
                dockKeys.forEachIndexed { i, key ->
                    if (drag != null && key == drag.key) return@forEachIndexed
                    val x = grid.leftMarginPx + i * (dockW + grid.gutterPx)
                    val tile = factory.place(key, TileSize.SMALL, x, geo.dockTopPx, dockW, dockTileHeight(grid), idPrefix = "dock:")
                    val held = edit.selected == key
                    GridTile(tile, held, 1f, if (edit.active) Edit.OTHER_TILE_SCALE else 1f, tileDim, edit, colors.accent, tileAlpha, theme.pressStyle,
                        exitAlpha(animation, lastRow, tile.model.id), onTileTap)
                }
                val selectedDock = dockKeys.indexOfFirst { it == edit.selected }
                if (edit.active && !edit.exiting && drag == null && selectedDock >= 0) {
                    Discs(
                        xPx = grid.leftMarginPx + selectedDock * (dockW + grid.gutterPx), yPx = geo.dockTopPx,
                        wPx = dockW, hPx = dockTileHeight(grid), size = TileSize.SMALL, theme = theme.theme, counterScale = 1f,
                    )
                }
            }
        }

        }

        // Edit mode's drivers: the entry/exit motion, the dwell, and the edge auto-scroll while dragging.
        EditMotion(edit)
        DwellTimer(edit, store, layout)
        DragAutoScroll(edit, geo, scroll)
    }
}

/** R6 §1.1.8 / §1.1.9 and §1.5.2 / §1.5.3: the entry and exit run on the frame clock, not on guesswork. */
@Composable
private fun EditMotion(edit: StartEditState) {
    LaunchedEffect(edit.motionToken) {
        if (edit.motionToken == 0) return@LaunchedEffect
        if (edit.exiting) {
            // R6 §1.5.2: the exit starts 150 ± 17 ms after TOUCH-UP. Sleeping 150 ms from here would add
            // however long this coroutine took to be scheduled — measured at 169-185 ms on the emulator —
            // so the wait is what is left of the 150 ms from the touch-up's own event time.
            val since = android.os.SystemClock.uptimeMillis() - edit.exitRequestedUptimeMs
            delay((Edit.EXIT_DELAY_MS - since).coerceAtLeast(0L))
            val scale0 = edit.scaleProgress
            val dim0 = edit.dimProgress
            val start = androidx.compose.runtime.withFrameMillis { it }
            // The measurement of that 150 ms, on one clock: the touch-up's own event time against the frame
            // this exit first draws in. Both are uptime millis, so QA reads the interval out of the ring
            // buffer instead of hunting for a touch indicator the emulator never renders.
            Diagnostics.add("edit", "exit first frame at uptime=$start, " +
                "${start - edit.exitRequestedUptimeMs} ms after touch-up (R6 §1.5.2: 150 ± 17 ms)")
            while (true) {
                val t = (androidx.compose.runtime.withFrameMillis { it } - start).toFloat()
                edit.scaleProgress = scale0 * (1f - (t / Edit.EXIT_SCALE_MS).coerceIn(0f, 1f))
                edit.dimProgress = dim0 * (1f - Motion.sampleKeyframes(Edit.exitDimKeyframes, t))
                if (t >= maxOf(Edit.EXIT_SCALE_MS, Edit.EXIT_DIM_MS)) break
            }
            edit.finishExit()
        } else {
            val start = androidx.compose.runtime.withFrameMillis { it }
            while (true) {
                val t = (androidx.compose.runtime.withFrameMillis { it } - start).toFloat()
                edit.scaleProgress = Edit.easeOutProgress(t, Edit.SCALE_MS, Edit.SCALE_HALF_MS)
                edit.dimProgress = Motion.sampleKeyframes(Edit.dimKeyframes, t)
                if (t >= maxOf(Edit.SCALE_MS, Edit.DIM_MS)) break
            }
            edit.scaleProgress = 1f
            edit.dimProgress = 1f
        }
    }
}

/**
 * R6 §1.3.3 approximation (H20 / H5): a tile whose centre sits on another tile holds there for 2000 ms showing
 * the folder feedback; only when the dwell runs out do the tiles there slide down the grid to make room.
 */
@Composable
private fun DwellTimer(edit: StartEditState, store: LayoutStore, layout: LayoutStore.Layout) {
    val hover = edit.hover
    LaunchedEffect(hover, edit.drag?.key) {
        val dragged = edit.drag?.key ?: return@LaunchedEffect
        if (hover == null) return@LaunchedEffect
        // Folders never nest (H23): a dragged folder, or a drop on a folder holding a folder, gets no feedback.
        edit.folderFeedback = dragged !is TileKey.FolderTile
        delay(Edit.DWELL_MS)
        edit.folderFeedback = false
        // The dwell ran out: the target and the tiles after it make room (the reflow preview). A tile carried
        // FORWARD takes the target's place (the target moves back); carried BACKWARD it lands in front of it —
        // otherwise dropping a tile on the one right after it would move nothing at all.
        val current = edit.previewOrder ?: layout.order
        val from = current.indexOfFirst { it.key == dragged }
        val targetAt = current.indexOfFirst { it.key == hover }
        val rest = current.filterNot { it.key == dragged }
        val index = rest.indexOfFirst { it.key == hover }
        if (index >= 0) {
            val size = layout.sizeOf(dragged) ?: TileSize.MEDIUM
            val at = if (from in 0 until targetAt) index + 1 else index
            edit.previewOrder = rest.toMutableList().apply { add(at.coerceIn(0, this.size), Sized(dragged, size)) }
        }
    }
}

/** Dragging near the top or bottom edge scrolls Start, so a tile can be carried past the screen (agent). */
@Composable
private fun DragAutoScroll(edit: StartEditState, geo: StartGeometry, scroll: ScrollState) {
    val dragging = edit.drag != null
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        while (true) {
            val drag = edit.drag ?: break
            val screenY = drag.pointer.y
            val edge = geo.pageHeightPx * 0.12f
            val step = when {
                screenY < edge -> -18f
                screenY > geo.pageHeightPx - edge -> 18f
                else -> 0f
            }
            if (step != 0f) scroll.scrollTo((scroll.value + step.toInt()).coerceIn(0, scroll.maxValue))
            delay(16)
        }
    }
}

private fun exitAlpha(animation: StartAnimation, row: Int, id: String): Float = animation.exitElapsedMs?.let { elapsed ->
    val extra = if (id == animation.exitTappedId) Motion.EXIT_TAPPED_EXTRA_MS else 0
    val start = minOf(row * Motion.EXIT_ROW_STAGGER_MS, Motion.EXIT_TOTAL_MS - Motion.EXIT_ROW_FADE_MS) + extra
    1f - ((elapsed - start) / Motion.EXIT_ROW_FADE_MS).coerceIn(0f, 1f)
} ?: 1f

@Composable
private fun GridTile(
    tile: PlacedTile,
    held: Boolean,
    heldScale: Float,
    otherScale: Float,
    dim: Color,
    edit: StartEditState,
    accent: Color,
    tileAlpha: Float,
    pressStyle: app.tileshell.prefs.PressStyle,
    alpha: Float,
    onTileTap: (PlacedTile) -> Unit,
) {
    val density = LocalDensity.current
    // R6 §1.5.4 (H8): the selection moves between tiles over ≈185 ms — the tapped tile grows and undims while
    // the previous one shrinks and dims. The entry itself is driven by the progress values, not by this.
    val heldness by animateFloatAsState(if (held) 1f else 0f, tween(Edit.SELECT_MS), label = "held")
    // R6 §1.3.2 (H4): tiles that must make room slide, they never fade or jump.
    val x by animateFloatAsState(tile.xPx, tween(Edit.REFLOW_MS, easing = LinearOutSlowInEasing), label = "x")
    val y by animateFloatAsState(tile.yPx, tween(Edit.REFLOW_MS, easing = LinearOutSlowInEasing), label = "y")
    // R6 §1.4 (H6 / H7): a resize grows in ≈170 ms or shrinks in ≈500 ms, and the content is hidden until the
    // rectangle has settled, then fades back in over ≈170 ms.
    val growing = tile.wPx * tile.hPx > (lastArea[tile.model.id] ?: 0f)
    val w by animateFloatAsState(tile.wPx, tween(if (growing) Edit.RESIZE_GROW_MS else Edit.RESIZE_SHRINK_MS), label = "w")
    val h by animateFloatAsState(tile.hPx, tween(if (growing) Edit.RESIZE_GROW_MS else Edit.RESIZE_SHRINK_MS), label = "h")
    val contentAlpha = remember(tile.model.id) { Animatable(1f) }
    // Keyed by tile id and pruned with the tile: left to grow, a tile unpinned and re-pinned at another size
    // came back with a stale area and blanked its content for the length of a resize.
    DisposableEffect(tile.model.id) { onDispose { lastArea.remove(tile.model.id) } }
    LaunchedEffect(tile.model.size) {
        val previous = lastArea.put(tile.model.id, tile.wPx * tile.hPx)
        if (previous == null || previous == tile.wPx * tile.hPx) return@LaunchedEffect
        contentAlpha.snapTo(0f)
        delay(if (tile.wPx * tile.hPx > previous) (Edit.RESIZE_GROW_MS + Edit.RESIZE_BLANK_MS).toLong() else Edit.RESIZE_SHRINK_MS.toLong())
        contentAlpha.animateTo(1f, tween(Edit.RESIZE_FADE_MS))
    }
    val model = if (edit.folderFeedback && edit.hover == tile.key) {
        // R6 §1.6.1 (H10): the target's icon shrinks into a mini tile at its top-left corner while the dragged
        // tile dwells on it, so the folder that is about to be made is visible before the finger lifts.
        tile.model.copy(folder = FolderFace(listOf(MiniTile(tile.model.icon, tile.model.fallbackGlyph)), expanded = false, count = 1))
    } else {
        tile.model
    }
    TileView(
        model = model,
        widthDp = with(density) { w.toDp() },
        heightDp = with(density) { h.toDp() },
        accent = accent,
        tileAlpha = tileAlpha,
        pressStyle = pressStyle,
        onTap = { onTileTap(tile) },
        interactive = !edit.active,
        dim = if (edit.active) dim.copy(alpha = dim.alpha * (1f - heldness)) else Color.Unspecified,
        folderTarget = edit.folderFeedback && edit.hover == tile.key,
        contentAlpha = contentAlpha.value,
        modifier = Modifier
            .offset { IntOffset(x.toInt(), y.toInt()) }
            .graphicsLayer {
                this.alpha = alpha
                val s = otherScale + (heldScale - otherScale) * heldness
                scaleX = s; scaleY = s
            },
    )
}

/** Last drawn area per tile id, so a size change can tell a grow from a shrink (R6 §1.4). */
private val lastArea = HashMap<String, Float>()

/** The two discs on the held tile's top-right and bottom-right corners (R6 §1.2.1-§1.2.3). */
@Composable
private fun Discs(xPx: Float, yPx: Float, wPx: Float, hPx: Float, size: TileSize, theme: ThemeMode, counterScale: Float) {
    val density = LocalDensity.current
    val widthPx = with(density) { 1.dp.toPx() } // 1 epx in px under the shell density
    val discPx = Edit.DISC_EPX * widthPx
    // §1.2.4: disc = theme foreground, glyph = theme background.
    val disc = if (theme == ThemeMode.DARK) Color.White else Color.Black
    val glyph = if (theme == ThemeMode.DARK) Color.Black else Color.White
    // The disc box is 31 epx and carries the held tile's counter-scale, so what lands on the screen is
    // 31 ± 1.5 epx (R6 §1.2.1). It is centred on the tile's DRAWN corner: the held tile stays at 1.00 inside a
    // grid contracted to 0.90, so its drawn corner is further from its centre than its layout corner is.
    val cx = xPx + wPx / 2f
    val cy = yPx + hPx / 2f
    val halfW = wPx / 2f * counterScale
    val halfH = hPx / 2f * counterScale
    EditDisc(Disc.UNPIN, size, discSizeDp, disc, glyph,
        Modifier.offset { IntOffset((cx + halfW - discPx / 2f).toInt(), (cy - halfH - discPx / 2f).toInt()) }
            .graphicsLayer { scaleX = counterScale; scaleY = counterScale })
    EditDisc(Disc.RESIZE, size, discSizeDp, disc, glyph,
        Modifier.offset { IntOffset((cx + halfW - discPx / 2f).toInt(), (cy + halfH - discPx / 2f).toInt()) }
            .graphicsLayer { scaleX = counterScale; scaleY = counterScale })
}

/**
 * The expanded folder band (R6 §1.6.5, H14): a full-width band between two 1-epx rules, showing the wallpaper,
 * with the members at full size on the same column grid, and — in edit mode — the "Name folder" placeholder
 * (§1.7.1, H17) that opens the name text box (§1.7.2, H18).
 */
@Composable
private fun FolderBand(
    geo: StartGeometry,
    factory: TileFactory,
    folder: app.tileshell.tiles.Folder,
    edit: StartEditState,
    store: LayoutStore,
    scrollValue: Int,
    accent: Color,
    tileAlpha: Float,
    pressStyle: app.tileshell.prefs.PressStyle,
    heldScale: Float,
    otherScale: Float,
    dim: Color,
    onTileTap: (PlacedTile) -> Unit,
) {
    val density = LocalDensity.current
    val colors = LocalShellColors.current
    val rule = with(density) { Edit.BAND_RULE_EPX.dp.toPx() }
    val top = geo.bandTopPx
    Box(
        Modifier
            .offset { IntOffset(0, (geo.bandRuleTopPx - top).toInt()) }
            .fillMaxWidth()
            .height(with(density) { rule.toDp() })
            .background(colors.subtleText)
            .testTag("folder_band_top:${folder.id}"),
    )
    Box(
        Modifier
            .offset { IntOffset(0, (geo.bandRuleBottomPx - top - rule).toInt()) }
            .fillMaxWidth()
            .height(with(density) { rule.toDp() })
            .background(colors.subtleText)
            .testTag("folder_band_bottom:${folder.id}"),
    )
    geo.members.forEach { p ->
        if (edit.drag?.key == p.key) return@forEach
        val tile = factory.place(p.key, p.size, geo.memberXPx(p), geo.memberYPx(p) - top, geo.wPx(p.size), geo.hPx(p.size), idPrefix = "member:")
        val held = edit.selected == p.key
        GridTile(tile, held, heldScale, otherScale, dim, edit, accent, tileAlpha, pressStyle, 1f, onTileTap)
    }
    if (edit.active) {
        // The box itself is drawn by the host, above the pivot; the band only says where it goes.
        edit.nameBoxYPx = geo.bandRuleTopPx - scrollValue + geo.grid.mediumPx * 0.02f
        if (!edit.naming) {
            BasicText(
                folder.name ?: "Name folder",
                style = ShellType.caption.copy(color = colors.subtleText),
                modifier = Modifier
                    .offset { IntOffset(geo.grid.leftMarginPx.toInt(), (geo.bandRuleTopPx - top + geo.grid.mediumPx * 0.04f).toInt()) }
                    .testTag("folder_name_placeholder:${folder.id}")
                    .padding(2.dp),
            )
        }
    }
}

/**
 * R6 §1.7.2 (H18): a full-width single-line box with a white fill, ≈0.27 × the tile side tall, with the
 * keyboard up. Drawn by the host ABOVE the pivot, not inside the band: a focused text field asks every
 * scrollable ancestor to bring it into view, and from inside a pager page that swung the pivot over to the
 * app list as soon as the keyboard opened.
 */
@Composable
fun FolderNameBox(initial: String, yPx: Float, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = Scale.portraitWidthPx(context).toFloat()
    val grid = StartGrid(widthPx, LocalStartTheme.current.mediumColumns)
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    // When the box goes, focus has to go with it: left to itself Compose hands focus to the next focusable,
    // which is the app list's search field on the next pivot page, and the pager then brings THAT into view.
    DisposableEffect(Unit) {
        onDispose {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
        }
    }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = ShellType.body.copy(color = Color.Black),
        cursorBrush = SolidColor(Color.Black),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
            onDone(text)
        }),
        modifier = Modifier
            .offset { IntOffset(grid.leftMarginPx.toInt(), yPx.toInt()) }
            .size(
                width = with(density) { (widthPx - grid.leftMarginPx - grid.rightMarginPx).toDp() },
                height = with(density) { (grid.mediumPx * Edit.NAME_BOX_HEIGHT).toDp() },
            )
            .background(Color.White)
            .padding(horizontal = 6.dp)
            .focusRequester(focus)
            .testTag("folder_name_box"),
    )
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
