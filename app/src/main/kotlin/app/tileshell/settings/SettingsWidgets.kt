package app.tileshell.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType

/** R3 C1: page header block 57 epx under the status bar; glyph 21-23 epx at x 12 epx; title 15-epx SemiBold. */
@Composable
fun PageHeader(glyph: String, title: String) {
    val colors = LocalShellColors.current
    Row(Modifier.fillMaxWidth().height(57.dp).padding(start = 12.dp).testTag("page_header"), verticalAlignment = Alignment.CenterVertically) {
        BasicText(glyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 22.sp, color = colors.text))
        Spacer(Modifier.width(12.dp))
        BasicText(title.uppercase(), style = ShellType.base.copy(color = colors.text))
    }
}

/** R3 C1: two-line item, 64-epx pitch, 30-epx glyph at x 12 epx, text at x 55 epx, title 15 / subtitle 12. */
@Composable
fun TwoLineItem(glyph: String, title: String, subtitle: String, tag: String, glyphColor: Color? = null, onClick: () -> Unit) {
    val colors = LocalShellColors.current
    PressRow(onClick, Modifier.fillMaxWidth().height(64.dp).testTag(tag)) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(43.dp)) {
                BasicText(glyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 30.sp, color = glyphColor ?: colors.text))
            }
            Column {
                BasicText(title, style = ShellType.body.copy(color = colors.text))
                BasicText(subtitle, style = ShellType.caption.copy(color = colors.subtleText), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    val colors = LocalShellColors.current
    BasicText(text, style = ShellType.subtitle.copy(color = colors.text), modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 8.dp))
}

/** R3 C1 toggle: 44 x 20 epx; On = accent fill with white thumb; Off = 1-epx outline; state label 56 epx right. */
@Composable
fun ToggleRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val colors = LocalShellColors.current
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
        BasicText(label, style = ShellType.body.copy(color = colors.text))
        Row(Modifier.padding(top = 6.dp).pointerInput(checked) { detectTapGestures { onChange(!checked) } }.testTag(tag), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp, 20.dp)
                    .background(if (checked) colors.accent else Color.Transparent, CircleShape)
                    .border(if (checked) 0.dp else 1.dp, colors.text, CircleShape),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(horizontal = 5.dp).size(8.dp).background(if (checked) Color.White else colors.text, CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            BasicText(if (checked) "On" else "Off", style = ShellType.body.copy(color = colors.text))
        }
    }
}

/** X23 approximation: R3 A20 slider geometry (track 2 epx, accent thumb 6.4 x 20.4 epx). Value 0..1. */
@Composable
fun SliderRow(label: String, value: Float, tag: String, onChange: (Float) -> Unit) {
    val colors = LocalShellColors.current
    var widthPx by remember { mutableFloatStateOf(1f) }
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
        BasicText(label, style = ShellType.body.copy(color = colors.text))
        Box(
            Modifier.fillMaxWidth().height(32.dp).onSizeChanged { widthPx = it.width.toFloat() }.testTag(tag)
                .pointerInput(Unit) {
                    detectTapGestures { onChange((it.x / widthPx).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> onChange((change.position.x / widthPx).coerceIn(0f, 1f)) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.subtleText))
            Box(Modifier.fillMaxWidth(value).height(2.dp).background(colors.accent))
            val thumbOffset = with(androidx.compose.ui.platform.LocalDensity.current) { (widthPx * value).toDp() } - 3.2.dp
            Box(Modifier.offset(x = thumbOffset).size(6.4.dp, 20.4.dp).background(colors.accent))
        }
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, tag: String, onSelect: () -> Unit) {
    val colors = LocalShellColors.current
    PressRow(onSelect, Modifier.fillMaxWidth().height(44.dp).testTag(tag)) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).border(2.dp, colors.text, CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(10.dp).background(colors.text, CircleShape))
            }
            Spacer(Modifier.width(10.dp))
            BasicText(label, style = ShellType.body.copy(color = colors.text, fontWeight = FontWeight.Normal))
        }
    }
}
