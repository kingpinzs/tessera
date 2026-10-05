package app.tileshell.video

import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The boundary every catalogue, media-server and hand-off call of the hub runs behind (phase 17 trust review, B-1 (b)).
 * TRUST-TOUCHING: those calls build requests from stored and server-supplied text, and an exception nobody catches in a
 * coroutine ends the process with its message — which may quote a credential — in the crash report. Here an unexpected
 * exception becomes the call's own "it did not work" value, and the line names the exception's CLASS and nothing else.
 */
object VideoCalls {
    /** `[video] <what>: failed (<ExceptionClass>)`. */
    fun line(what: String, e: Throwable): String = "$what: failed (${e.javaClass.simpleName})"

    /** [block]'s value, or [fallback] when it throws; [log] is given the line. Pure, so the rule is unit-tested. */
    inline fun <T> guarded(what: String, fallback: T, log: (String) -> Unit, block: () -> T): T = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        // The class only: the message of a header or address the platform refused quotes it.
        log(line(what, e))
        fallback
    }

    /** [block] off the main thread behind [guarded]. */
    suspend fun <T> io(what: String, fallback: T, block: () -> T): T =
        withContext(Dispatchers.IO) { guarded(what, fallback, { Diagnostics.add("video", it) }, block) }
}
