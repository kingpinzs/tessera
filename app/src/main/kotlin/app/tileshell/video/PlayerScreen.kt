package app.tileshell.video

import android.view.SurfaceView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.clock.ClockMetrics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.delay
import app.tileshell.video.PlayerGeometry as G

/** The "•••" menu's fill and border (r11/movies-tv-pass2.md §1, 1.7.8). */
private val MENU_FILL = Color(0x2B, 0x2B, 0x2B)
private val MENU_BORDER = Color(0x76, 0x76, 0x76)

/**
 * The player page (Y5, Y6, Y7): the video on a SurfaceView from the top of the screen — no status bar and no header,
 * the frame draws the nav bar below this box — and the chrome over it. Every place is [PlayerGeometry]'s, measured from
 * the box's bottom, which is the nav bar's top.
 *
 * Tags: `video_surface` (the picture's own box), `player_scrim`, `player_track`, `player_played`, `player_thumb`,
 * `player_seek` (the touch area along the track), `player_elapsed`, `player_remaining`, `player_cc`, `player_back10`,
 * `player_playpause`, `player_fwd30`, `player_fullscreen`, `player_more`, `player_menu` with
 * `player_menu:<cast|zoom|repeat|autoplay>`, `player_error`, `player_subtitle`.
 */
@Composable
fun PlayerScreen(ui: PlayerUi, actions: PlayerActions) {
    val colors = LocalShellColors.current
    val locale = LocalConfiguration.current.locales[0]
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).testTag("player_root")) {
        val w = maxWidth.value
        val h = maxHeight.value

        if (ui.hasSurface) {
            val (vw, vh) = G.videoBox(ui.videoW, ui.videoH, w, h, ui.zoomFill)
            Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                Box(Modifier.requiredSize(vw.dp, vh.dp).testTag("video_surface")) {
                    AndroidView(factory = { SurfaceView(it).also(actions::attachSurface) }, modifier = Modifier.fillMaxSize())
                }
            }
        }

        // A tap on the picture shows the controls, or hides them.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { ui.controlsShown = !ui.controlsShown; ui.touches++ } })

        if (ui.ccOn && ui.cue.isNotEmpty()) {
            BasicText(
                ui.cue,
                Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = (G.SCRIM_HEIGHT + 8).dp)
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp).testTag("player_subtitle"),
                style = ShellType.body.copy(color = Color.White, textAlign = TextAlign.Center),
            )
        }

        ui.failure?.let { failure ->
            BasicText(
                failure.text,
                Modifier.align(Alignment.Center).padding(horizontal = 24.dp).testTag("player_error"),
                style = ShellType.subtitle.copy(color = Color.White, textAlign = TextAlign.Center),
            )
        }

        // Y6: in over ≈200 ms, held ≈3.2 s while the video plays, out over 367–400 ms; both on the shell's motion clock.
        var alpha by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(ui.controlsShown) {
            val from = alpha
            if (ui.controlsShown) {
                if (from < 1f) MotionClock.animate("controls_fade", G.FADE_IN_MS, ClockMetrics.easeOut) { alpha = from + (1f - from) * it }
            } else if (from > 0f) {
                MotionClock.animate("controls_fade", G.FADE_OUT_MS, LinearEasing) { alpha = from * (1f - it) }
            }
        }
        val full = alpha >= 1f
        LaunchedEffect(ui.controlsShown, full, ui.playing, ui.menuOpen, ui.failure, ui.touches) {
            if (ui.controlsShown && full && ui.playing && !ui.menuOpen && ui.failure == null) {
                delay(G.HOLD_MS)
                ui.controlsShown = false
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                actions.refreshPosition()
                delay(200)
            }
        }

        if (ui.controlsShown || alpha > 0f) {
            Box(Modifier.fillMaxSize().alpha(alpha)) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(G.SCRIM_HEIGHT.dp)
                        .background(Color.Black.copy(alpha = G.SCRIM_ALPHA)).testTag("player_scrim")
                        // The band belongs to the controls: a touch on it is not a tap on the picture.
                        .pointerInput(Unit) { detectTapGestures { ui.touches++ } },
                )

                val fraction = G.fraction(ui.positionMs, ui.durationMs)
                val trackTop = h - G.TRACK_CENTRE_ABOVE_NAV - G.TRACK_THICKNESS / 2f
                val thumbX = G.thumbCentreX(fraction, w)
                Box(
                    Modifier.offset(G.TRACK_INSET.dp, trackTop.dp).size(G.trackWidth(w).dp, G.TRACK_THICKNESS.dp)
                        .background(Color.White.copy(alpha = G.UNPLAYED_ALPHA)).testTag("player_track"),
                )
                Box(
                    Modifier.offset(G.TRACK_INSET.dp, trackTop.dp).size((thumbX - G.TRACK_INSET).dp, G.TRACK_THICKNESS.dp)
                        .background(colors.accent).testTag("player_played"),
                )
                Box(
                    Modifier.offset((thumbX - G.THUMB_DIAMETER / 2f).dp, (h - G.TRACK_CENTRE_ABOVE_NAV + G.THUMB_BELOW_TRACK - G.THUMB_DIAMETER / 2f).dp)
                        .size(G.THUMB_DIAMETER.dp).border(G.THUMB_STROKE.dp, colors.accent, CircleShape).testTag("player_thumb"),
                )
                // 1.6.15: the slider is dragged, and a tap on it seeks to that fraction of the track.
                Box(
                    Modifier.offset(0.dp, (h - G.TRACK_CENTRE_ABOVE_NAV - 20f).dp).fillMaxWidth().height(40.dp).testTag("player_seek")
                        .pointerInput(w) {
                            detectTapGestures { ui.touches++; actions.seekToFraction(G.fractionAt(it.x / density, w)) }
                        }
                        .pointerInput(w) {
                            detectDragGestures { change, _ -> ui.touches++; actions.seekToFraction(G.fractionAt(change.position.x / density, w)) }
                        },
                )

                val label = ShellType.caption.copy(color = Color.White, fontSize = G.LABEL_FONT.sp)
                val labelTop = h - G.LABEL_BASELINE_ABOVE_NAV - G.LABEL_FONT * CapMetrics.ASCENT_RATIO
                BasicText(
                    G.clock(G.elapsedSeconds(ui.positionMs), locale),
                    Modifier.offset(G.ELAPSED_LEFT.dp, labelTop.dp).testTag("player_elapsed"), style = label, maxLines = 1,
                )
                Box(Modifier.offset(0.dp, labelTop.dp).fillMaxWidth().padding(end = G.REMAINING_RIGHT_INSET.dp), contentAlignment = Alignment.TopEnd) {
                    BasicText(
                        G.clock(G.remainingSeconds(ui.positionMs, ui.durationMs), locale),
                        Modifier.testTag("player_remaining"), style = label, maxLines = 1,
                    )
                }

                val rowY = h - G.ROW_CENTRE_ABOVE_NAV
                if (ui.hasText) {
                    TransportButton(G.CC_CENTRE, rowY, Glyph.CLOSED_CAPTION, "player_cc", if (ui.ccOn) "Captions on" else "Captions off", if (ui.ccOn) colors.accent else Color.White) {
                        ui.touches++; actions.toggleCaptions()
                    }
                }
                TransportButton(w / 2f - G.SKIP_OFFSET, rowY, Glyph.SKIP_BACK_10, "player_back10", "Back 10 seconds") { ui.touches++; actions.skip(-G.SKIP_BACK_MS) }
                TransportButton(w / 2f, rowY, if (ui.playing) Glyph.PAUSE else Glyph.PLAY, "player_playpause", if (ui.playing) "Pause" else "Play") {
                    ui.touches++; actions.togglePlay()
                }
                TransportButton(w / 2f + G.SKIP_OFFSET, rowY, Glyph.SKIP_FORWARD_30, "player_fwd30", "Forward 30 seconds") { ui.touches++; actions.skip(G.SKIP_FORWARD_MS) }
                TransportButton(w - G.FULLSCREEN_FROM_RIGHT, rowY, if (ui.landscape) Glyph.COLLAPSE else Glyph.EXPAND, "player_fullscreen", "Full screen") {
                    ui.touches++; actions.toggleFullScreen()
                }
                TransportButton(w - G.MORE_FROM_RIGHT, rowY, Glyph.MORE_HORIZONTAL, "player_more", "More") { ui.touches++; ui.menuOpen = true }
            }
        }

        if (ui.menuOpen) {
            Box(Modifier.fillMaxSize().testTag("player_menu_scrim").pointerInput(Unit) { detectTapGestures { ui.menuOpen = false; ui.touches++ } })
            val height = 4 * G.MENU_ITEM + 2 * G.MENU_PAD
            Column(
                Modifier.offset((w - G.MENU_WIDTH).dp, (h - G.MENU_BOTTOM_ABOVE_NAV - height).dp).size(G.MENU_WIDTH.dp, height.dp)
                    .background(MENU_FILL).border(1.dp, MENU_BORDER).padding(vertical = G.MENU_PAD.dp).testTag("player_menu"),
            ) {
                MenuItem("Cast to device", "player_menu:cast", checked = null) { ui.menuOpen = false; actions.castToDevice() }
                MenuItem("Zoom to fill", "player_menu:zoom", ui.zoomFill) { actions.toggleZoom(); ui.menuOpen = false }
                MenuItem("Repeat", "player_menu:repeat", ui.repeat) { actions.toggleRepeat(); ui.menuOpen = false }
                MenuItem("Autoplay", "player_menu:autoplay", ui.autoplay) { actions.toggleAutoplay(); ui.menuOpen = false }
            }
        }
    }
}

