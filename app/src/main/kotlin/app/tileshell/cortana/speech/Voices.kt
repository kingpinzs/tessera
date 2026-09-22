package app.tileshell.cortana.speech

/**
 * The bundled Kokoro en v0.19 voices, in speaker-id order.
 *
 * The ids are the model's own: `voices.bin` holds eleven speaker embeddings and sherpa-onnx addresses
 * them by index, so the id is not ours to choose. The names and the one-line descriptions ARE ours —
 * Kokoro ships identifiers (`af_bella`, `bm_george`), not names a Settings page can show, and the
 * descriptions are listening notes, not model metadata. They are approximations until someone hears
 * them on the phone (the phase's NEEDS-HUMAN row).
 *
 * Emitted as `id|name|description` lines, which is what [SpeechClient.voices] splits on, so neither
 * field may contain `|` or a newline ([VoicesTest] holds that).
 */
data class BundledVoice(val id: Int, val name: String, val description: String, val kokoroId: String)

object Voices {

    /** Kokoro en v0.19 ships eleven speakers; [SherpaTts] checks the loaded model against this. */
    const val EXPECTED_SPEAKERS = 11

    /**
     * Speaker 2 is `af_nicole`, and [app.tileshell.cortana.CortanaPrefsState.DEFAULT_VOICE_ID] is 2.
     * That file's comment calls 2 `af_bella`; `af_bella` is 1 in this ordering. The id the launcher
     * defaults to is unchanged either way — only the identifier written in the comment differs.
     */
    val BUNDLED: List<BundledVoice> = listOf(
        BundledVoice(0, "Ava", "American female, the model's default blend", "af"),
        BundledVoice(1, "Bella", "Warm American female", "af_bella"),
        BundledVoice(2, "Nicole", "Warm, close American female", "af_nicole"),
        BundledVoice(3, "Sarah", "Even, unhurried American female", "af_sarah"),
        BundledVoice(4, "Sky", "Bright, light American female", "af_sky"),
        BundledVoice(5, "Adam", "Clear American male", "am_adam"),
        BundledVoice(6, "Michael", "Low, steady American male", "am_michael"),
        BundledVoice(7, "Emma", "Measured British female", "bf_emma"),
        BundledVoice(8, "Isabella", "Bright British female", "bf_isabella"),
        BundledVoice(9, "George", "British male", "bm_george"),
        BundledVoice(10, "Lewis", "Soft, low British male", "bm_lewis"),
    )

    /**
     * The `id|name|description` lines [ISpeech.voices] returns.
     *
     * @param numSpeakers what the loaded model reports. Only ids the model actually has are listed, so a
     *        model that ever ships with fewer speakers cannot offer a voice that would fail to synthesize.
     *        Ids beyond this table are dropped too: an unnamed voice has nothing to show in Settings.
     */
    fun lines(numSpeakers: Int = EXPECTED_SPEAKERS): String =
        BUNDLED.asSequence()
            .filter { it.id in 0 until numSpeakers }
            .joinToString("\n") { "${it.id}|${it.name}|${it.description}" }
}
