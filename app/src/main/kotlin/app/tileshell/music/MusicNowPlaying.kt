package app.tileshell.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.bars.BarMetrics
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The now-playing screen (phase 10 build task 7), built to **R8's measurements**.
 *
 * Every number below carries its R8 section. The build is **V-2016** per the phase doc's Ruling 1 —
 * the later of the two measurable builds and the only one whose second line carries the album — and
 * **NEEDS-HUMAN row H-M1** is Jeremy looking at the finished screen and ruling V-2016 against V-2015,
 * which is four numbers and a string.
 *
 * ### Anchoring (R8 §1.10), which is the part that is easy to get wrong
 *
 * R8's absolute y values (400 / 444 / 488 / 536 epx) are from a 16:9 canvas and **must not be carried
 * across**. What transfers is the anchoring: the chrome anchors to the TOP, and the nav bar, chevron,
 * transport row and scrubber anchor to the BOTTOM at 48 / 16 / 56 / 96 epx above the nav bar's top
 * edge. R8 proved that by measuring the same three offsets on both a 640-epx and a 731-epx canvas. On
 * the S25U's ~780-epx canvas the extra height falls between the art and the metadata, exactly as §1.10
 * says it must.
 *
 * ### One substitution, stated
 *
 * R8 measured Groove's status bar at 24 epx. This shell draws its OWN status bar at 28 epx (R3 C4) on
 * every page, and one app inside the shell with a 4-epx-shorter status bar would be the odd one out
 * rather than the faithful one. The 48-epx app header below it is R8's, unchanged.
 *
 * ### Artist-art mode is not built
 *
 * R8 §1.3's full-bleed artist-photo mode needs artist photography, which a local library read from
 * MediaStore does not have. Album-art mode is what a phone full of copied files shows, and it is what
 * is built; nothing is approximated in its place.
 */
object NowPlayingMetrics {
    /** R3 C4 (this shell's own bar; R8 §1.1 measured Groove's at 24 epx — see the note above). */
    val STATUS: Dp = BarMetrics.STATUS_EPX.dp
    /** R8 §1.1: the app header, and its background, which the status bar shares with no seam. */
    val HEADER = 48.dp
    val HEADER_BG = Color(0xFF171717)
    /** R8 §1.2: hamburger centre, title left edge, search centre inset from the right. */
    val HAMBURGER_CX = 24.dp
    val TITLE_X = 61.dp
    val SEARCH_INSET = 24.dp
    /** R8 §1.2: title cap 11.0 epx at Segoe UI's 0.70 cap ratio → a ~16-epx semibold face, all caps. */
    val HEADER_TITLE_SP = 16.sp
    val HEADER_GLYPH_EPX = 18

    /** R8 §1.3 (V-2016): art flush under the chrome, square, 16-epx side margins. */
    val ART_SIDE = 16.dp

    /** R8 §1.4: both metadata lines start at a 12-epx origin. */
    val META_X = 12.dp
    /** R8 §1.4 (V-2016): title ≈20 epx, second line ≈15-16 epx, both white, second line "Artist • Album". */
    val TITLE_SP = 20.sp
    val ARTIST_SP = 15.sp
    const val META_SEPARATOR = " • "

    /** R8 §1.5 (V-2016): the scrubber row's centre, 96 epx above the nav bar's top edge. */
    val SCRUB_CY_ABOVE_NAV = 96.dp
    /** R8 §1.6: the transport row's centre, 56 epx above the nav bar. */
    val TRANSPORT_CY_ABOVE_NAV = 56.dp
    /** R8 §1.7: the chevron's centre, 16 epx above the nav bar. */
    val CHEVRON_CY_ABOVE_NAV = 16.dp

