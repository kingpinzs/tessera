package app.tileshell.files

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The four long operations the copy service runs (r3 D8); [verb] is the word their `[files]` lines start with. */
enum class OpKind(val verb: String) {
    COPY("copy"),
    MOVE("move"),
    EXTRACT("zip extract"),
    CREATE_ZIP("zip create"),
}

/**
 * What the copy service is doing, as the UI reads it ([FileOpsClient.state]): it draws the "Moving files…" box from
 * [Running], the replace / keep both / skip dialog from [AwaitingConflict], and lands on [Finished.destination].
 * [Running.bytes] moves about twice a second (the progress line's beat), not at every chunk.
 */
sealed interface OpState {
    data object Idle : OpState

    /** [destination] is the folder the operation writes into (an extract's is the zip's own folder). */
    data class Running(val kind: OpKind, val bytes: Long, val total: Long, val destination: File) : OpState

    /**
     * The operation found [file] already there and waits for [answer], which is called once (a second call does
     * nothing). The notification's Cancel and the time limit end the wait as well.
     */
    class AwaitingConflict(
        val kind: OpKind,
        val bytes: Long,
        val total: Long,
        val destination: File,
        val file: File,
        val answer: (Conflict) -> Unit,
    ) : OpState

    /** It ended; `result.outputs` names what it made. Stays until [FileOpsClient.dismiss] or the next operation. */
    data class Finished(val kind: OpKind, val result: OpResult, val destination: File) : OpState
}

/**
 * The copy service's state machine, with nothing of Android in it: ONE operation at a time ([begin] refuses a second),
 * its progress, the stop it was told ([stop]: the notification's Cancel, or the platform's time limit), and the wait for
 * a conflict's answer ([ask], called on the operation's own thread). `FileOpsRunTest` runs it on the JVM.
 */
class FileOpsRun {
    private val lock = Object()
    private val current = MutableStateFlow<OpState>(OpState.Idle)

    val state: StateFlow<OpState> = current

    @Volatile
    private var stop: StopReason? = null

    /** The answer to the conflict being asked, and the token of that asking (an old dialog's answer names an old token). */
    private var answer: Conflict? = null
    private var asking: Any? = null

    /** An operation is running or waiting for an answer. */
    val active: Boolean get() = current.value.let { it is OpState.Running || it is OpState.AwaitingConflict }

    /** Starts an operation, or returns false because one is running. */
    fun begin(kind: OpKind, destination: File): Boolean = synchronized(lock) {
        if (active) return false
        stop = null
        answer = null
        asking = null
        current.value = OpState.Running(kind, 0L, 0L, destination)
        true
    }

    fun progress(bytes: Long, total: Long) = synchronized(lock) {
        val run = current.value as? OpState.Running ?: return
        current.value = run.copy(bytes = bytes, total = total)
    }

    /** What the byte loop asks before every chunk. */
    fun stopped(): StopReason? = stop

    /** Tells the running operation to stop; the first reason given is the one it ends with. False when nothing runs. */
    fun stop(reason: StopReason): Boolean = synchronized(lock) {
        if (!active) return false
        if (stop == null) stop = reason
        lock.notifyAll()
        true
    }

    /**
     * [target] is already there: returns the answer, waiting for the UI's when [known] is null. Throws [Stopped] when
     * the operation is told to stop first — so a cancel during the question ends the operation `cancelled`, never as a
     * skip that reads `done`.
     */
    fun ask(target: File, known: Conflict?): Conflict = synchronized(lock) {
        stop?.let { throw Stopped(it) }
        if (known != null) return known
        val run = current.value as? OpState.Running ?: throw Stopped(StopReason.CANCEL)
        val token = Any()
        asking = token
        answer = null
        current.value = OpState.AwaitingConflict(run.kind, run.bytes, run.total, run.destination, target) { chosen ->
            synchronized(lock) {
                if (asking === token && answer == null) {
                    answer = chosen
                    lock.notifyAll()
                }
            }
        }
        try {
            while (answer == null && stop == null) lock.wait()
        } finally {
            asking = null
            current.value = run
        }
        stop?.let { throw Stopped(it) }
        answer ?: throw Stopped(StopReason.CANCEL)
    }

    /** The operation ended with [result]. */
    fun finish(result: OpResult) = synchronized(lock) {
        val (kind, destination) = when (val s = current.value) {
            is OpState.Running -> s.kind to s.destination
            is OpState.AwaitingConflict -> s.kind to s.destination
            else -> return
        }
        asking = null
        current.value = OpState.Finished(kind, result, destination)
    }

    /** The UI has shown the end (it is on the destination folder, or showed the failure). */
    fun dismiss() = synchronized(lock) {
        if (current.value is OpState.Finished) current.value = OpState.Idle
    }
}

/**
 * When a progress line is due (r3 V7): the first call always (so an operation of more than one chunk has a line with
 * 0 < bytes < total — the floor's "mid"), then every [everyMs]; half a second, so that no second of a running byte
 * loop passes without a line.
 */
class ProgressBeat(private val everyMs: Long = 500, private val nanoTime: () -> Long = System::nanoTime) {
    private var lastNs = 0L
    private var any = false

    fun due(): Boolean {
        val now = nanoTime()
        if (any && now - lastNs < everyMs * 1_000_000) return false
        any = true
        lastNs = now
        return true
    }
}

/** The words of the copy service: its notification, the progress box's title, its progress and pace lines. */
object FileOpsText {
    const val CHANNEL_NAME = "File operations"
    const val CANCEL = "Cancel"
    const val WAITING = "Waiting for an answer in Files"

    /** The notification's title and the progress box's text (`files_progress_text`). */
    fun title(kind: OpKind): String = when (kind) {
        OpKind.COPY -> "Copying files…"
        OpKind.MOVE -> "Moving files…"
        OpKind.EXTRACT -> "Extracting…"
        OpKind.CREATE_ZIP -> "Creating zip…"
    }

    /** `[files] copy|move|zip extract|zip create progress <bytes>/<total>`, without its tag. */
    fun progressLine(kind: OpKind, bytes: Long, total: Long): String = "${kind.verb} progress $bytes/$total"

    /** `[files] qa pace <bps>` (Q-18-3), without its tag. */
    fun paceLine(bytesPerSecond: Long): String = "qa pace $bytesPerSecond"

    /** Whole percent done, 0..100; null while the total is not known (the bar is then indeterminate). */
    fun percent(bytes: Long, total: Long): Int? {
        if (total <= 0) return null
        val done = bytes.coerceIn(0L, total)
        // Exact while bytes × 100 fits a Long (92 PB); past that the total's last two digits cannot matter.
        val percent = if (done <= Long.MAX_VALUE / 100) done * 100 / total else done / (total / 100)
        return percent.toInt().coerceIn(0, 100)
    }

    /** What the ongoing notification shows. */
    data class Note(val title: String, val text: String, val percent: Int?)

    /**
     * The notification for [state], null when no operation runs. The percentage lives here and not in the app (r3 D2).
     * A question nobody can see — Files is not on screen — is said here, since the dialog cannot be.
     */
    fun note(state: OpState, filesVisible: Boolean): Note? = when (state) {
        is OpState.Running -> percent(state.bytes, state.total).let { Note(title(state.kind), it?.let { p -> "$p%" }.orEmpty(), it) }
        is OpState.AwaitingConflict -> percent(state.bytes, state.total).let {
            Note(title(state.kind), if (filesVisible) it?.let { p -> "$p%" }.orEmpty() else WAITING, it)
        }
        else -> null
    }
}
