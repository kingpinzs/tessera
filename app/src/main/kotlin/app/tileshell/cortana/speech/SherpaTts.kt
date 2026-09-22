package app.tileshell.cortana.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioTrack
import app.tileshell.diag.Diagnostics
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/** What a spoken utterance reports while it plays. Implemented by the service. */
interface TtsEvents {
    fun onSpeakingLevel(utteranceId: String, level: Float)
    fun onSpeakingDone(utteranceId: String, cancelled: Boolean)
    fun onError(code: Int, detail: String)
}

/**
 * Kokoro en v0.19 and the AudioTrack it plays through, in the `:speech` process.
 *
 * Synthesis streams: `generateWithCallback` hands back audio in chunks and each chunk is written to a
 * MODE_STREAM track as it arrives, so speech starts before the sentence is finished. espeak-ng's data
 * cannot be read out of the APK, so [EspeakData] unpacks it and its real path is the engine's `dataDir`.
 */
class SherpaTts(private val context: Context) {

    companion object {
        const val SPEED = 1.0f
        const val NUM_THREADS = 2
        const val PROVIDER = "cpu"

        /** ~50 ms of audio per level report, which is what the speaking halo steps on (51 ± 17 ms). */
        const val LEVEL_INTERVAL_MS = 50
    }

    @Volatile private var tts: OfflineTts? = null
    @Volatile private var dataDir: File? = null

    /** What the loaded model reports, or -1 before it is loaded. */
    @Volatile var numSpeakers: Int = -1
        private set

    @Volatile var sampleRate: Int = 0
        private set

    /** Bumped by every speak and every stop; an utterance plays only while the value it was given holds. */
    private val generation = AtomicInteger(0)

    @Volatile private var track: AudioTrack? = null

    val loaded: Boolean get() = tts != null

    val espeakDataDir: File? get() = dataDir

    /**
     * Unpacks espeak-ng's data, then builds the engine. Call off the main thread.
     *
     * @throws SpeechModelException [SpeechError.ESPEAK_DATA_BAD] when the data cannot be trusted (no
     *         engine is built — a broken data directory mispronounces silently),
     *         [SpeechError.MODEL_MISSING] / [SpeechError.MODEL_CORRUPT] for the model itself
     */
    fun load() {
        if (tts != null) return
        val assets = context.assets
        SpeechAssets.firstMissing(assets, SpeechAssets.TTS_FILES)?.let { missing ->
            throw SpeechModelException(SpeechError.MODEL_MISSING, "TTS asset missing: $missing")
        }
        val espeak = EspeakData.ensure(context)
        dataDir = espeak

        val started = System.currentTimeMillis()
        val built = try {
            OfflineTts(assetManager = assets, config = config(espeak.absolutePath))
        } catch (t: Throwable) {
            Diagnostics.add("speech", "tts: engine construction failed: $t")
            throw SpeechModelException(SpeechError.MODEL_CORRUPT, "TTS model rejected: $t", t)
        }
        tts = built
        sampleRate = runCatching { built.sampleRate() }.getOrDefault(0)
        numSpeakers = runCatching { built.numSpeakers() }.getOrDefault(-1)
        Diagnostics.add(
            "speech",
            "tts: loaded in ${System.currentTimeMillis() - started} ms " +
                "(kokoro int8, sampleRate=$sampleRate, speakers=$numSpeakers, threads=$NUM_THREADS)",
        )
        if (numSpeakers != Voices.EXPECTED_SPEAKERS) {
            Diagnostics.add(
                "speech",
                "tts: VOICE TABLE MISMATCH - model reports $numSpeakers speakers, " +
                    "the bundled table names ${Voices.EXPECTED_SPEAKERS}; " +
                    "only ids 0..${numSpeakers - 1} will be offered",
            )
        }
    }

    fun config(espeakDataDir: String): OfflineTtsConfig = OfflineTtsConfig(
        model = OfflineTtsModelConfig(
            kokoro = OfflineTtsKokoroModelConfig(
                model = SpeechAssets.TTS_MODEL,
                voices = SpeechAssets.TTS_VOICES,
                tokens = SpeechAssets.TTS_TOKENS,
                // A real filesystem path: espeak-ng opens its data by path and cannot read the APK.
                dataDir = espeakDataDir,
            ),
            numThreads = NUM_THREADS,
            debug = false,
            provider = PROVIDER,
        ),
    )

    fun release() {
        val current = tts ?: return
        interrupt()
        tts = null
        numSpeakers = -1
        sampleRate = 0
        runCatching { current.release() }.onFailure { Diagnostics.add("speech", "tts: release threw $it") }
        Diagnostics.add("speech", "tts: released")
    }

    /**
     * Cancels whatever is playing and claims the next generation.
     *
     * Called on the Binder thread. The playing utterance sees the generation change, stops generating,
     * flushes the track and reports `onSpeakingDone(cancelled = true)` before the next one starts, because
     * both run on the same TTS worker.
     */
    fun interrupt(): Int {
        val next = generation.incrementAndGet()
        // Cut the audio already queued in the hardware buffer; the loop itself unwinds on the generation.
        track?.let { t ->
            runCatching { t.pause() }
            runCatching { t.flush() }
        }
        return next
    }

    val speaking: Boolean get() = track != null

