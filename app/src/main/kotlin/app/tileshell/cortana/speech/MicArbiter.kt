package app.tileshell.cortana.speech

/**
 * One engine, one microphone (phase 05 edge case: "the second caller waits or is refused with a notice,
 * never a crash"). The speech process keeps one of these; the rules live here, free of Binder, so the host
 * JVM tests every one of them — the device can only rarely put two clients on the microphone at once.
 *
 * [K] is the client's identity: the callback's IBinder in the service, a plain value in the tests.
 *
 * - A client acquires the microphone only when nobody holds it, or when it already does (starting again
 *   replaces its own capture). Anyone else is refused: never queued, never allowed to take it over.
 * - Only the holder can release it (a stop from anyone else does nothing).
 * - A capture that ends frees the microphone only if it is still the holder's CURRENT capture: a holder
 *   that started again already has a newer generation, and the old capture ending must not free that.
 *
 * **Named owners (phase 15, T15-4 / T15-28).** Voice Recorder is a third owner beside Tess and the keyboard,
 * and it holds the microphone for a whole take from its own `:recorder` process rather than for one
 * utterance. A refusal therefore has to say WHO holds the microphone — "the keyboard" and "the voice
 * recorder" are different sentences to the user — so the holder's NAME ([MicHolders]) is kept beside its
 * key, and [holderName] is what every refusal reports.
 */
class MicArbiter<K : Any> {
    private var owner: K? = null
    private var ownerName: String = ""
    private var generation = -1

    /** The holder's process id, for the status dump and the diagnostics; -1 when free. */
    var ownerPid = -1
        private set

    /**
     * Try to take the microphone for [who] (process [pid]). [nextGeneration] is called only on success and
     * returns the capture generation the new capture will carry.
     *
     * @return that generation, or null when another client holds the microphone
     */
    fun acquire(who: K, pid: Int, nextGeneration: () -> Int): Int? = acquire(who, pid, "", nextGeneration)

    /**
     * [acquire] with the owner's [name] ([MicHolders.CORTANA], [MicHolders.KEYBOARD], [MicHolders.RECORDER]),
     * which a refusal of anyone else then reports through [holderName].
     */
    @Synchronized
    fun acquire(who: K, pid: Int, name: String, nextGeneration: () -> Int): Int? {
        val holder = owner
        if (holder != null && holder != who) return null
        val g = nextGeneration()
        owner = who
        ownerName = name
        generation = g
        ownerPid = pid
        return g
    }

    @Synchronized
    fun isHolder(who: K): Boolean = owner == who

    /** [who] lets go (it stopped, unregistered or died). @return true when it was the holder. */
    @Synchronized
    fun release(who: K): Boolean {
        if (owner != who) return false
        clear()
        return true
    }

    /** The capture carrying generation [g] has ended; frees the microphone only if that is still current. */
    @Synchronized
    fun finished(g: Int) {
        if (generation == g) clear()
    }

    @Synchronized
    fun holder(): K? = owner

    /** The holder's name as it acquired ("" for an unnamed holder), or null while the microphone is free. */
    @Synchronized
    fun holderName(): String? = if (owner == null) null else ownerName

    private fun clear() {
        owner = null
        ownerName = ""
        generation = -1
        ownerPid = -1
    }
}

/**
 * The three owners of the shell's one microphone, named across the binder (T15-28), and the words each
 * refusal is shown in. Pure, so the wording rules are JVM-tested beside the arbiter's.
 *
 * `MICROPHONE_BUSY`'s detail carries the holder as [busyDetail] writes it, so the refused side reads the
 * name back with [holderOf] and never has to guess from which client it is itself.
 */
object MicHolders {
    /** Tess, in the launcher process: the session and the recognition service other apps reach. */
    const val CORTANA = "cortana"

    /** The keyboard's voice key, in `:ime`. */
    const val KEYBOARD = "keyboard"

    /** Voice Recorder's take, in `:recorder` (phase 15). */
    const val RECORDER = "recorder"

    private const val DETAIL_PREFIX = "held by "

    /**
     * The owner name a [SpeechClient] in process [processName] listens as. The keyboard is the only client
     * in `:ime`; everything in the launcher process listens for Tess (her session and the recognition
     * service Android's default recogniser reaches, phase 05 EDGE3).
     */
    fun forProcess(processName: String?): String =
        if (processName != null && processName.endsWith(":ime")) KEYBOARD else CORTANA

    /** `MICROPHONE_BUSY`'s detail: the holder's name in a form [holderOf] reads back. */
    fun busyDetail(holder: String?): String = DETAIL_PREFIX + (holder?.takeIf { it.isNotEmpty() } ?: "unknown")

    /** The holder named in a `MICROPHONE_BUSY` detail, or null when the detail names none. */
    fun holderOf(detail: String?): String? =
        detail?.trim()?.takeIf { it.startsWith(DETAIL_PREFIX) }?.removePrefix(DETAIL_PREFIX)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The sentence Tess's card and the keyboard's notice show when [holder] has the microphone (approximation,
     * H13). One sentence form for both refused sides, so the two can never disagree about who holds it.
     */
    fun busySentence(holder: String?, assistantName: String): String = when (holder) {
        KEYBOARD -> "The keyboard is using the microphone right now."
        RECORDER -> "The voice recorder is using the microphone right now."
        CORTANA -> "$assistantName is using the microphone right now."
        else -> "Something else is using the microphone right now."
    }

    /**
     * What Voice Recorder shows when its take is refused (E17: "Tess is listening", approximation H13).
     * The other holders get the shared sentence.
     */
    fun recorderNotice(holder: String?, assistantName: String): String = when (holder) {
        CORTANA -> "$assistantName is listening"
        else -> busySentence(holder, assistantName)
    }
}
