package app.tileshell.testclient.qacapture

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.AlarmClock
import android.provider.MediaStore
import android.util.Log

/**
 * Sends `ACTION_SET_ALARM` to the shell's exported `AlarmApiActivity` the ways an app can that `am start` cannot build
 * (ledger L18-3; the adversarial review's N4, and pass 3's N12). The app holds `SET_ALARM` (a normal permission) and no
 * other. Started with `am start -n app.tileshell.testclient.qacapture/.SetAlarmActivity`:
 *
 *   --es mode days        EXTRA_DAYS = an ArrayList holding a Parcelable of THIS app's own class ([QaParcel]); the
 *                         shell must refuse the request, not crash (N4). SKIP_UI true.
 *   --es mode ringtone    EXTRA_RINGTONE = a MediaStore audio row THIS app inserts and owns (so it can read it), with a
 *                         read grant on the intent; the handler weighs whether the caller could read it (N12). It logs
 *                         `setalarm ringtone uri=<uri>` so the driver knows the row. [--ez skip true|false] chooses
 *                         SKIP_UI (stored) or not (editor opens filled in) — the driver sends both and checks they agree.
 *
 * Shared: `--ei hour`, `--ei minute`, `--es message`. Logged under TileShellQa.
 */
class SetAlarmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "days"
        val hour = intent.getIntExtra("hour", 6)
        val minute = intent.getIntExtra("minute", 30)
        val message = intent.getStringExtra("message") ?: "L18 qa"
        val skip = intent.getBooleanExtra("skip", true)
        val send = Intent(AlarmClock.ACTION_SET_ALARM)
            .setClassName("app.tileshell", "app.tileshell.clock.AlarmApiActivity")
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, message)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, skip)
        when (mode) {
            "days" -> {
                val list = ArrayList<Parcelable>()
                list.add(QaParcel())
                send.putParcelableArrayListExtra(AlarmClock.EXTRA_DAYS, list)
                Log.i(CaptureProbeActivity.TAG, "setalarm mode=days (EXTRA_DAYS holds this app's own Parcelable) skip=$skip started")
            }
            "ringtone" -> {
                val uri = insertAudioRow()
                if (uri == null) {
                    Log.i(CaptureProbeActivity.TAG, "setalarm mode=ringtone insert=failed")
                } else {
                    send.putExtra(AlarmClock.EXTRA_RINGTONE, uri.toString())
                    send.clipData = ClipData.newRawUri("ringtone", uri)
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    Log.i(CaptureProbeActivity.TAG, "setalarm mode=ringtone uri=$uri skip=$skip started")
                }
            }
            else -> Log.i(CaptureProbeActivity.TAG, "setalarm mode=$mode unknown")
        }
        runCatching { startActivity(send) }.onFailure { Log.i(CaptureProbeActivity.TAG, "setalarm failed: ${it.javaClass.simpleName}") }
        finish()
    }

    /** A small MediaStore audio row on the user's own (external) volume, owned by this app so it can read it (N12's "a row the caller can read"). */
    private fun insertAudioRow(): Uri? = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "qa-ringtone-${System.currentTimeMillis()}.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/QaCapture")
            put(MediaStore.Audio.Media.IS_ALARM, 1)
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
        contentResolver.openOutputStream(uri)?.use { it.write(ByteArray(1024)) }
        uri
    }.getOrNull()
}
