package app.tileshell.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings > Diagnostics: the same ring buffer the listener's dump() prints, newest first (phase 01 Decisions). */
@Composable
fun DiagnosticsPage() {
    val colors = LocalShellColors.current
    var entries by remember { mutableStateOf(Diagnostics.snapshot()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); entries = Diagnostics.snapshot() } }
    PageHeader(Glyph.DOCUMENT, "Diagnostics")
    val fmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    entries.asReversed().take(300).forEachIndexed { i, e ->
        BasicText(
            "${fmt.format(Date(e.wallMs))} [${e.tag}] ${e.message}",
            style = ShellType.caption.copy(color = colors.text),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).testTag("diag:$i"),
        )
    }
}
