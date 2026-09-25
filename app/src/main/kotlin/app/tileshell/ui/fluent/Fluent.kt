package app.tileshell.ui.fluent

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.ui.graphics.Color
import app.tileshell.diag.Diagnostics
import app.tileshell.prefs.ShellSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Phase 13's materials engine: Fluent acrylic on the shell's transient surfaces and the two lights on press.
 *
 * Every acrylic value is an approximation from Microsoft's Fluent documentation (phase 13 Decisions "The material",
 * RV9 source 2, judged in H2): a Gaussian blur of what is BEHIND the surface, radius 30 epx; a tint T at opacity 0.8;
 * a ± 5-level noise from a hash of the pixel position. Draw order: blurred backdrop → tint → noise.
 */
object FluentMaterial {
    /** Fluent's 30 effective px; it scales with px/epx like every RV10 value (90 px at FHD+). */
    const val RADIUS_EPX = 30f
    /** Fluent's in-app tint opacity. */
    const val ALPHA = 0.8f
    /** The noise: an integer level in −5..+5 added to each channel (2 % of 255). */
    const val NOISE_LEVELS = 5

    /** HWUI's radius → sigma conversion (Skia's `BlurMask::ConvertRadiusToSigma`), confirmed at build start. */
    fun sigmaPx(radiusPx: Float): Float = 0.57735f * radiusPx + 0.5f

    /** How far past a surface's edge the blur reads (3σ): the live source's S layer grows by this on every side. */
    fun marginPx(radiusPx: Float): Float = ceil(3f * sigmaPx(radiusPx))

    /**
     * The tint that keeps a measured fill in its own setup (Decisions "Tint derivation", interview Q1 A):
     * T = (F − (1 − α)·B_m) / α per channel, so that over the backdrop it was measured on, B_m, the material reads
     * exactly F. Null when any channel would fall below 0 — a fill darker than 20 % of its backdrop cannot be
     * acrylic at this α and the surface stays solid (H7).
     */
    fun deriveTint(fill: Rgb, measuredBackdrop: Rgb): Rgb? {
        fun ch(f: Int, b: Int): Int? {
            val t = (f - (1f - ALPHA) * b) / ALPHA
            if (t < 0f) return null
            return floor(t + 0.5f).toInt().coerceAtMost(255)
        }
        return Rgb(
            ch(fill.r, measuredBackdrop.r) ?: return null,
            ch(fill.g, measuredBackdrop.g) ?: return null,
            ch(fill.b, measuredBackdrop.b) ?: return null,
        )
    }

    /** What the material reads over a flat backdrop B, before noise: 0.8·T + 0.2·B, rounded. */
    fun over(tint: Rgb, backdrop: Rgb): Rgb = Rgb(
        (ALPHA * tint.r + (1f - ALPHA) * backdrop.r).roundToInt(),
        (ALPHA * tint.g + (1f - ALPHA) * backdrop.g).roundToInt(),
        (ALPHA * tint.b + (1f - ALPHA) * backdrop.b).roundToInt(),
    )
}

data class Rgb(val r: Int, val g: Int, val b: Int) {
    fun toColor(): Color = Color(r, g, b)
    override fun toString(): String = "($r,$g,$b)"

    companion object {
        fun of(color: Color): Rgb = Rgb(
            (color.red * 255f).roundToInt(),
            (color.green * 255f).roundToInt(),
            (color.blue * 255f).roundToInt(),
        )
    }
}

/** Where a surface's blurred picture comes from (Decisions "Two in-app backdrop sources, one material"). */
enum class FluentSource(val word: String) { STATIC("static"), LIVE("live") }

/**
 * The surface table (Decisions "The surface table") — the COMPLETE list of this phase's surfaces (T13-19). The id
 * is the `acrylic:<id>` test tag and the `[fluent] <id>` diagnostics name.
 */
enum class FluentSurface(val id: String, val source: FluentSource) {
    APPLIST("applist", FluentSource.STATIC),
    APPLIST_MENU("applist_menu", FluentSource.LIVE),
    MUSIC_MENU("music_menu", FluentSource.LIVE),
    CORTANA_PANE("cortana_pane", FluentSource.LIVE),
    REMINDER_MENU("reminder_menu", FluentSource.LIVE),
}

/** Why acrylic is off, in precedence order (T13-20): the first false term of the rule names it. */
enum class FluentReason(val word: String) {
    NONE("none"),
    LOW_RAM("low-ram"),
    BATTERY_SAVER("battery-saver"),
    SETTING("setting"),
}