    /**
     * R8 §1.4 (V-2016): title band 416.0 → 436.0 and artist band 444.0 → 456.0 against a scrubber
     * centre of 496.0 — so the two lines are held 70 and 46 epx above the scrubber row, which is what
     * survives the move to a taller canvas (§1.10) and what the expanded state re-uses unchanged (§1.9).
     */
    val TITLE_CY_ABOVE_SCRUB = 70.dp
    val ARTIST_CY_ABOVE_SCRUB = 46.dp

    /** R8 §1.5 (V-2016): track 50.4 → 308.8 epx on the 360-epx canvas, 3 epx thick. */
    val TRACK_LEFT = 50.4.dp
    val TRACK_RIGHT_INSET = 51.2.dp
    val TRACK_THICK = 3.dp
    /** R8 §1.5: played 63 % white, unplayed 25 % white — neutral, NOT the accent. */
    val PLAYED = Color(0xA1FFFFFF)
    val UNPLAYED = Color(0x40FFFFFF)
    /** R8 §1.5: a hollow white ring, 18 epx across, ≈2 epx stroke; the page shows through its centre. */
    val THUMB = 18.dp
    val THUMB_STROKE = 2.dp
    /** R8 §1.5: digit cap 8.75 epx → ≈12.5 epx (the Caption class); elapsed at 17.6, total inset 16. */
    val TIME_SP = 12.5.sp
    val ELAPSED_X = 17.6.dp
    val TOTAL_INSET = 16.dp

    /** R8 §1.6: six equal cells of W/6 — proportional, not fixed epx. */
    const val TRANSPORT_CELLS = 6
    /** R8 §1.6: glyph heights, in epx. */
    val GLYPH_H = 15.dp
    val SHUFFLE_H = 14.5.dp
    val REPEAT_H = 20.dp
    val DOTS_SPAN = 17.5.dp
    val DOTS_H = 2.5.dp
    /** R8 §1.6: an active toggle is a filled 35-epx circle of #343434; the glyph stays white. */
    val TOGGLE_PILL = 35.dp
    val TOGGLE_FILL = Color(0xFF343434)

    /** R8 §1.7: the chevron's ink box. */
    val CHEVRON_W = 12.dp
    val CHEVRON_H = 6.5.dp

    /** R8 §1.9: queue row pitch, and the alternating row backgrounds. */
    val QUEUE_ROW = 61.5.dp
    val QUEUE_ALT = Color(0xFF1A1A1A)
    /**
     * R8 §1.9: expanded, the whole block moves up so the title band sits flush under the 72-epx chrome,
     * with its internal spacing unchanged. For V-2016 that puts the scrubber centre 80 epx below the
     * chrome (the title band top is 416 and the scrubber centre 496).
     */
    val SCRUB_CY_BELOW_CHROME_EXPANDED = 80.dp

    val NAV: Dp = BarMetrics.NAV_EPX.dp
}

/**
 * How far along the track the thumb's CENTRE sits, as a fraction of the track's length.
 *
 * R8 §1.5's thumb travel rule: the centre runs from `trackLeft + r` to `trackRight − r` and never
 * overhangs the track. Pure, so the rule R8 verified against three screenshots is verified here too.
 */
fun thumbCentreFraction(positionMs: Long, durationMs: Long, trackLength: Float, radius: Float): Float {
    if (trackLength <= 0f) return 0f
    val travel = (trackLength - 2f * radius).coerceAtLeast(0f)
    val progress = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    return (radius + travel * progress) / trackLength
}

