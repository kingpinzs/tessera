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
fun DiagnosticsPage() {
    val colors = LocalShellColors.current
    val context = LocalContext.current
    var entries by remember { mutableStateOf(Diagnostics.snapshot()) }
    var speech by remember { mutableStateOf("speech: reading...") }
    LaunchedEffect(Unit) {
        while (true) {
            entries = Diagnostics.snapshot()
            speech = withContext(Dispatchers.IO) { runCatching { SpeechClient.status() }.getOrElse { "speech: $it" } }
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