data class FluentState(val on: Boolean, val reason: FluentReason) {
    fun line(): String = "acrylic=${if (on) "on" else "off"} reason=${reason.word}"
}

/**
 * The on / off rule (Decisions "On / off rule", Fluent's own): acrylic = transparencyEffects ∧ ¬battery saver ∧
 * ¬low-RAM. The reason is the first false term in the order low-ram, battery-saver, setting (T13-20: phase 12's
 * preset edge case expects `battery-saver` when a preset turns the switch off under battery saver).
 */
object FluentRule {
    fun decide(transparencyEffects: Boolean, powerSave: Boolean, lowRam: Boolean): FluentState = when {
        lowRam -> FluentState(false, FluentReason.LOW_RAM)
        powerSave -> FluentState(false, FluentReason.BATTERY_SAVER)
        !transparencyEffects -> FluentState(false, FluentReason.SETTING)
        else -> FluentState(true, FluentReason.NONE)
    }
}

/**
 * The noise's level at a pixel: an integer in −[FluentMaterial.NOISE_LEVELS]..+[FluentMaterial.NOISE_LEVELS] from
 * a hash of the pixel position (deterministic, no asset, no allocation). The GPU runs the same formula in
 * [FluentEffects]' shader; this copy exists so the level's range and spread are tested on the JVM.
 */
object FluentNoise {
    fun hash(x: Int, y: Int): Double {
        // Dave Hoskins' "hash without sine" (hash12): stable in float precision, unlike fract(sin(…)).
        fun fract(v: Double) = v - floor(v)
        var p0 = fract(x * 0.1031)
        var p1 = fract(y * 0.1031)
        var p2 = fract(x * 0.1031)
        val d = p0 * (p1 + 33.33) + p1 * (p2 + 33.33) + p2 * (p0 + 33.33)
        p0 += d; p1 += d; p2 += d
        return fract((p0 + p1) * p2)
    }

    fun level(x: Int, y: Int): Int =
        floor(hash(x, y) * (2 * FluentMaterial.NOISE_LEVELS + 1)).toInt() - FluentMaterial.NOISE_LEVELS
}

/**
 * The live on / off state, followed through the setting's flow and `ACTION_POWER_SAVE_MODE_CHANGED`, so a surface
 * that is showing when battery saver turns on redraws to its fallback in the next frame. Runs in the launcher's
 * process only ([start] is called from its start-up), and writes `[fluent] acrylic=… reason=…` at process start
 * and whenever the value or the reason changes.
 */
object Fluent {
    private val mutable = MutableStateFlow(FluentState(true, FluentReason.NONE))
    val state: StateFlow<FluentState> = mutable.asStateFlow()

    private var started = false
    private var transparencyEffects = true
    private var powerSave = false
    private var lowRam = false
    private var logged: FluentState? = null

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val power = app.getSystemService(PowerManager::class.java)
        lowRam = app.getSystemService(ActivityManager::class.java).isLowRamDevice
        powerSave = power.isPowerSaveMode
        val settings = ShellSettings.get(app)
        transparencyEffects = settings.theme.value.transparencyEffects
        publish()
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                powerSave = power.isPowerSaveMode
                publish()
            }
        }, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            settings.theme.map { it.transparencyEffects }.distinctUntilChanged().collect {
                transparencyEffects = it
                publish()
            }
        }
    }

    @Synchronized
    private fun publish() {
        val next = FluentRule.decide(transparencyEffects, powerSave, lowRam)
        if (next != logged) {
            logged = next
            Diagnostics.add("fluent", next.line())
        }
        mutable.value = next
    }

    /** `[fluent] <surface> source=… tint=(r,g,b) alpha=0.8 blur=30epx`, each time a surface is shown. */
    fun logShown(surface: FluentSurface, tint: Rgb) {
        Diagnostics.add(
            "fluent",
            "${surface.id} source=${surface.source.word} tint=$tint alpha=${FluentMaterial.ALPHA} " +
                "blur=${FluentMaterial.RADIUS_EPX.roundToInt()}epx",
        )
    }

    /** `[fluent] <surface> form=acrylic|fallback`, each time a shown surface changes form (T13-9). */
    fun logForm(surface: FluentSurface, acrylic: Boolean) {
        Diagnostics.add("fluent", "${surface.id} form=${if (acrylic) "acrylic" else "fallback"}")
    }
}
