package app.tileshell.files

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import app.tileshell.diag.Diagnostics
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the Files pages call to copy, move, extract and zip (phase 18, r3 D8): each starts [FileOpsService] with ONE
 * operation and returns at once; [state] says what it is doing. Called on the main thread.
 *
 * An operation is started only by a [FilesActivity] that is on screen (the platform lets no background app start a
 * `dataSync` service, and nothing but the user's own tap should start a write), and only when none is running.
 * What to copy is handed to the service in this process's memory, never in an intent: the service acts on nothing an
 * intent names, so nothing outside this object can make it write.
 *
 * A name already there: [known] holds the answers the page already has (keyed by the path that is in the way, e.g.
 * `File(destDir, source.name).path`); any other conflict is asked while the operation waits — [state] becomes
 * [OpState.AwaitingConflict] and the page calls its `answer`.
 */
object FileOpsClient {
    /** How a start ended. */
    enum class Start {
        STARTED,

        /** An operation is running (or waiting for an answer); nothing was started. */
        BUSY,

        /** The caller is not on screen; nothing was started. */
        NOT_VISIBLE,

        /** Android refused the service; nothing was started. */
        NOT_ALLOWED,
    }

    /** One operation as the service receives it. */
    internal class Request(val kind: OpKind, val sources: List<File>, val destination: File, val known: Map<String, Conflict>)

    internal val run = FileOpsRun()

    /** idle | running | awaiting a conflict's answer | finished. */
    val state: StateFlow<OpState> get() = run.state

    private val visible = MutableStateFlow(false)

    /** A FilesActivity is on screen: a conflict's dialog can be seen (else the notification says it waits). */
    internal val filesVisible: StateFlow<Boolean> get() = visible

    private var pending: Request? = null
    private var watching = false

    /** "Copy to": [sources] into [destDir]. */
    fun copy(activity: FilesActivity, sources: List<File>, destDir: File, known: Map<String, Conflict> = emptyMap()): Start =
        start(activity, Request(OpKind.COPY, sources.toList(), destDir, known.toMap()))

    /** "Move to": [sources] into [destDir]. */
    fun move(activity: FilesActivity, sources: List<File>, destDir: File, known: Map<String, Conflict> = emptyMap()): Start =
        start(activity, Request(OpKind.MOVE, sources.toList(), destDir, known.toMap()))

    /** Extract [zip] to `<zip base name>/` beside it. */
    fun extract(activity: FilesActivity, zip: File, known: Map<String, Conflict> = emptyMap()): Start =
        start(activity, Request(OpKind.EXTRACT, listOf(zip), zip.absoluteFile.parentFile ?: zip, known.toMap()))

    /** Zip [sources] into `<item>.zip` / `Archive.zip` in [destDir]. */
    fun createZip(activity: FilesActivity, sources: List<File>, destDir: File, known: Map<String, Conflict> = emptyMap()): Start =
        start(activity, Request(OpKind.CREATE_ZIP, sources.toList(), destDir, known.toMap()))

    /** Stops the running operation (what the notification's Cancel does); false when nothing runs. */
    fun cancel(): Boolean = run.stop(StopReason.CANCEL)

    /** The page has shown the end: [state] goes back to idle. */
    fun dismiss() = run.dismiss()

    private fun start(activity: FilesActivity, request: Request): Start {
        val verb = request.kind.verb
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            Diagnostics.add("files", "$verb not started: Files is not on screen")
            return Start.NOT_VISIBLE
        }
        if (!run.begin(request.kind, request.destination)) {
            Diagnostics.add("files", "$verb not started: another operation is running")
            return Start.BUSY
        }
        watch(activity.application)
        visible.value = true
        synchronized(this) { pending = request }
        return try {
            activity.startForegroundService(Intent(activity, FileOpsService::class.java).setAction(FileOpsService.ACTION_RUN))
            Start.STARTED
        } catch (e: Exception) {
            synchronized(this) { pending = null }
            Diagnostics.add("files", "$verb not started: ${e.javaClass.simpleName}")
            run.finish(OpResult.Failed(NOT_STARTED))
            run.dismiss()
            Start.NOT_ALLOWED
        }
    }

    /** The service's: the operation it was started for, once. */
    internal fun take(): Request? = synchronized(this) { pending.also { pending = null } }

    /** Follows FilesActivity on and off the screen, from the first operation on. */
    private fun watch(application: Application) {
        if (watching) return
        watching = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity is FilesActivity) visible.value = true
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity is FilesActivity) visible.value = false
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** [OpResult.Failed.reason] of an operation whose service Android would not put in the foreground. */
    const val NOT_STARTED = "not started"
}