/** One transport glyph, 18 epx tall, in a 44-epx touch box centred on ([centreX], [centreY]) — 1.6.12. */
@Composable
private fun BoxScope.TransportButton(centreX: Float, centreY: Float, glyph: String, tag: String, description: String, ink: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.offset((centreX - 22f).dp, (centreY - 22f).dp).size(44.dp).testTag(tag)
            .semantics { contentDescription = description }
            .pointerInput(tag) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        // The icon font's glyphs fill 0.8 of their em box: 22.5 epx of type draws an 18-epx glyph.
        BasicText(glyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = (G.GLYPH_HEIGHT / 0.8f).sp, lineHeight = (G.GLYPH_HEIGHT / 0.8f).sp, color = ink))
    }
}

/** One 44-epx menu row; a setting that is on carries a check at its left and `selected` in semantics. */
@Composable
private fun MenuItem(label: String, tag: String, checked: Boolean?, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(G.MENU_ITEM.dp).testTag(tag)
            .semantics { if (checked != null) selected = checked }
            .pointerInput(tag) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (checked == true) {
            BasicText(Glyph.CHECKMARK, Modifier.padding(start = 12.dp), style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 16.sp, color = Color.White))
        }
        BasicText(label, Modifier.padding(start = (G.MENU_TEXT_INSET - 1f).dp), style = ShellType.body.copy(color = Color.White), maxLines = 1)
    }
}
