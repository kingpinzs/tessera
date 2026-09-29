package app.tileshell.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.tileshell.bars.BarMetrics
import app.tileshell.brand.Brand
import app.tileshell.diag.Diagnostics
import app.tileshell.settings.ThemeItems
import app.tileshell.settings.ThemePresetsGrid
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionTrace
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.motion.Motion
import app.tileshell.ui.tokens.ShellType

/*
 * Phase 12's wizard surface (build task 2): drawn inside StartActivity above everything Start draws, with the pager not
 * composed while it shows (T12-6). One page per step between the drawn bars, then the presets page. Every look value is a
 * P4 design judged in H1 / H3 (W10M's out-of-box experience was never measured).
 */

/** The wizard, filling the page between the drawn bars. [onEnd] runs after "Skip setup" or "Done" wrote the marker. */
@Composable
fun WizardHost(onEnd: () -> Unit) {
    val context = LocalContext.current
    val run = SetupWizard.run ?: return
    val colors = LocalShellColors.current
    // The Setup rows' role sheet and permission dialog return to Start, the activity that raised them; the step advances on
    // the resume that follows (SetupWizard.reconcile), so the results themselves need no handling here.
    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val version = SetupWizard.rowsVersion
    val rows = remember(version, run) { SetupWizard.rows(context, { requestRole.launch(it) }, { requestPermissions.launch(it) }) }
    val pageKey = run.current ?: "presets"

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("wizard_page"),
    ) {
        WizardPageTransition(pageKey) {
            val row = run.current?.let { key -> rows.firstOrNull { it.key == key } }
            if (row == null) {
                PresetsPage(run, onDone = { SetupWizard.done(context); onEnd() })
            } else {
                StepPage(row, run, onSkip = { SetupWizard.skip(context); onEnd() })
            }
        }
    }
}

@Composable
private fun StepPage(row: WizardRow, run: RunState, onSkip: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val label = WizardRules.label(row, run)
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = BarMetrics.STATUS_EPX.dp)
            .padding(horizontal = 12.dp)
            .testTag("wizard_step:${row.key}"),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Progress(run)
            Spacer(Modifier.weight(1f))
            // "Skip setup" is a decision made at the start (Decisions "Step page"): the first step's page only.
            if (run.pos == 0) {
                PressRow(onSkip, Modifier.taggedText("wizard_skip", "Skip setup")) {
                    BasicText("Skip setup", style = ShellType.body.copy(color = colors.accent), modifier = Modifier.padding(8.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        BasicText(row.title, style = ShellType.title.copy(color = colors.text), modifier = Modifier.testTag("wizard_title"))
        Spacer(Modifier.height(8.dp))
        BasicText(WizardRules.WHY[row.key].orEmpty(), style = ShellType.body.copy(color = colors.text), modifier = Modifier.testTag("wizard_why"))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (glyph, glyphColor) = Checklist.glyphFor(row.state)
            BasicText(glyph, style = TextStyle(fontFamily = Brand.iconFont, fontSize = ShellType.body.fontSize, color = glyphColor))
            Spacer(Modifier.width(8.dp))
            BasicText(row.detail, style = ShellType.body.copy(color = colors.subtleText), modifier = Modifier.testTag("wizard_body"))
        }
        Spacer(Modifier.height(32.dp))
        AccentButton(label, "wizard_action") { SetupWizard.fire(context, row) }
        Spacer(Modifier.height(8.dp))
        PressRow({ SetupWizard.notNow(context) }, Modifier.taggedText("wizard_not_now", "Not now")) {
            BasicText("Not now", style = ShellType.body.copy(color = colors.text), modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp))
        }
    }
}

