package app.tileshell.ui.fluent

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader

/**
 * The material as one platform effect: HWUI's Gaussian blur, then the tint at α and the noise in one AGSL pass.
 * Both backdrop sources use it — the live source on its S layer, the static source once, offscreen — so they
 * share one σ (T13-18) and one tint / noise formula.
 */
object FluentEffects {
    // `content` is the blurred backdrop (premultiplied; a transparent pixel reads as black, the window's own
    // background). The noise hash is FluentNoise.hash, the same formula, on the pixel's integer position.
    private const val TINT_NOISE_AGSL = """
        uniform shader content;
        uniform float3 tint;
        uniform float alpha;
        uniform float levels;

        float hash12(float2 p) {
            float3 p3 = fract(float3(p.xyx) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        half4 main(float2 coord) {
            half4 c = content.eval(coord);
            float3 rgb = mix(float3(c.rgb), tint, alpha);
            float level = floor(hash12(floor(coord)) * (2.0 * levels + 1.0)) - levels;
            rgb = clamp(rgb + level / 255.0, 0.0, 1.0);
            return half4(half3(rgb), 1.0);
        }
    """

    private data class Key(val radiusPx: Float, val tint: Rgb)
    private val cache = HashMap<Key, RenderEffect>()

    /** Blur (radius in px, HWUI's radius → σ) → tint at [FluentMaterial.ALPHA] → noise. */
    @Synchronized
    fun material(radiusPx: Float, tint: Rgb): RenderEffect = cache.getOrPut(Key(radiusPx, tint)) {
        val shader = RuntimeShader(TINT_NOISE_AGSL).apply {
            setFloatUniform("tint", tint.r / 255f, tint.g / 255f, tint.b / 255f)
            setFloatUniform("alpha", FluentMaterial.ALPHA)
            setFloatUniform("levels", FluentMaterial.NOISE_LEVELS.toFloat())
        }
        RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "content"),
            RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP),
        )
    }
}
