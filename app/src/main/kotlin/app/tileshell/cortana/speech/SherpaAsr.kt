package app.tileshell.cortana.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.tileshell.diag.Diagnostics
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Microphone level, 0..1, for the listening waveform's bars (H10).
 *
 * dBFS mapped onto a 60 dB window: digital silence is 0, full scale is 1, and everything quieter than
 * -60 dBFS sits at 0 so a silent room does not make the bars twitch. Pure, so the host tests it.
 */
object AudioLevel {

    /** The quietest RMS that still moves a bar, in dBFS. */
    const val FLOOR_DB = -60f

    fun fromRms(rms: Float): Float {
        if (rms <= 0f || rms.isNaN()) return 0f
        val db = 20f * log10(rms)
        val level = (db - FLOOR_DB) / -FLOOR_DB
        return level.coerceIn(0f, 1f)
    }

    /** RMS of [count] samples starting at [offset], which are normalized to -1..1. */
    fun rms(samples: FloatArray, count: Int, offset: Int = 0): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in offset until offset + count) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / count).toFloat()
    }

    fun ofBlock(samples: FloatArray, count: Int, offset: Int = 0): Float =
        fromRms(rms(samples, count, offset))
}

/** What a capture reports while it runs. Implemented by the service, which forwards over the Binder. */
interface AsrEvents {
    fun onListening()
    fun onPartial(text: String)
    fun onLevel(level: Float)
    fun onFinal(open: String, grammar: String, audioMs: Int)
    fun onError(code: Int, detail: String)
}

/**
 * The streaming recognizer and the microphone, in the `:speech` process.
 *
 * One recognizer, two streams per utterance: the open pass decodes what was actually said, and the
 * grammar pass decodes the same samples with the command phrases boosted. Per-stream hotwords only work
 * under `modified_beam_search`, which is why the decoding method is not greedy.
 */