/** m:ss, which is what both of R8's labels show ("0:20", "5:53"). */
fun clockText(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** Which level of the `•••` menu is open (task 9): the two entries, or one of their choice lists. */
private enum class MoreMenu { ROOT, SLEEP, EQUALISER, CROSSFADE }

@Composable
fun NowPlayingPage(onBack: () -> Unit, onWindows: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf<MoreMenu?>(null) }
    androidx.activity.compose.BackHandler(enabled = more != null) { more = null }
    // The elapsed label and the thumb are the only things on this screen that move on their own. Four
    // reads a second is finer than the label's own resolution and far cheaper than a frame callback.
    LaunchedEffect(MusicPlayer.isPlaying, expanded) {
        while (true) {
            MusicPlayer.refreshPosition()
            delay(250)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).testTag("nowplaying_root")) {
        val chromeBottom = NowPlayingMetrics.STATUS + NowPlayingMetrics.HEADER
        val navTop = maxHeight - NowPlayingMetrics.NAV
        val scrubCy =
            if (expanded) chromeBottom + NowPlayingMetrics.SCRUB_CY_BELOW_CHROME_EXPANDED
            else navTop - NowPlayingMetrics.SCRUB_CY_ABOVE_NAV
        val transportCy = scrubCy + (NowPlayingMetrics.SCRUB_CY_ABOVE_NAV - NowPlayingMetrics.TRANSPORT_CY_ABOVE_NAV)
        val chevronCy = scrubCy + (NowPlayingMetrics.SCRUB_CY_ABOVE_NAV - NowPlayingMetrics.CHEVRON_CY_ABOVE_NAV)

        // R8 §1.3 / §1.9: the art is flush under the chrome and square, and in the expanded state the
        // album-art mode removes it entirely — the page is plain black behind the queue.
        if (!expanded) AlbumArt(top = chromeBottom, side = NowPlayingMetrics.ART_SIDE, width = maxWidth)

        Metadata(cy = scrubCy - NowPlayingMetrics.TITLE_CY_ABOVE_SCRUB, style = MetaLine.TITLE)
        Metadata(cy = scrubCy - NowPlayingMetrics.ARTIST_CY_ABOVE_SCRUB, style = MetaLine.ARTIST)
        Scrubber(cy = scrubCy, width = maxWidth)
        TransportRow(cy = transportCy, width = maxWidth, onMore = { more = MoreMenu.ROOT })
        Chevron(cy = chevronCy, width = maxWidth, expanded = expanded) { expanded = !expanded }

        if (expanded) {
            Queue(
                top = chevronCy + NowPlayingMetrics.CHEVRON_CY_ABOVE_NAV,
                bottom = maxHeight - navTop,
            )
        }

        // The chrome last, so nothing can draw over it.
        Chrome(onBack)
        more?.let { level ->
            val density = LocalDensity.current
            // The band rises from just above the transport row, so it covers neither the nav bar nor
            // the row the `•••` sits in.
            val rise = with(density) { (transportCy - NowPlayingMetrics.TOGGLE_PILL / 2).toPx() }
            MusicMenu(
                anchorPx = 0f,
                riseFromPx = rise,
                items = moreEntries(level) { more = it },
                onDismiss = { more = null },
            )
        }
        Box(Modifier.align(Alignment.BottomStart)) {
            app.tileshell.bars.W10mNavBar(onBack = onBack, onWindows = onWindows)
        }
    }
}

