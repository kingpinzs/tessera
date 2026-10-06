package app.tileshell.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.diag.Probe
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings > Diagnostics > Probe (phase 17, r3 V15): the phone's cameras with the characteristics the Camera's mode
 * gates read, the shell's processes with their memory, and the facts of one picture or video picked with Android's own
 * photo picker (no permission is needed for it). Every value is text; "Copy probe" puts all of it on the clipboard, and
 * Diagnostics' "Copy everything" carries the last read too. A P4 design (a diagnostics page; no W10M counterpart).
 */
@Composable
fun ProbePage() {
    val colors = LocalShellColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameras by remember { mutableStateOf("cameras: reading...") }
    var processes by remember { mutableStateOf("processes: reading...") }
    var file by remember { mutableStateOf(Probe.lastFile.ifEmpty { "file: none picked yet" }) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        cameras = withContext(Dispatchers.IO) { Probe.cameras(context) }
        processes = withContext(Dispatchers.IO) { Probe.processes(context) }
        Diagnostics.add("probe", "read: ${cameras.lineSequence().firstOrNull().orEmpty()}; ${processes.lineSequence().firstOrNull().orEmpty()}")
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            Diagnostics.add("probe", "file: nothing picked")
        } else {
            scope.launch {
                file = withContext(Dispatchers.IO) { Probe.file(context, uri, app.tileshell.diag.ProbeMotionPhoto::describe) }
                Diagnostics.add("probe", file.lineSequence().firstOrNull().orEmpty())
            }
        }
    }

    PageHeader(Glyph.DOCUMENT, "Probe")
    @Composable
    fun action(label: String, tag: String, onClick: () -> Unit) {
        PressRow(onClick, Modifier.fillMaxWidth().height(44.dp).testTag(tag)) {
            BasicText(label, style = ShellType.body.copy(color = colors.accent), modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
    action("Read again", "probe_refresh") { refresh++ }
    action("Pick a photo or video", "probe_pick") {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    action("Copy probe", "probe_copy") {
        val text = "TESSERA PROBE\n\n$cameras\n$processes\n$file"
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Tessera probe", text))
        Toast.makeText(context, "Probe copied", Toast.LENGTH_SHORT).show()
    }
    for ((tag, text) in listOf("probe_file" to file, "probe_processes" to processes, "probe_cameras" to cameras)) {
        BasicText(
            text.trimEnd(),
            style = ShellType.caption.copy(color = colors.text),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag(tag),
        )
    }
}