class SherpaAsr(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000

        /** ~50 ms of audio: one [AsrEvents.onLevel] and one partial check per read. */
        const val BLOCK_SAMPLES = SAMPLE_RATE / 20

        /** A single utterance is capped here and then finished as if the endpoint had fired. */
        const val MAX_UTTERANCE_MS = 30_000
        const val MAX_SAMPLES = SAMPLE_RATE * MAX_UTTERANCE_MS / 1000

        /** Decoding paths kept alive by the beam search; 4 is sherpa-onnx's own default. */
        const val MAX_ACTIVE_PATHS = 4
        const val NUM_THREADS = 2
        const val PROVIDER = "cpu"

        /**
         * `sherpa-onnx-streaming-zipformer-en-2023-06-26` is a zipformer2 export. Named rather than left
         * empty for auto-detection: this is the type sherpa-onnx's own model table gives for this exact
         * model (`getModelConfig(6)` in api_OnlineRecognizer.kt), so it cannot drift on a runtime bump.
         */
        const val MODEL_TYPE = "zipformer2"

        /** Per-stream hotwords exist only under the beam search; greedy decoding has no grammar pass. */
        const val DECODING_METHOD = "modified_beam_search"

        /** With [SpeechAssets.ASR_BPE], this is what tokenizes a plain-text hotword such as a contact name. */
        const val MODELING_UNIT = "bpe"
    }

    @Volatile private var recognizer: OnlineRecognizer? = null

    /** Bumped by every start and every stop; a capture runs only while the value it was given still holds. */
    private val generation = AtomicInteger(0)

    val loaded: Boolean get() = recognizer != null

    /** @param bpeVocab where sherpa-onnx should read `bpe.model`: an asset path or a filesystem path */
    fun config(bpeVocab: String): OnlineRecognizerConfig = OnlineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0.0f),
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = SpeechAssets.ASR_ENCODER,
                decoder = SpeechAssets.ASR_DECODER,
                joiner = SpeechAssets.ASR_JOINER,
            ),
            tokens = SpeechAssets.ASR_TOKENS,
            numThreads = NUM_THREADS,
            debug = false,
            provider = PROVIDER,
            modelType = MODEL_TYPE,
            modelingUnit = MODELING_UNIT,
            bpeVocab = bpeVocab,
        ),
        // The endpoint is what ends an utterance; sherpa-onnx's default rules are the ones this phase uses.
        endpointConfig = EndpointConfig(),
        enableEndpoint = true,
        decodingMethod = DECODING_METHOD,
        maxActivePaths = MAX_ACTIVE_PATHS,
    )

    /**
     * Builds the recognizer from the APK's assets. Call off the main thread.
     *
     * @throws SpeechModelException [SpeechError.MODEL_MISSING] when an asset is absent,
     *         [SpeechError.MODEL_CORRUPT] when sherpa-onnx refuses what is there
     */
    /** Where the loaded engine read `bpe.model` from, for [ISpeech.status] and the device QA run. */
    @Volatile var bpeVocabSource: String = "none"
        private set

    fun load() {
        if (recognizer != null) return
        val assets = context.assets
        SpeechAssets.firstMissing(assets, SpeechAssets.ASR_FILES)?.let { missing ->
            throw SpeechModelException(SpeechError.MODEL_MISSING, "ASR asset missing: $missing")
        }
        val started = System.currentTimeMillis()

        // The asset path is what the rest of this config uses and what `newFromAsset` is for: tokens and
        // the three .onnx files are asset paths too, so sherpa-onnx cannot be stat()ing them. If this
        // build of sherpa-onnx nonetheless wants a real file for the BPE vocabulary, the second attempt
        // hands it one, extracted under the same checksum discipline as espeak-ng-data. Which one won is
        // recorded in [bpeVocabSource] and in the diagnostics ring.
        val assetAttempt = try {
            SpeechBreadcrumb.enter("asr construct (bpeVocab as an asset)")
            OnlineRecognizer(assetManager = assets, config = config(SpeechAssets.ASR_BPE))
                .also { SpeechBreadcrumb.done("asr construct (bpeVocab as an asset)") }
        } catch (t: Throwable) {
            Diagnostics.add("speech", "asr: construction with bpeVocab as an asset path failed: $t")
            null
        }
        if (assetAttempt != null) {
            recognizer = assetAttempt
            bpeVocabSource = "asset:${SpeechAssets.ASR_BPE}"
        } else {
            val extracted = ExtractedAsset.ensure(
                context = context,
                assetPath = SpeechAssets.ASR_BPE,
                fileName = "bpe.model",
                expectedSha256 = SpeechAssets.ASR_BPE_SHA256,
            )
            val fileAttempt = try {
                SpeechBreadcrumb.enter("asr construct (bpeVocab extracted)")
                OnlineRecognizer(assetManager = assets, config = config(extracted.absolutePath))
                    .also { SpeechBreadcrumb.done("asr construct (bpeVocab extracted)") }
            } catch (t: Throwable) {
                Diagnostics.add("speech", "asr: construction with an extracted bpe.model failed too: $t")
                throw SpeechModelException(SpeechError.MODEL_CORRUPT, "ASR model rejected: $t", t)
            }
            recognizer = fileAttempt
            bpeVocabSource = "file:${extracted.absolutePath}"
        }

        Diagnostics.add(
            "speech",
            "asr: loaded in ${System.currentTimeMillis() - started} ms " +
                "($MODEL_TYPE/$DECODING_METHOD, paths=$MAX_ACTIVE_PATHS, threads=$NUM_THREADS, " +
                "endpoint=on, modelingUnit=$MODELING_UNIT, bpeVocab=$bpeVocabSource)",
        )
    }

    fun release() {
        val current = recognizer ?: return
        interrupt()
        recognizer = null
        bpeVocabSource = "none"
        runCatching { current.release() }.onFailure { Diagnostics.add("speech", "asr: release threw $it") }
        Diagnostics.add("speech", "asr: released")
    }

    /**
     * Ends any capture that is running and claims the next generation.
     *
     * Called on the Binder thread, never queued behind the capture loop — that loop occupies the ASR
     * worker for the whole utterance, so a queued stop would never run.
     *
     * @return the generation a capture started now should carry
     */
    fun interrupt(): Int = generation.incrementAndGet()

    val listening: Boolean get() = capturing

    @Volatile private var capturing = false

    /**
     * Opens the microphone and decodes until the endpoint, [interrupt], or [MAX_UTTERANCE_MS].
     *
     * Runs on the ASR worker thread for the whole utterance and always emits exactly one terminal event
     * ([AsrEvents.onFinal] or [AsrEvents.onError]) unless it was superseded before it started.
     */
    fun capture(hotwords: String, myGeneration: Int, events: AsrEvents) {
        if (generation.get() != myGeneration) {
            Diagnostics.add("speech", "asr: capture $myGeneration superseded before it started")
            return
        }
        val engine = recognizer
        if (engine == null) {
            events.onError(SpeechError.MODEL_MISSING, "ASR engine is not loaded")
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Diagnostics.add("speech", "asr: RECORD_AUDIO not granted")
            events.onError(SpeechError.NO_MICROPHONE_PERMISSION, "RECORD_AUDIO is not granted")
            return
        }

        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBytes <= 0) {
            Diagnostics.add("speech", "asr: getMinBufferSize returned $minBytes")
            events.onError(SpeechError.AUDIO_UNAVAILABLE, "AudioRecord buffer size unavailable ($minBytes)")
            return
        }
        // Four blocks of headroom so a slow decode does not overrun the hardware buffer.
        val bufferBytes = max(minBytes, BLOCK_SAMPLES * 2 * 4)

        val record = try {
            @Suppress("MissingPermission")
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            )
        } catch (t: Throwable) {
            Diagnostics.add("speech", "asr: AudioRecord construction failed: $t")
            events.onError(SpeechError.AUDIO_UNAVAILABLE, "AudioRecord unavailable: $t")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Diagnostics.add("speech", "asr: AudioRecord state ${record.state}")
            runCatching { record.release() }
            events.onError(SpeechError.AUDIO_UNAVAILABLE, "AudioRecord did not initialize")
            return
        }
        try {
            record.startRecording()
        } catch (t: Throwable) {
            Diagnostics.add("speech", "asr: startRecording failed: $t")
            runCatching { record.release() }
            events.onError(SpeechError.AUDIO_UNAVAILABLE, "startRecording failed: $t")
            return
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Diagnostics.add("speech", "asr: recordingState ${record.recordingState}")
            runCatching { record.stop() }
            runCatching { record.release() }
            events.onError(SpeechError.AUDIO_UNAVAILABLE, "the microphone did not start")
            return
        }

        var open: OnlineStream? = null
        var grammar: OnlineStream? = null
        capturing = true
        try {
            open = engine.createStream()
            grammar = if (hotwords.isNotBlank()) engine.createStream(hotwords) else null
            Diagnostics.add(
                "speech",
                "asr: listening gen=$myGeneration grammar=${if (grammar != null) "on" else "off"} " +
                    "hotwordLines=${hotwords.lineSequence().count { it.isNotBlank() }}",
            )
            events.onListening()

            val shorts = ShortArray(BLOCK_SAMPLES)
            val floats = FloatArray(BLOCK_SAMPLES)
            var totalSamples = 0
            var lastPartial = ""
            var endpointed = false
            var readError: String? = null

            while (generation.get() == myGeneration && totalSamples < MAX_SAMPLES) {
                val read = record.read(shorts, 0, shorts.size)
                if (read < 0) {
                    readError = "AudioRecord.read returned $read"
                    break
                }
                if (read == 0) continue
                for (i in 0 until read) floats[i] = shorts[i] / 32768.0f
                totalSamples += read

                val block = if (read == floats.size) floats else floats.copyOf(read)
                open.acceptWaveform(block, SAMPLE_RATE)
                grammar?.acceptWaveform(block, SAMPLE_RATE)
                while (engine.isReady(open)) engine.decode(open)
                grammar?.let { stream -> while (engine.isReady(stream)) engine.decode(stream) }

                val partial = engine.getResult(open).text
                if (partial != lastPartial) {
                    lastPartial = partial
                    events.onPartial(partial)
                }
                events.onLevel(AudioLevel.ofBlock(floats, read))

                if (engine.isEndpoint(open)) {
                    endpointed = true
                    break
                }
            }

            runCatching { record.stop() }
            if (readError != null) {
                Diagnostics.add("speech", "asr: $readError")
                events.onError(SpeechError.AUDIO_UNAVAILABLE, readError)
                return
            }

            // Drain: the tail of the audio is still in the feature pipeline until inputFinished.
            open.inputFinished()
            while (engine.isReady(open)) engine.decode(open)
            grammar?.let { stream ->
                stream.inputFinished()
                while (engine.isReady(stream)) engine.decode(stream)
            }
            // Verbatim, both passes. This model emits UPPERCASE with no punctuation; normalising it here
            // would hide that from the matcher in the launcher process, which is what owns normalisation.
            val openText = engine.getResult(open).text
            val grammarText = grammar?.let { engine.getResult(it).text } ?: ""
            val audioMs = (totalSamples.toLong() * 1000L / SAMPLE_RATE).toInt()
            val ending = when {
                endpointed -> "endpoint"
                totalSamples >= MAX_SAMPLES -> "30s cap"
                else -> "stopped"
            }
            Diagnostics.add(
                "speech",
                "asr: final gen=$myGeneration via $ending audioMs=$audioMs " +
                    "open=\"$openText\" grammar=\"$grammarText\"",
            )
            events.onFinal(openText, grammarText, audioMs)
        } catch (t: Throwable) {
            Diagnostics.add("speech", "asr: capture failed: $t")
            events.onError(SpeechError.INTERNAL, "capture failed: $t")
        } finally {
            capturing = false
            runCatching { record.stop() }
            runCatching { record.release() }
            runCatching { open?.release() }
            runCatching { grammar?.release() }
        }
    }
}
