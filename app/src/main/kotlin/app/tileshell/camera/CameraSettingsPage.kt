package app.tileshell.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.settings.ToggleRow
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType

private val BOX_BORDER = Color(0xFF828282)    // r11/camera.md 1.7.6, the V-2017 translucent page
private val LIST_FILL = Color(0xFF2B2B2B)     // 1.7.8
private val DESCRIPTION = Color(0xFF999999)   // 1.7.4

/** One choice of a combo box. */
private class Choice(val label: String, val current: Boolean, val pick: () -> Unit)

/**
 * The Camera's settings page (r11/camera.md 1.7 / §2, the final release's page, HIGH): "SETTINGS" at a 16.5-epx cap,
 * 24-epx section titles, each combo box 32 epx tall with a 2-epx border and its chevron 22.25 epx from the right on an
 * 80-epx label-to-label pitch, an open list at a 44-epx pitch with the current item accent-filled, a dark translucent
 * layer over the live preview. Its rows are W10M's that mean something on Android — Lenses, the OneDrive row and
 * "Related settings" are left out (T17-16) — with "Framing grid" and "Capture living images" (r3 D3); the image sizes
 * and video sizes are the bound camera's own.
 */
@Composable
fun BoxScope.CameraSettingsPage(engine: CameraEngine, onClose: () -> Unit) {
    val s = engine.settings
    var open: String? by remember { mutableStateOf(null) }
    Box(
        Modifier.fillMaxSize().background(Color(0xE6101010)).testTag("camera_settings_page")
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 1.7.1: cap 16.5–16.75 epx (a 24-epx face), left 12, cap top 24.5.
            BasicText(
                "SETTINGS",
                // The ink starts 12 epx from the left: the face's own left bearing at this size is about 1 epx.
                Modifier.padding(start = 11.dp, top = CapMetrics.topPaddingForCapTop(24.5f, 24f).dp).testTag("camera_settings_title"),
                style = TextStyle(fontFamily = Brand.uiFont, fontSize = 24.sp, lineHeight = CapMetrics.lineHeightFor(24f).sp, color = Color.White),
            )
            Box(Modifier.height(14.dp))
            Section("Photos")
            Combo("aspect", "Aspect ratio", open, { open = it }, listOf(
                Choice("16:9", s.wide) { engine.updateSettings { it.copy(wide = true, photoSize = "") } },
                Choice("4:3", !s.wide) { engine.updateSettings { it.copy(wide = false, photoSize = "") } },
            ))
            Combo("grid", "Framing grid", open, { open = it }, listOf(
                Choice("Off", !s.grid) { engine.updateSettings { it.copy(grid = false) } },
                Choice("Rule of thirds", s.grid) { engine.updateSettings { it.copy(grid = true) } },
            ))
            val sizes = engine.photoSizesForAspect()
            val chosen = sizes.firstOrNull { "${it.width}x${it.height}" == s.photoSize } ?: sizes.firstOrNull()
            Combo("size", "Image size for main camera", open, { open = it }, sizes.map { size ->
                Choice("${CameraChoices.sizeLabel(size.width, size.height)} ${size.width}x${size.height}", size == chosen) {
                    engine.updateSettings { it.copy(photoSize = "${size.width}x${size.height}") }
                }
            })
            Toggle("timelapse", "Time lapse", s.timeLapse, "When the timer is on, keep taking photos until I press the camera button again.") { on -> engine.updateSettings { it.copy(timeLapse = on) } }
            Toggle("living", "Capture living images", s.livingImages, null) { on -> engine.updateSettings { it.copy(livingImages = on) } }
            Section("Videos")
            val qualities = engine.videoQualities
            val quality = qualities.firstOrNull { it.first == s.videoQuality } ?: qualities.firstOrNull()
            Combo("video", "Video recording", open, { open = it }, qualities.map { (name, size) ->
                Choice(CameraChoices.videoLabel(size.width, size.height), name == quality?.first) { engine.updateSettings { it.copy(videoQuality = name) } }
            })
            if (engine.stabilizationSupported) {
                Toggle("stabilization", "Digital video stabilization", s.stabilization, null) { on -> engine.updateSettings { it.copy(stabilization = on) } }
            }
            Section("About this app")
            val context = LocalContext.current
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
            BasicText("Camera $version", Modifier.padding(start = 13.25.dp, top = 4.dp).testTag("camera_about"), style = ShellType.body.copy(color = Color.White))
            BasicText("Photos and videos are saved in DCIM/Camera on this phone.", Modifier.padding(start = 13.25.dp, top = 4.dp, end = 12.dp), style = ShellType.body.copy(color = DESCRIPTION))
            Box(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    // 1.7.2: section titles at the header's size, left 12.
    BasicText(title, Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp), style = TextStyle(fontFamily = Brand.uiFont, fontSize = 24.sp, lineHeight = 30.sp, color = Color.White))
}

/** A label and its combo box on the measured 80-epx pitch; open, its list replaces the box, the current item accent-filled. */
@Composable
private fun Combo(id: String, label: String, open: String?, setOpen: (String?) -> Unit, choices: List<Choice>) {
    val colors = LocalShellColors.current
    val isOpen = open == id
    Column(Modifier.fillMaxWidth().height(if (isOpen) (40 + 44 * choices.size + 8).dp else 80.dp)) {
        Box(Modifier.height(40.dp).padding(start = 13.25.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(label, Modifier.testTag("camera_set_label:$id"), style = ShellType.body.copy(color = Color.White))
        }
        if (!isOpen) {
            Box(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(32.dp).border(2.dp, BOX_BORDER).testTag("camera_set:$id")
                    .tap { if (choices.isNotEmpty()) setOpen(id) },
            ) {
                BasicText(
                    choices.firstOrNull { it.current }?.label.orEmpty(),
                    Modifier.align(Alignment.CenterStart).padding(start = 12.25.dp).testTag("camera_set_value:$id"),
                    style = ShellType.body.copy(color = Color.White), maxLines = 1,
                )
                // 1.7.5: the chevron's centre 22.25 epx from the page's right edge (10.25 inside the box).
                Box(Modifier.align(Alignment.CenterEnd).offset(x = (-2.25).dp).size(16.dp), contentAlignment = Alignment.Center) {
                    BasicText(Glyph.CHEVRON_DOWN, Modifier.testTag("camera_set_chevron:$id"), style = TextStyle(fontFamily = Brand.iconFont, fontSize = 12.sp, color = Color.White))
                }
            }
        } else {
            Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().background(LIST_FILL).testTag("camera_set_list:$id")) {
                choices.forEachIndexed { i, choice ->
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).background(if (choice.current) colors.accent else Color.Transparent)
                            .testTag("camera_set_item:$id:$i").semantics { role = Role.Tab; selected = choice.current }
                            .tap {
                                Diagnostics.add("camera", "setting $id -> ${choice.label}")
                                choice.pick(); setOpen(null)
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicText(choice.label, Modifier.padding(start = 12.25.dp), style = ShellType.body.copy(color = Color.White), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Toggle(id: String, label: String, checked: Boolean, description: String?, onChange: (Boolean) -> Unit) {
    // R3 C1's toggle (44 × 20, its state label 56 epx right of its left edge), as the shell's Settings pages draw it.
    ToggleRow(label, checked, "camera_set:$id") { on -> Diagnostics.add("camera", "setting $id -> ${if (on) "On" else "Off"}"); onChange(on) }
    if (description != null) BasicText(description, Modifier.padding(start = 13.25.dp, end = 12.dp, bottom = 8.dp), style = ShellType.body.copy(color = DESCRIPTION))
}
