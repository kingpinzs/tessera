package app.tileshell.recorder

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The list the Voice Recorder page shows: every `IS_RECORDING` row (Q2 A), kept current by a ContentObserver so
 * a recording deleted outside the app — by `adb shell rm`, a file manager, or the other app that made it — drops
 * from the list with no restart (edge cases), and a saved or recovered take appears the same way.
 *
 * Every refresh says what it found, in the launcher ring (T15-3 / T15-19): `list: n recordings (m by other
 * apps)`, or, while READ_MEDIA_AUDIO is off and MediaStore shows the shell only its own files,
 * `list: n recordings (other apps: hidden, READ_MEDIA_AUDIO denied)` — an empty list and a hidden one never look
 * alike in the evidence.
 */
object RecorderLibrary {

    data class Snapshot(val recordings: List<Recording>, val othersVisible: Boolean)

    private val state = MutableStateFlow(Snapshot(emptyList(), othersVisible = true))
    val snapshot: StateFlow<Snapshot> = state.asStateFlow()

    private var observer: ContentObserver? = null
    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** Follow MediaStore while the page shows. */
    fun start(context: Context) {
        val app = context.applicationContext
        if (observer != null) return
        val watcher = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = refreshSoon(app, "media change")
        }
        runCatching { app.contentResolver.registerContentObserver(RecordingStore.COLLECTION, true, watcher) }
            .onSuccess { observer = watcher }
            .onFailure { Diagnostics.add("recorder", "could not observe the audio collection: $it") }
    }

    fun stop(context: Context) {
        observer?.let { runCatching { context.applicationContext.contentResolver.unregisterContentObserver(it) } }
        observer = null
        pending?.let { main.removeCallbacks(it) }
        pending = null
    }

    /** A burst of changes (a publish touches a row several times) becomes one refresh. */
    private fun refreshSoon(context: Context, why: String) {
        pending?.let { main.removeCallbacks(it) }
        val r = Runnable {
            pending = null
            Thread({ refresh(context, why) }, "recorder-list").start()
        }
        pending = r
        main.postDelayed(r, COALESCE_MS)
    }

    /** Read the list now (any thread). */
    fun refresh(context: Context, why: String) {
        val app = context.applicationContext
        val visible = RecordingStore.canSeeOthers(app)
        val list = RecordingStore.query(app)
        state.value = Snapshot(list, visible)
        val shell = app.packageName
        if (visible) {
            Diagnostics.add("recorder", "list: ${list.size} recordings (${RecordingFilter.othersCount(list, shell)} by other apps)")
        } else {
            // MediaStore returns only the shell's own files without the grant; any other row here is an orphan
            // it still attributes to nobody (T15-26), which is hidden with the rest.
            Diagnostics.add("recorder", "list: ${list.size} recordings (other apps: hidden, READ_MEDIA_AUDIO denied)")
        }
        Diagnostics.add("recorder", "list refreshed ($why)")
    }

    private const val COALESCE_MS = 250L
}
