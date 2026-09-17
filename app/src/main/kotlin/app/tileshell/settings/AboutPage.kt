package app.tileshell.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType

/** Settings > About: version and the bundled licence notices (Selawik OFL 1.1 and Fluent UI System Icons MIT require them). */
@Composable
fun AboutPage() {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    val notices = remember {
        context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n") { name ->
            "== $name ==\n" + context.assets.open("licenses/$name").bufferedReader().readText()
        }
    }
    PageHeader(Glyph.INFO, "About")
    BasicText("Version $version", style = ShellType.body.copy(color = colors.text), modifier = Modifier.padding(12.dp).testTag("about_version"))
    BasicText(notices, style = ShellType.caption.copy(color = colors.subtleText), modifier = Modifier.padding(horizontal = 12.dp).testTag("about_licences"))
}
