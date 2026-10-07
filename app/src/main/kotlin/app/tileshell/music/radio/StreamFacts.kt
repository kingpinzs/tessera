package app.tileshell.music.radio

import androidx.media3.common.Player
import java.util.Locale

/**
 * The small readings [StreamWatch] makes of what the player tells it (phase 20 build task 3), kept apart from it so
 * each is a rule with a test. Pure (`StreamFactsTest`).
 */
object StreamFacts {
    /** How often, while a stream is lost, [StreamWatch] looks whether it has returned. */
    const val CHECK_MS = 1_000L

    /**
     * Whether a station's start is the user's own — a tap or Tess (the queue was set), next / previous or a queue row
     * (a seek to another item) — and so counts for the directory's click counter and the favourite's last-played
     * stamp. A re-prepare of the reconnect clock moves to no item, so it is never one; nor is the player moving on
     * by itself.
     */
    fun userStarted(transitionReason: Int): Boolean =
        transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED || transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK

    /** What a move of the player says about a station whose stream its server closed ([streamEnded]). */
    enum class Ended {
        /** The player went on to the next item by itself: the station is gone back to, and that move is [BACK]. */
        MOVED_ON,

        /** The player is back on the station it had left, or began it again by itself (repeat): the stream is lost. */
        LOST,
    }

    /**
     * A station's stream never ends by itself: when its server closes the connection cleanly the player reads "the end
     * of the item" — no load error, no player error — and would go silent, or play the next favourite as if asked.
     * So a move the PLAYER made out of a station ([Player.MEDIA_ITEM_TRANSITION_REASON_AUTO]) is undone
     * ([Ended.MOVED_ON]), and the move back — or a repeat of the station — puts it on the reconnect clock
     * ([Ended.LOST]) exactly as a player error does. Null: an ordinary move (a tap, next / previous, Tess, a track).
     *
     * @param fromId the item the player was on, [toId] the item it is on now
     * @param returningTo the station [Ended.MOVED_ON] is going back to, while that move is under way
     */
    fun streamEnded(transitionReason: Int, fromId: String?, toId: String?, returningTo: String?, isLive: (String?) -> Boolean): Ended? = when {
        returningTo != null && toId == returningTo && transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> Ended.LOST
        transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && isLive(toId) -> Ended.LOST
        transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && isLive(fromId) -> Ended.MOVED_ON
        else -> null
    }

    /**
     * Whether a load or player error is Media3 refusing a redirect between https and http (its kept default, r3
     * D12): [messages] are the error's own and its causes'. Such a station is not retried.
     */
    fun redirectRefused(messages: List<String?>): Boolean = messages.any { it != null && it.startsWith(CROSS_PROTOCOL) }

    /** `DefaultHttpDataSource`'s message, read from Media3 1.9.0. */
    const val CROSS_PROTOCOL = "Disallowed cross-protocol redirect"

    /**
     * Whether a lost stream has returned: it is sounding, and the end of what is buffered has moved since the last
     * look — bytes are arriving again. A player still sounding out of what it had buffered has not returned.
     */
    fun returned(playing: Boolean, bufferedAtLastLookMs: Long, bufferedMs: Long): Boolean = playing && bufferedMs != bufferedAtLastLookMs

    /** The codec's name for the `stream: connected` line, from the audio track's sample MIME type. */
    fun codec(sampleMimeType: String?): String {
        val type = sampleMimeType.orEmpty().lowercase(Locale.ROOT)
        return when (type) {
            "audio/mpeg", "audio/mpeg-l1", "audio/mpeg-l2" -> "mp3"
            "audio/mp4a-latm" -> "aac"
            else -> type.substringAfter('/', "")
        }
    }
}
