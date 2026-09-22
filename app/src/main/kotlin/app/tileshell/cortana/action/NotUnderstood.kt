package app.tileshell.cortana.action

import android.content.Context
import app.tileshell.cortana.Card
import app.tileshell.cortana.CardKind
import app.tileshell.diag.Diagnostics

/**
 * Where speech the command list does not match goes (phase 03 build task 7).
 *
 * This is a permanent part, not a stand-in (Rule 16): phase 08's on-device model registers as the
 * [handler] and answers, and until it does the same path gives W10M's "I can't do that yet" reply.
 * Either way the open-vocabulary transcript is recorded here, which is what E3 reads.
 */
object NotUnderstood {

    /** Phase 08 implements this. It returns null to let the default reply stand. */
    fun interface Handler {
        fun answer(context: Context, text: String): Outcome?
    }

    @Volatile
    var handler: Handler? = null

    fun handle(context: Context, text: String): Outcome {
        // E3 reads exactly this line: the transcribed text, verbatim, in the diagnostics ring buffer.
        Diagnostics.add("not_understood", "transcript=\"$text\" handler=${handler?.javaClass?.simpleName ?: "none"}")
        handler?.answer(context, text)?.let { return it }
        val spoken = "Sorry, I can't do that yet."
        return Outcome(spoken, Card(CardKind.NOT_UNDERSTOOD, spoken, body = listOf(text)))
    }
}
