package app.tileshell.brand

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The keyboard's key-press sound (phase 05 Decisions "Key sounds and vibration", approximation H12).
 *
 * Branding module rule (A10, phase 03's sounds precedent): the shipped sound is an ORIGINAL sound-alike,
 * synthesised here, and a real Microsoft sound file can be dropped in for a personal build as
 * `assets/brand/key_click.wav`, exactly as Segoe UI can replace Selawik. Nothing Microsoft ships in the APK.
 *
 * The sound-alike is a soft tick: 18 ms of a 2.2 kHz tone with a 1.1 kHz body under it, 1 ms attack
 * and a fast exponential decay — short enough that fast typing never overlaps into a buzz.
 */
object KeyClick {
    const val DROP_IN_ASSET = "brand/key_click.wav"
    private const val RATE = 44_100

    /** A playable file: the dropped-in asset when there is one, else the synthesised click. */
    fun file(context: Context): File {
        val out = File(context.cacheDir, "key_click.wav")
        val dropIn = runCatching { context.assets.open(DROP_IN_ASSET).use { it.readBytes() } }.getOrNull()
        val bytes = dropIn ?: synthesise()
        if (!out.exists() || out.length() != bytes.size.toLong()) out.writeBytes(bytes)
        return out
    }

    fun synthesise(): ByteArray {
        val n = RATE * 18 / 1000
        val pcm = ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val attack = (t / 0.001).coerceAtMost(1.0)
            val env = attack * exp(-t / 0.004)
            val v = 0.55 * sin(2 * PI * 2200 * t) + 0.35 * sin(2 * PI * 1100 * t)
            (v * env * 0.6 * Short.MAX_VALUE).toInt().toShort()
        }
        return wav(pcm)
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