@Composable
private fun PresetsPage(run: RunState, onDone: () -> Unit) {
    val colors = LocalShellColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = BarMetrics.STATUS_EPX.dp)
            .testTag("wizard_presets"),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(Modifier.padding(start = 12.dp, top = 16.dp)) { Progress(run) }
            Spacer(Modifier.height(20.dp))
            BasicText("Choose a theme", style = ShellType.title.copy(color = colors.text), modifier = Modifier.padding(horizontal = 12.dp).testTag("wizard_title"))
            Spacer(Modifier.height(4.dp))
            BasicText(
                "Tap one to see it now. You can change any part of it below, or later in Start settings.",
                style = ShellType.body.copy(color = colors.subtleText),
                modifier = Modifier.padding(horizontal = 12.dp).testTag("wizard_why"),
            )
            Spacer(Modifier.height(16.dp))
            ThemePresetsGrid("preset") { name -> Diagnostics.add("wizard", "preset $name") }
            ThemeItems()
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
            AccentButton("Done", "wizard_done", onDone)
        }
    }
}

@Composable
private fun Progress(run: RunState) {
    val colors = LocalShellColors.current
    BasicText(
        "Step ${run.stepNumber} of ${run.total}",
        style = ShellType.caption.copy(color = colors.accent),
        modifier = Modifier.testTag("wizard_progress"),
    )
}

/** The page's one accent button (P4, H1): a filled accent rectangle, its label on the node that carries the tag. */
@Composable
private fun AccentButton(label: String, tag: String, onClick: () -> Unit) {
    val colors = LocalShellColors.current
    PressRow(
        onClick,
        Modifier.widthIn(min = 120.dp).height(40.dp).background(colors.accent).taggedText(tag, label),
    ) {
        BasicText(label, style = ShellType.body.copy(color = Color.White), modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
    }
}

/**
 * A control's test tag with its visible text ON the tagged node (the MUSIC8 lesson, testability 24): uiautomator reads the
 * text by resource-id, and a merged child's text is not reported on its parent, so the child's semantics are replaced.
 */
fun Modifier.taggedText(tag: String, text: String, selected: Boolean? = null): Modifier = clearAndSetSemantics {
    testTag = tag
    this.text = AnnotatedString(text)
    if (selected != null) this.selected = selected
}

/**
 * Page-to-page motion: phase 01's X7 Settings transition (the Start entrance form, scale 0.78 -> 1 with the fade over
 * [Motion.PAGE_ENTER_MS]), on the shell's own clock (C-5): each run logs `[motion] wizard_page t0=… settle=… frames=…
 * maxGapMs=…`, t0 being the motion's first frame, so settle reads 217 ms to within a frame (E10). StartActivity wraps Start's
 * first composition after the wizard in the same motion.
 */
@Composable
fun WizardPageTransition(key: Any, content: @Composable () -> Unit) {
    var elapsed by remember(key) { mutableFloatStateOf(0f) }
    LaunchedEffect(key) {
        val start = withFrameNanos { it }
        val trace = MotionTrace("wizard_page", start / 1_000_000L)
        trace.frame(start, 0f)
        while (true) {
            val now = withFrameNanos { it }
            val ms = (now - start) / 1_000_000f
            // The motion ends on the frame NEAREST its 217 ms, not the first frame after it: 217 is not a whole number of
            // frames (13 × 16.7 = 216.7 at 60 Hz), and ending one frame late put the fade's end at 233 ms (E10 run 1),
            // outside "alpha complete in 217 ms". At 120 Hz that frame is also 216.7 ms.
            val done = ms >= Motion.PAGE_ENTER_MS - Motion.FRAME_MS / 2f
            elapsed = if (done) Motion.PAGE_ENTER_MS.toFloat() else ms
            val v = if (done) 1f else ms / Motion.PAGE_ENTER_MS
            trace.frame(now, v)
            if (done) break
        }
        Diagnostics.add("motion", trace.message())
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        val s = Motion.sampleFrames(Motion.entranceScaleFrames, elapsed)
        scaleX = s; scaleY = s
        alpha = Motion.entranceAlpha(elapsed)
    }) { content() }
}