    /**
     * Synthesizes and plays [text], reporting level and exactly one done. Runs on the TTS worker thread.
     */
    fun speak(utteranceId: String, text: String, speakerId: Int, myGeneration: Int, events: TtsEvents) {
        if (generation.get() != myGeneration) {
            Diagnostics.add("speech", "tts: $utteranceId superseded before it started")
            events.onSpeakingDone(utteranceId, true)
            return
        }
        val engine = tts
        if (engine == null) {
            events.onError(SpeechError.MODEL_MISSING, "TTS engine is not loaded")
            events.onSpeakingDone(utteranceId, true)
            return
        }
        val rate = if (sampleRate > 0) sampleRate else runCatching { engine.sampleRate() }.getOrDefault(24000)
        val sid = speakerId.coerceIn(0, max(0, numSpeakers - 1))
        if (sid != speakerId) {
            Diagnostics.add("speech", "tts: speaker $speakerId out of range (0..${numSpeakers - 1}); using $sid")
        }

        val audioManager = context.getSystemService(AudioManager::class.java)
        val attributes = audioAttributes()
        var focus: AudioFocusRequest? = null
        var cancelled = false
        var framesWritten = 0L
        val started = System.currentTimeMillis()

        val player = buildTrack(attributes, rate)
        if (player == null) {
            events.onError(SpeechError.AUDIO_UNAVAILABLE, "AudioTrack unavailable at $rate Hz")
            events.onSpeakingDone(utteranceId, true)
            return
        }
        track = player

        try {
            // Music playing while Cortana speaks: duck it, and hand it back the moment she is done.
            if (audioManager != null) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes)
                    .build()
                focus = request
                val granted = runCatching { audioManager.requestAudioFocus(request) }
                    .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Diagnostics.add("speech", "tts: audio focus not granted ($granted); speaking anyway")
                }
            }

            player.play()
            val levelChunk = max(1, rate * LEVEL_INTERVAL_MS / 1000)

            engine.generateWithCallback(text = text, sid = sid, speed = SPEED) { samples ->
                if (generation.get() != myGeneration) {
                    cancelled = true
                    return@generateWithCallback 0
                }
                var offset = 0
                while (offset < samples.size) {
                    if (generation.get() != myGeneration) {
                        cancelled = true
                        return@generateWithCallback 0
                    }
                    val count = min(levelChunk, samples.size - offset)
                    // Mono float PCM: one float is one frame.
                    val wrote = player.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
                    if (wrote < 0) {
                        Diagnostics.add("speech", "tts: AudioTrack.write returned $wrote")
                        cancelled = true
                        return@generateWithCallback 0
                    }
                    framesWritten += wrote
                    if (wrote > 0) {
                        events.onSpeakingLevel(utteranceId, AudioLevel.ofBlock(samples, wrote, offset))
                    }
                    offset += wrote
                    if (wrote < count) {
                        // A blocking write only comes up short when the track was stopped or flushed under
                        // us, which is what [interrupt] does. Stop generating rather than spin.
                        Diagnostics.add("speech", "tts: short write ($wrote of $count); treating as cancelled")
                        cancelled = true
                        return@generateWithCallback 0
                    }
                }
                1
            }

            if (generation.get() != myGeneration) cancelled = true
            if (!cancelled) drain(player, framesWritten, rate, myGeneration)
            if (generation.get() != myGeneration) cancelled = true

            Diagnostics.add(
                "speech",
                "tts: $utteranceId sid=$sid frames=$framesWritten " +
                    "(${framesWritten * 1000 / max(1, rate)} ms) in ${System.currentTimeMillis() - started} ms " +
                    "cancelled=$cancelled",
            )
        } catch (t: Throwable) {
            Diagnostics.add("speech", "tts: $utteranceId failed: $t")
            events.onError(SpeechError.INTERNAL, "speak failed: $t")
            cancelled = true
        } finally {
            if (cancelled) {
                runCatching { player.pause() }
                runCatching { player.flush() }
            }
            runCatching { player.stop() }
            runCatching { player.release() }
            if (track === player) track = null
            focus?.let { request ->
                runCatching { audioManager?.abandonAudioFocusRequest(request) }
            }
            events.onSpeakingDone(utteranceId, cancelled)
        }
    }

    /** Waits out what is still in the hardware buffer, so done means done playing, not done generating. */
    private fun drain(player: AudioTrack, framesWritten: Long, rate: Int, myGeneration: Int) {
        var guard = 0
        while (generation.get() == myGeneration && guard < MAX_DRAIN_SLICES) {
            val played = runCatching { player.playbackHeadPosition.toLong() and 0xFFFFFFFFL }.getOrDefault(framesWritten)
            val remaining = framesWritten - played
            if (remaining <= 0) break
            val remainingMs = remaining * 1000L / max(1, rate)
            Thread.sleep(min(remainingMs, DRAIN_SLICE_MS.toLong()).coerceAtLeast(1L))
            guard++
        }
    }

    /**
     * USAGE_ASSISTANT is what Cortana's voice is; a device that refuses it gets plain media usage rather
     * than no speech at all.
     */
    private fun audioAttributes(): AudioAttributes = try {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    } catch (t: Throwable) {
        Diagnostics.add("speech", "tts: USAGE_ASSISTANT rejected ($t); falling back to USAGE_MEDIA")
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    private fun buildTrack(attributes: AudioAttributes, rate: Int): AudioTrack? {
        val minBytes = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBytes <= 0) {
            Diagnostics.add("speech", "tts: getMinBufferSize returned $minBytes at $rate Hz")
            return null
        }
        // Float samples, 4 bytes each: half a second of headroom keeps the write ahead of the speaker.
        val bufferBytes = max(minBytes, rate * 4 / 2)
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            Diagnostics.add("speech", "tts: AudioTrack construction failed: $t")
            null
        }
    }
}

private const val DRAIN_SLICE_MS = 20

/** 30 s of slices: a bound so a stuck playback head cannot hold the TTS worker forever. */
private const val MAX_DRAIN_SLICES = 30_000 / DRAIN_SLICE_MS
