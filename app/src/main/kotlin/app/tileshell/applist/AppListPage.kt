package app.tileshell.applist

import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.apps.AppCatalog
import app.tileshell.apps.AppEntry
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType

/** App list (build task 11 extends this with search, letter headers, the "New" caption, jump grid and profiles). */
@Composable
fun AppListPage(onLaunch: (AppEntry, Rect?) -> Unit) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val catalog = remember { AppCatalog.get(context) }
    val apps by catalog.apps.collectAsState()
    Column(Modifier.fillMaxSize().padding(top = 28.dp).testTag("app_list")) {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(apps, key = { it.key }) { entry ->
                PressRow(onClick = { onLaunch(entry, null) }, modifier = Modifier.fillMaxWidth().height(44.dp).testTag("app:${entry.component.packageName}")) {
                    Row(Modifier.fillMaxSize().padding(start = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        catalog.icon(entry, 120)?.let { Image(it, null, Modifier.size(41.dp)) } ?: Box(Modifier.size(41.dp))
                        Spacer(Modifier.width(9.dp))
                        BasicText(entry.label, style = ShellType.body.copy(color = colors.text))
                    }
                }
            }
        }
    }
}
