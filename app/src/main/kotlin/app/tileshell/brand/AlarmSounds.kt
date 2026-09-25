package app.tileshell.brand

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Alarms & Clock's alarm sounds (phase 15 Decisions "Ringing"; approximation H8).
 *
 * Branding module rule (A10, phase 03's sounds precedent, [KeyClick]): W10M's alarm sounds are Microsoft assets,
 * so what ships is ORIGINAL sound-alikes synthesised here — a few seconds each, looped by the ring — and a real
 * file can be dropped in for a personal build as `assets/brand/alarm_<id>.wav`. Nothing Microsoft ships in the
 * APK, and nothing is fetched (offline preferred, A11 as amended).
 *
 * A sound is addressed as `tessera-sound:<id>` wherever an alarm stores a sound URI.
 */
object AlarmSounds {
    const val SCHEME = "tessera-sound"
    private const val RATE = 44_100

    data class Sound(val id: String, val title: String)

    /** In the Sounds page's order; the first is the default an alarm rings with. */
    val all = listOf(
        Sound("classic", "Alarm Clock"),
        Sound("beep", "Beep-beep"),
        Sound("chime", "Chime"),
        Sound("rise", "Rise"),
        Sound("pulse", "Pulse"),
    )

    val default: Sound get() = all.first()

    fun uri(id: String) = "$SCHEME:$id"

    /** The sound a `tessera-sound:<id>` URI names, or null for any other URI. */
    fun byUri(uri: String?): Sound? = uri?.takeIf { it.startsWith("$SCHEME:") }?.removePrefix("$SCHEME:")?.let { id -> all.firstOrNull { it.id == id } }

    /** A playable file: the dropped-in asset when there is one, else the synthesised sound-alike. */
    fun file(context: Context, sound: Sound): File {
        // Device-protected cache: the ring can run before the first unlock after a reboot (T15-22).
        val dir = File(context.createDeviceProtectedStorageContext().cacheDir, "alarm_sounds").apply { mkdirs() }
        val out = File(dir, "${sound.id}.wav")
        val dropIn = runCatching { context.assets.open("brand/alarm_${sound.id}.wav").use { it.readBytes() } }.getOrNull()
        val bytes = dropIn ?: synthesise(sound.id)
        if (!out.exists() || out.length() != bytes.size.toLong()) out.writeBytes(bytes)
        return out
    }

    /** One loop of the sound: a pattern of tones with soft attacks, then silence before it repeats. */
    fun synthesise(id: String): ByteArray {
        // Each note: start (s), length (s), frequencies (Hz).
        data class Note(val at: Double, val len: Double, val hz: List<Double>)
        val (loop, notes) = when (id) {
            "beep" -> 1.2 to listOf(Note(0.0, 0.12, listOf(1760.0)), Note(0.2, 0.12, listOf(1760.0)))
            "chime" -> 2.4 to listOf(Note(0.0, 0.9, listOf(1046.5, 1568.0)), Note(0.45, 0.9, listOf(784.0, 1174.7)), Note(0.9, 1.2, listOf(1046.5, 1318.5)))
            "rise" -> 2.0 to (0 until 5).map { i -> Note(i * 0.28, 0.22, listOf(523.25 * Math.pow(2.0, i / 4.0))) }
            "pulse" -> 1.0 to listOf(Note(0.0, 0.35, listOf(880.0, 1320.0)))
            else -> 1.6 to (0 until 4).map { i -> Note(i * 0.16, 0.1, listOf(2093.0, 1046.5)) } // "classic": a bell-like ring
        }
        val n = (RATE * loop).toInt()
        val pcm = DoubleArray(n)
        for (note in notes) {
            val start = (note.at * RATE).toInt()
            val len = (note.len * RATE).toInt()
            for (i in 0 until min(len, n - start)) {
                val t = i.toDouble() / RATE
                val env = min(1.0, t / 0.005) * exp(-t / (note.len * 0.45))
                var v = 0.0
                note.hz.forEachIndexed { k, hz -> v += sin(2 * PI * hz * t) / (k + 1) }
                pcm[start + i] += v * env
            }
        }
        val peak = pcm.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-9)
        return wav(ShortArray(n) { (pcm[it] / peak * 0.7 * Short.MAX_VALUE).toInt().toShort() })
    }

    private fun wav(pcm: ShortArray): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort(it) }
        val body = data.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + body.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(body.size)
        }.array()
        return ByteArrayOutputStream().apply { write(header); write(body) }.toByteArray()
    }
}