/** R8 §1.1 / §1.2: the 24 + 48-epx chrome, the hamburger, "NOW PLAYING" and search on one centre line. */
@Composable
private fun Chrome(onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(NowPlayingMetrics.HEADER_BG).testTag("nowplaying_chrome")) {
        app.tileshell.bars.W10mStatusBar(Modifier.background(NowPlayingMetrics.HEADER_BG))
        Box(Modifier.fillMaxWidth().height(NowPlayingMetrics.HEADER)) {
            // R8 §1.2: the hamburger's CENTRE is at 24 epx, so its box starts half a glyph before it.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = NowPlayingMetrics.HAMBURGER_CX - (NowPlayingMetrics.HEADER_GLYPH_EPX / 2).dp)
                    .clickable(onClick = onBack)
                    .testTag("nowplaying_back"),
            ) {
                BasicText(Glyph.LIST, style = ShellType.body.copy(color = Color.White, fontFamily = Brand.iconFont, fontSize = NowPlayingMetrics.HEADER_GLYPH_EPX.sp))
            }
            BasicText(
                "NOW PLAYING",
                style = ShellType.body.copy(
                    color = Color.White,
                    fontSize = NowPlayingMetrics.HEADER_TITLE_SP,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = NowPlayingMetrics.TITLE_X)
                    .testTag("nowplaying_title"),
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = -(NowPlayingMetrics.SEARCH_INSET - (NowPlayingMetrics.HEADER_GLYPH_EPX / 2).dp))
                    .testTag("nowplaying_search"),
            ) {
                BasicText(Glyph.SEARCH, style = ShellType.body.copy(color = Color.White, fontFamily = Brand.iconFont, fontSize = NowPlayingMetrics.HEADER_GLYPH_EPX.sp))
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.AlbumArt(top: Dp, side: Dp, width: Dp) {
    val context = LocalContext.current
    val albumId = MusicPlayer.albumId
    val px = with(LocalDensity.current) { (width - side * 2).roundToPx() }
    val art by produceState<ImageBitmap?>(null, albumId, px) {
        value = if (albumId == null) null else withContext(Dispatchers.IO) { MusicArt.loadById(context, albumId, px) }
    }
    val box = Modifier
        .offset(x = side, y = top)
        .size(width - side * 2)
        .testTag("nowplaying_art")
    val bitmap = art
    if (bitmap != null) {
        // R8 §1.3: uniform fit, not crop — the rectangle takes the image's aspect and nothing is cut.
        androidx.compose.foundation.Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = box)
    } else {
        Box(box.background(LocalShellColors.current.accent), Alignment.Center) {
            BasicText(Glyph.MUSIC, style = ShellType.header.copy(color = Color.White, fontFamily = Brand.iconFont))
        }
    }
}

private enum class MetaLine { TITLE, ARTIST }

@Composable
private fun BoxWithConstraintsScope.Metadata(cy: Dp, style: MetaLine) {
    val title = style == MetaLine.TITLE
    // R8 §1.4 (V-2016): line 1 is the track title; line 2 is "Artist • Album", the version's own change.
    val text = if (title) MusicPlayer.title else buildString {
        append(MusicPlayer.artist)
        if (MusicPlayer.album.isNotEmpty()) append(NowPlayingMetrics.META_SEPARATOR).append(MusicPlayer.album)
    }
    val size = if (title) NowPlayingMetrics.TITLE_SP else NowPlayingMetrics.ARTIST_SP
    val half = with(LocalDensity.current) { (size.toDp() / 2) }
    BasicText(
        text,
        // R8 §1.4: white, left-aligned, and the title is a SINGLE line clipped at the screen edge — it
        // does not wrap and it does not ellipsize; G3 shows a long title cut by the edge.
        style = ShellType.body.copy(color = Color.White, fontSize = size),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        softWrap = false,
        modifier = Modifier
            .offset(x = NowPlayingMetrics.META_X, y = cy - half)
            .testTag(if (title) "nowplaying_track" else "nowplaying_artist"),
    )
}

