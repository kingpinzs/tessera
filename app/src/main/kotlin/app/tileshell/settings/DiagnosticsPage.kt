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
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import app.tileshell.cortana.speech.SpeechClient
import app.tileshell.diag.Diagnostics
import app.tileshell.diag.Probe
import app.tileshell.diag.RemoteRings
import app.tileshell.diag.RingDumpService
import app.tileshell.ui.components.PressRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings > Diagnostics: the same ring buffer the listener's dump() prints, newest first (phase 01
 * Decisions), plus the speech process's own status and a way to get all of it off the phone.
 *
 * Two things were missing and both cost a night. The speech engine runs in :speech, which has its OWN
 * ring buffer, so the page showed everything EXCEPT the part that was failing. And there was no way to
 * copy any of it, so a failure on a phone could only be described in words — "My speech files are
 * missing" names three different faults (absent asset, rejected model, bad espeak data) and reading it
 * cannot tell you which. Diagnostics you cannot get out of the device are diagnostics you do not have.
 */
@Composable
fun DiagnosticsPage(onProbe: () -> Unit = {}) {
    val colors = LocalShellColors.current
    val context = LocalContext.current
    var entries by remember { mutableStateOf(Diagnostics.snapshot()) }
    var speech by remember { mutableStateOf("speech: reading...") }
    // Phase 17 (r3 D16, V15): the :camera, :video and :photosedit processes each have their own ring. Each is read
    // through its dump service while its process runs; the read never starts a process.
    var remote by remember { mutableStateOf(REMOTE_RINGS.associate { it.first to "reading..." }) }
    LaunchedEffect(Unit) {
        while (true) {
            entries = Diagnostics.snapshot()
            speech = withContext(Dispatchers.IO) { runCatching { SpeechClient.status() }.getOrElse { "speech: $it" } }
            remote = withContext(Dispatchers.IO) {
                REMOTE_RINGS.associate { (name, service) -> name to (RemoteRings.read(context, service) ?: "not running") }
            }
            delay(1000)
        }
    }
    PageHeader(Glyph.DOCUMENT, "Diagnostics")
    val fmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    val report: () -> String = {
        buildString {
            append("TESSERA DIAGNOSTICS\n")
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append("  Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append('\n')
            append(Build.FINGERPRINT).append('\n')
            append("abis ").append(Build.SUPPORTED_ABIS.joinToString()).append('\n')
            append('\n').append("== SPEECH PROCESS").append('\n').append(speech).append('\n')
            for ((name, _) in REMOTE_RINGS) {
                append('\n').append("== ").append(name.uppercase()).append(" PROCESS").append('\n').append(remote[name].orEmpty().trimEnd()).append('\n')
            }
            val probe = listOf(Probe.lastFile, Probe.lastProcesses, Probe.lastCameras).filter { it.isNotEmpty() }
            if (probe.isNotEmpty()) append('\n').append("== PROBE (the last read)").append('\n').append(probe.joinToString("\n") { it.trimEnd() }).append('\n')
            append('\n').append("== SHELL, newest first").append('\n')
            entries.asReversed().forEach { e ->
                append(fmt.format(Date(e.wallMs))).append(" [").append(e.tag).append("] ").append(e.message).append('\n')
            }
        }
    }

    PressRow(
        {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Tessera diagnostics", report()))
            Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
        },
        Modifier.fillMaxWidth().height(44.dp).testTag("diag_copy"),
    ) {
        BasicText(
            "Copy everything",
            style = ShellType.body.copy(color = colors.accent),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }

    PressRow(onProbe, Modifier.fillMaxWidth().height(44.dp).testTag("diag_probe")) {
        BasicText(
            "Probe: cameras, memory, a file's facts",
            style = ShellType.body.copy(color = colors.accent),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }

    // The other processes' rings (phase 17), newest first, each under its header; then the speech process.
    for ((name, _) in REMOTE_RINGS) {
        BasicText(
            "== $name process",
            style = ShellType.caption.copy(color = colors.accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).testTag("diag_ring_header:$name"),
        )
        BasicText(
            remoteNewestFirst(remote[name].orEmpty()),
            style = ShellType.caption.copy(color = colors.text),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).testTag("diag_ring:$name"),
        )
    }

    // The speech process first: it is the one that runs out of reach of everything else on this page.
    BasicText(
        "== speech process",
        style = ShellType.caption.copy(color = colors.accent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).testTag("diag_speech_header"),
    )
    BasicText(
        speech,
        style = ShellType.caption.copy(color = colors.text),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).testTag("diag_speech"),
    )

    entries.asReversed().take(300).forEachIndexed { i, e ->
        BasicText(
            "${fmt.format(Date(e.wallMs))} [${e.tag}] ${e.message}",
            style = ShellType.caption.copy(color = colors.text),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).testTag("diag:$i"),
        )
    }
}

/** The processes whose rings this page reads through their dump services, by the name the page shows. */
private val REMOTE_RINGS: List<Pair<String, Class<out RingDumpService>>> = listOf(
    "camera" to app.tileshell.camera.CameraDumpService::class.java,
    "video" to app.tileshell.video.VideoDumpService::class.java,
    "photosedit" to app.tileshell.photos.PhotosEditDumpService::class.java,
)

/** A dump service's text for the page: its header and status lines, then its ring lines newest first, at most 120. */
internal fun remoteNewestFirst(dump: String): String {
    val lines = dump.trimEnd().lines()
    val cut = lines.indexOfFirst { it.startsWith("tileshell diagnostics:") }
    if (cut < 0) return dump.trimEnd()
    return (lines.take(cut + 1) + lines.drop(cut + 1).asReversed().take(120)).joinToString("\n")
}
