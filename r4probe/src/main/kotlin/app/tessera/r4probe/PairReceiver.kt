package app.tessera.r4probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Pairs with the phone's own Wireless debugging in the background, so Settings' pairing dialog stays in front (it stops
 * listening when it is covered). The run's notification sends the code here from its reply field; [PairTestReceiver] is
 * the same thing for the emulator test, which the shell (it holds DUMP) can reach and no app can.
 * Extras: host, port, code, and connect_port (then it also connects and runs `id`).
 */
open class PairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val host = intent.getStringExtra("host") ?: return
        val port = intent.getIntExtra("port", 0)
        val code = intent.getStringExtra("code")
            ?: android.app.RemoteInput.getResultsFromIntent(intent)?.getCharSequence("code")?.toString()?.trim()
            ?: return
        val connectPort = intent.getIntExtra("connect_port", 0)
        val pending = goAsync()
        Thread {
            val sb = StringBuilder(AdbSelf.pair(context, host, port, code))
            if (connectPort > 0 && !sb.startsWith("PAIR FAILED")) {
                sb.append("\n").append(AdbSelf.connect(context, host, connectPort))
                sb.append("\n").append(AdbSelf.shell("id").second.trim())
            }
            runCatching { File(context.filesDir, "pair.txt").writeText(sb.toString() + "\n") }
            HelperLink.note(sb.toString().lines().first())
            pending.finish()
        }.start()
    }
}

/** The emulator test's door to [PairReceiver]: exported, but only to callers holding DUMP (the shell, the system). */
class PairTestReceiver : PairReceiver()