@Composable
private fun BoxWithConstraintsScope.Scrubber(cy: Dp, width: Dp) {
    val density = LocalDensity.current
    val trackLeft = NowPlayingMetrics.TRACK_LEFT
    val trackWidth = width - trackLeft - NowPlayingMetrics.TRACK_RIGHT_INSET
    val radiusDp = NowPlayingMetrics.THUMB / 2
    val duration = MusicPlayer.durationMs
    Box(
        Modifier
            .offset(x = trackLeft, y = cy - NowPlayingMetrics.THUMB / 2)
            .width(trackWidth)
            .height(NowPlayingMetrics.THUMB)
            .testTag("nowplaying_scrubber")
            // A real draggable slider (R8 §1.5), not a bare progress bar. The inverse of the travel
            // rule: a touch names a thumb CENTRE, which is a position between the two end stops.
            .pointerInput(duration) { detectSeek { x -> seekFromTouch(x, size.width.toFloat(), density, radiusDp, duration) } }
            .pointerInput(duration) { detectSeekTap { x -> seekFromTouch(x, size.width.toFloat(), density, radiusDp, duration) } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = with(density) { radiusDp.toPx() }
            val thick = with(density) { NowPlayingMetrics.TRACK_THICK.toPx() }
            val cyPx = size.height / 2f
            val frac = thumbCentreFraction(MusicPlayer.positionMs, duration, size.width, r)
            val cxPx = size.width * frac
            drawRect(NowPlayingMetrics.UNPLAYED, Offset(0f, cyPx - thick / 2f), androidx.compose.ui.geometry.Size(size.width, thick))
            drawRect(NowPlayingMetrics.PLAYED, Offset(0f, cyPx - thick / 2f), androidx.compose.ui.geometry.Size(cxPx, thick))
            // R8 §1.5: a HOLLOW ring — whatever is behind the screen shows through its centre.
            drawCircle(Color.White, radius = r - with(density) { NowPlayingMetrics.THUMB_STROKE.toPx() } / 2f, center = Offset(cxPx, cyPx), style = Stroke(with(density) { NowPlayingMetrics.THUMB_STROKE.toPx() }))
        }
    }
    // R8 §1.5: elapsed left, total right, both vertically centred on the track.
    val timeHalf = with(LocalDensity.current) { NowPlayingMetrics.TIME_SP.toDp() / 2 }
    BasicText(
        clockText(MusicPlayer.positionMs),
        style = ShellType.caption.copy(color = Color.White, fontSize = NowPlayingMetrics.TIME_SP),
        maxLines = 1,
        modifier = Modifier.offset(x = NowPlayingMetrics.ELAPSED_X, y = cy - timeHalf).testTag("nowplaying_elapsed"),
    )
    Box(Modifier.fillMaxWidth().offset(y = cy - timeHalf).padding(end = NowPlayingMetrics.TOTAL_INSET)) {
        BasicText(
            // R8 §1.5 (HIGH): the right label is the TOTAL duration, not the remaining time.
            clockText(duration),
            style = ShellType.caption.copy(color = Color.White, fontSize = NowPlayingMetrics.TIME_SP),
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterEnd).testTag("nowplaying_total"),
        )
    }
}

private fun seekFromTouch(x: Float, lengthPx: Float, density: androidx.compose.ui.unit.Density, radiusDp: Dp, durationMs: Long) {
    val r = with(density) { radiusDp.toPx() }
    val travel = (lengthPx - 2f * r).coerceAtLeast(1f)
    val fraction = ((x - r) / travel).coerceIn(0f, 1f)
    MusicPlayer.seekTo((durationMs * fraction).toLong())
}

/** A drag scrubs; a tap jumps. R8 §1.5 measured a real draggable slider, not a progress bar. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectSeek(onSeek: (Float) -> Unit) {
    detectHorizontalDragGestures(
        onDragStart = { start -> onSeek(start.x) },
        onHorizontalDrag = { change, _ -> onSeek(change.position.x) },
    )
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectSeekTap(onSeek: (Float) -> Unit) {
    detectTapGestures { onSeek(it.x) }
}

private enum class Control { PREVIOUS, PLAY_PAUSE, NEXT, REPEAT, SHUFFLE, MORE }

/**
 * The `•••` menu's entries (task 9). R8 UNMEASURED-3 never established what the `•••` holds; the sleep
 * timer and the equaliser are this build's "more", which is a P4 design call and not something R8 said.
 * Each entry's label carries the current state, so the menu IS the status display.
 */
private fun moreEntries(level: MoreMenu, go: (MoreMenu?) -> Unit): List<MenuEntry> {
    val now = android.os.SystemClock.elapsedRealtime()
    return when (level) {
        MoreMenu.ROOT -> buildList {
            add(MenuEntry(SleepTimer.menuLabel(now, MusicPlayer.sleepAt, MusicPlayer.sleepEndOfTrack), "music_menu_sleep") { go(MoreMenu.SLEEP) })
            // No equaliser on the device means no entry, rather than an entry that opens an empty list.
            if (MusicPlayer.eqAvailable) {
                add(MenuEntry(Equaliser.menuLabel(MusicPlayer.eqPreset, MusicPlayer.eqPresets), "music_menu_eq") { go(MoreMenu.EQUALISER) })
            }
            // E17: the crossfade sits with the other two things that change how the queue sounds.
            add(MenuEntry(Crossfade.menuLabel(MusicPlayer.crossfadeMs), "music_menu_crossfade") { go(MoreMenu.CROSSFADE) })
        }
        MoreMenu.CROSSFADE -> Crossfade.Choice.entries.map { choice ->
            val label = if (choice.ms == MusicPlayer.crossfadeMs) "${choice.label} (current)" else choice.label
            MenuEntry(label, "music_menu_crossfade:${choice.tag}") { MusicPlayer.setCrossfade(choice.ms); go(null) }
        }
        MoreMenu.SLEEP -> buildList {
            SleepTimer.Choice.entries.forEach { choice ->
                add(MenuEntry(choice.label, "music_menu_sleep:${choice.tag}") { MusicPlayer.setSleep(choice.minutes); go(null) })
            }
            if (MusicPlayer.sleepEndOfTrack || MusicPlayer.sleepAt > now) {
                add(MenuEntry("Turn off the sleep timer", "music_menu_sleep:off") { MusicPlayer.setSleep(SleepTimer.OFF); go(null) })
            }
        }
        MoreMenu.EQUALISER -> buildList {
            add(MenuEntry(if (MusicPlayer.eqPreset == Equaliser.OFF) "Off (current)" else "Off", "music_menu_eq:off") {
                MusicPlayer.setEqualiser(Equaliser.OFF); go(null)
            })
            MusicPlayer.eqPresets.forEachIndexed { i, name ->
                val label = if (i == MusicPlayer.eqPreset) "$name (current)" else name
                add(MenuEntry(label, "music_menu_eq:$i") { MusicPlayer.setEqualiser(i); go(null) })
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.TransportRow(cy: Dp, width: Dp, onMore: () -> Unit) {
    val cell = width / NowPlayingMetrics.TRANSPORT_CELLS
    val pill = NowPlayingMetrics.TOGGLE_PILL
    Control.entries.forEachIndexed { i, control ->
        // R8 §1.6: cell pitch is W/6 and the glyph is centred in its cell, so on a 360-epx canvas the
        // centres land on 30 / 90 / 150 / 210 / 270 / 330 exactly.
        val cx = cell * i + cell / 2
        val on = when (control) {
            Control.REPEAT -> MusicPlayer.repeatOn
            Control.SHUFFLE -> MusicPlayer.shuffleOn
            else -> false
        }
        Box(
            Modifier
                .offset(x = cx - pill / 2, y = cy - pill / 2)
                .size(pill)
                .clickable { if (control == Control.MORE) onMore() else onControl(control) }
                .testTag("nowplaying_control:${control.name.lowercase()}"),
            Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // R8 §1.6: an active toggle is a filled circle behind an unchanged white glyph.
                if (on) drawCircle(NowPlayingMetrics.TOGGLE_FILL, radius = size.minDimension / 2f)
                drawControl(control, MusicPlayer.isPlaying, this)
            }
        }
    }
}

private fun onControl(control: Control) = when (control) {
    Control.PREVIOUS -> MusicPlayer.previous()
    Control.PLAY_PAUSE -> MusicPlayer.togglePlay()
    Control.NEXT -> MusicPlayer.next()
    Control.REPEAT -> MusicPlayer.cycleRepeat()
    Control.SHUFFLE -> MusicPlayer.toggleShuffle()
    // Opened by the page (task 9): the sleep timer and the equaliser. See [moreEntries].
    Control.MORE -> Unit
}

/**
 * R8 §1.6: thin line glyphs, not filled — `|◀` is a bar plus a HOLLOW triangle. Drawn rather than
 * typed, because the icon font this shell ships carries no transport glyphs; the tile's own transport
 * strip already makes that call.
 */
private fun DrawScope.drawControl(control: Control, playing: Boolean, scope: DrawScope) {
    val epx = size.minDimension / NowPlayingMetrics.TOGGLE_PILL.value
    fun e(v: Float) = v * epx
    val cx = size.width / 2f
    val cy = size.height / 2f
    val stroke = Stroke(e(1.5f))
    when (control) {
        Control.PREVIOUS, Control.NEXT -> {
            // R8 §1.6: a bar plus a HOLLOW triangle, 14.75 x 15 epx. The bar is on the side the control
            // travels towards and the triangle points AT it — which is the way round the first device
            // capture of this screen caught wrong, with both triangles mirrored and the bar hidden
            // underneath the triangle's own back edge.
            val h = e(NowPlayingMetrics.GLYPH_H.value)
            val w = e(14.75f)
            val dir = if (control == Control.NEXT) 1f else -1f
            val bar = e(2f)
            val gap = e(1.5f)
            drawRect(
                Color.White,
                Offset(if (dir > 0) cx + w / 2f - bar else cx - w / 2f, cy - h / 2f),
                androidx.compose.ui.geometry.Size(bar, h),
            )
            val back = cx - dir * (w / 2f)
            val tip = cx + dir * (w / 2f - bar - gap)
            drawPath(
                Path().apply {
                    moveTo(back, cy - h / 2f); lineTo(back, cy + h / 2f); lineTo(tip, cy); close()
                },
                Color.White, style = stroke,
            )
        }
        Control.PLAY_PAUSE -> {
            val h = e(NowPlayingMetrics.GLYPH_H.value)
            if (playing) {
                val bar = e(2f)
                val gap = e(3f)
                drawRect(Color.White, Offset(cx - gap / 2f - bar, cy - h / 2f), androidx.compose.ui.geometry.Size(bar, h))
                drawRect(Color.White, Offset(cx + gap / 2f, cy - h / 2f), androidx.compose.ui.geometry.Size(bar, h))
            } else {
                val w = e(7.25f)
                drawPath(
                    Path().apply {
                        moveTo(cx - w / 2f, cy - h / 2f); lineTo(cx - w / 2f, cy + h / 2f); lineTo(cx + w / 2f, cy); close()
                    },
                    Color.White, style = stroke,
                )
            }
        }
        Control.REPEAT -> {
            // Two arrows round a rectangle: the 20-epx tall glyph R8 measured.
            val h = e(NowPlayingMetrics.REPEAT_H.value)
            val w = e(16f)
            drawPath(
                Path().apply {
                    moveTo(cx - w / 2f, cy - h / 4f); lineTo(cx - w / 2f, cy + h / 4f)
                    lineTo(cx + w / 2f, cy + h / 4f); lineTo(cx + w / 2f, cy - h / 4f); close()
                },
                Color.White, style = stroke,
            )
            drawPath(
                Path().apply {
                    moveTo(cx + w / 2f - e(3f), cy - h / 4f - e(3f))
                    lineTo(cx + w / 2f, cy - h / 4f)
                    lineTo(cx + w / 2f - e(3f), cy - h / 4f + e(3f))
                },
                Color.White, style = stroke,
            )
        }
        Control.SHUFFLE -> {
            // R8 §1.6 calls it `⤬`, 19.75 x 14.5 epx: two crossing paths with arrowheads on the side
            // they run to. Without the heads it reads as a close button, which is what the first
            // device capture of this screen showed.
            val h = e(NowPlayingMetrics.SHUFFLE_H.value)
            val w = e(19.75f)
            val head = e(4f)
            val thin = e(1.5f)
            drawLine(Color.White, Offset(cx - w / 2f, cy - h / 2f), Offset(cx + w / 2f, cy + h / 2f), thin)
            drawLine(Color.White, Offset(cx - w / 2f, cy + h / 2f), Offset(cx + w / 2f, cy - h / 2f), thin)
            for (sign in listOf(-1f, 1f)) {
                val tipY = cy + sign * h / 2f
                drawPath(
                    Path().apply {
                        moveTo(cx + w / 2f, tipY)
                        lineTo(cx + w / 2f - head, tipY)
                        moveTo(cx + w / 2f, tipY)
                        lineTo(cx + w / 2f, tipY - sign * head)
                    },
                    Color.White, style = Stroke(thin),
                )
            }
        }
        Control.MORE -> {
            // R8 §1.6: three dots, 2.5 epx tall, spanning 17.5 epx.
            val span = e(NowPlayingMetrics.DOTS_SPAN.value)
            val r = e(NowPlayingMetrics.DOTS_H.value) / 2f
            for (k in -1..1) drawCircle(Color.White, r, Offset(cx + k * span / 2f, cy))
        }
    }
}

/** R8 §1.7: a 12 × 6.5-epx chevron at exactly W/2; it flips to `v` when the queue is open. */
@Composable
private fun BoxWithConstraintsScope.Chevron(cy: Dp, width: Dp, expanded: Boolean, onTap: () -> Unit) {
    val hit = 44.dp
    Box(
        Modifier
            .offset(x = width / 2 - hit / 2, y = cy - hit / 2)
            .size(hit)
            .clickable(onClick = onTap),
        Alignment.Center,
    ) {
        // The TAG goes on the ink, not on the touch target. The 44-epx target reaches below the nav
        // bar's top edge, and uiautomator clips a node's reported bounds there — MUSIC7's first run
        // measured the chevron 20 epx above the nav bar instead of R8's 16 because it was measuring a
        // clipped hit box. The ink box is R8's 12 x 6.5 epx and is what the measurement is about.
        Canvas(
            Modifier
                .size(NowPlayingMetrics.CHEVRON_W, NowPlayingMetrics.CHEVRON_H)
                .testTag("nowplaying_chevron"),
        ) {
            val up = !expanded
            val y0 = if (up) size.height else 0f
            val y1 = if (up) 0f else size.height
            val w = size.width / 2f
            drawLine(Color.White, Offset(0f, y0), Offset(w, y1), size.height * 0.28f)
            drawLine(Color.White, Offset(w, y1), Offset(size.width, y0), size.height * 0.28f)
        }
    }
}

/** R8 §1.9: the play queue, two lines a row, alternating backgrounds, the playing row in the accent. */
@Composable
private fun BoxWithConstraintsScope.Queue(top: Dp, bottom: Dp) {
    val colors = LocalShellColors.current
    val entries = MusicPlayer.queue
    LazyColumn(
        Modifier
            .offset(y = top)
            .fillMaxWidth()
            .height((maxHeight - top - bottom).coerceAtLeast(0.dp))
            .testTag("nowplaying_queue"),
    ) {
        items(entries.size, key = { "${entries[it].id}:$it" }) { i ->
            val entry = entries[i]
            val current = i == MusicPlayer.queueIndex
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(NowPlayingMetrics.QUEUE_ROW)
                    .background(if (i % 2 == 1) NowPlayingMetrics.QUEUE_ALT else Color.Black)
                    .clickable { MusicPlayer.playAt(i) }
                    .padding(start = NowPlayingMetrics.META_X)
                    .testTag("nowplaying_queue_row:$i"),
                Alignment.CenterStart,
            ) {
                Column {
                    BasicText(
                        // R8 §1.9: the accent appears ONLY here, and nowhere on the collapsed screen.
                        entry.title,
                        style = ShellType.body.copy(color = if (current) colors.accent else Color.White),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        entry.artist + NowPlayingMetrics.META_SEPARATOR + entry.album,
                        style = ShellType.caption.copy(color = colors.subtleText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("nowplaying_queue_sub:$i"),
                    )
                }
            }
        }
    }
}
