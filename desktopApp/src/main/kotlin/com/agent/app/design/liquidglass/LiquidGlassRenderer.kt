package com.agent.app.design.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * Optical equations and parameter naming are adapted from Sam Asante's
 * liquid-glass reference project, Copyright (c) 2026 Sam Asante, MIT License.
 */

/** 编译和绘制独立的 Liquid Glass SkSL 材质。 */
internal object LiquidGlassRenderer {
    private val logger = Logger.getLogger(LiquidGlassRenderer::class.java.name)

    private val runtimeEffect: RuntimeEffect by lazy {
        try {
            RuntimeEffect.makeForShader(LIQUID_GLASS_SKSL)
        } catch (error: Throwable) {
            logger.log(Level.SEVERE, "Liquid Glass SkSL 初始化失败，无法创建设置页玻璃材质。", error)
            throw IllegalStateException(
                "Liquid Glass SkSL 编译失败；设置页不能以近似材质继续渲染。",
                error,
            )
        }
    }

    /** 强制首次编译 SkSL；失败时保留上下文并向上抛出。 */
    fun requireCompiled() {
        runtimeEffect
    }

    /** 在指定 Skia 画布上绘制采样背景的圆角液态玻璃。 */
    fun draw(
        canvas: org.jetbrains.skia.Canvas,
        backdrop: Bitmap,
        size: Size,
        sourceOffset: Offset,
        radiusPx: Float,
        optics: LiquidGlassOptics,
        tint: Color,
        alpha: Float = 1f,
    ) {
        if (size.width <= 0f || size.height <= 0f || alpha <= 0f) return
        val image = Image.makeFromBitmap(backdrop)
        val backdropShader = image.makeShader()
        val builder = RuntimeShaderBuilder(runtimeEffect)
        val shader = try {
            builder.child("backdrop", backdropShader)
            builder.uniform("size", size.width, size.height)
            builder.uniform("sourceOffset", sourceOffset.x, sourceOffset.y)
            builder.uniform("radius", radiusPx)
            builder.uniform("strength", optics.strength)
            builder.uniform("depth", optics.depth)
            builder.uniform("curvature", optics.curvature)
            builder.uniform("bend", optics.bend)
            builder.uniform("bendWidth", optics.bendWidth)
            builder.uniform("dispersion", optics.dispersion)
            builder.uniform("frost", optics.frost)
            builder.uniform("saturation", optics.saturation)
            builder.uniform("sheen", optics.sheen)
            builder.uniform("sheenWidth", optics.sheenWidth)
            builder.uniform("sheenFalloff", optics.sheenFalloff)
            builder.uniform("glow", optics.glow)
            builder.uniform("glowSpread", optics.glowSpread)
            builder.uniform("glowFalloff", optics.glowFalloff)
            builder.uniform("specular", optics.specular)
            builder.uniform("sheenAngle", optics.sheenAngleDegrees)
            builder.uniform("brightness", optics.brightness)
            builder.uniform("tint", tint.red, tint.green, tint.blue, tint.alpha * alpha)
            builder.makeShader()
        } catch (error: Throwable) {
            backdropShader.close()
            image.close()
            builder.close()
            throw IllegalStateException("Liquid Glass shader uniform 装配失败。", error)
        }
        val paint = Paint().apply { this.shader = shader }
        try {
            canvas.drawRRect(
                RRect.makeXYWH(0f, 0f, size.width, size.height, radiusPx),
                paint,
            )
        } finally {
            paint.close()
            shader.close()
            builder.close()
            backdropShader.close()
            image.close()
        }
    }
}

/** 返回 CPU 测试使用的圆角矩形 SDF。 */
internal fun liquidGlassRoundedRectDistance(
    point: Offset,
    size: Size,
    radius: Float,
): Float {
    val centeredX = abs(point.x - size.width / 2f) - (size.width / 2f - radius)
    val centeredY = abs(point.y - size.height / 2f) - (size.height / 2f - radius)
    val outside = hypot(max(centeredX, 0f), max(centeredY, 0f))
    return outside + min(max(centeredX, centeredY), 0f) - radius
}

/** 返回与 SkSL 同构的弯边位移，中心接近零、边缘达到最大。 */
internal fun liquidGlassDisplacement(
    point: Offset,
    size: Size,
    radius: Float,
    optics: LiquidGlassOptics,
): Offset {
    val center = Offset(size.width / 2f, size.height / 2f)
    val vector = point - center
    val length = hypot(vector.x, vector.y).coerceAtLeast(0.0001f)
    val normal = Offset(vector.x / length, vector.y / length)
    val distance = abs(liquidGlassRoundedRectDistance(point, size, radius))
    val width = max(1f, min(size.width, size.height) * optics.bendWidth)
    val edge = (1f - distance / width).coerceIn(0f, 1f)
    val meniscus = edge.pow(1f + optics.curvature * 3f) * optics.bend
    val magnitude = min(size.width, size.height) * optics.strength * optics.depth * meniscus
    return normal * magnitude
}

/** 返回红、绿、蓝三通道相对主位移的色散偏移。 */
internal fun liquidGlassDispersionOffsets(
    displacement: Offset,
    dispersion: Float,
): Triple<Offset, Offset, Offset> {
    val chroma = displacement * (dispersion * 0.12f)
    return Triple(displacement + chroma, displacement, displacement - chroma)
}

/** 返回指定法线对方向性高光的响应，供高光方向回归测试。 */
internal fun liquidGlassDirectionalHighlight(
    normal: Offset,
    angleDegrees: Float,
    falloff: Float,
): Float {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val light = Offset(cos(radians).toFloat(), sin(radians).toFloat())
    return max(0f, normal.x * -light.x + normal.y * -light.y).pow(max(0.1f, falloff))
}

/** Liquid Glass 的自包含 SkSL，不引用现有 Air 玻璃效果。 */
private const val LIQUID_GLASS_SKSL = """
uniform shader backdrop;
uniform float2 size;
uniform float2 sourceOffset;
uniform float radius;
uniform float strength;
uniform float depth;
uniform float curvature;
uniform float bend;
uniform float bendWidth;
uniform float dispersion;
uniform float frost;
uniform float saturation;
uniform float sheen;
uniform float sheenWidth;
uniform float sheenFalloff;
uniform float glow;
uniform float glowSpread;
uniform float glowFalloff;
uniform float specular;
uniform float sheenAngle;
uniform float brightness;
uniform half4 tint;

float roundedRectSdf(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 saturated(half4 color, float amount) {
    half luminance = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
    return half4(mix(half3(luminance), color.rgb, amount), color.a);
}

half4 main(float2 p) {
    float2 halfSize = size * 0.5;
    float2 centered = p - halfSize;
    float distance = roundedRectSdf(centered, halfSize, radius);
    float mask = 1.0 - smoothstep(-0.5, 1.5, distance);
    float2 normal = centered / max(length(centered), 0.0001);
    float edgeWidth = max(1.0, min(size.x, size.y) * bendWidth);
    float edge = clamp(1.0 - abs(distance) / edgeWidth, 0.0, 1.0);
    float meniscus = pow(edge, 1.0 + curvature * 3.0) * bend;
    float2 displacement = normal * min(size.x, size.y) * strength * depth * meniscus;
    float2 chroma = displacement * dispersion * 0.12;
    float2 samplePoint = sourceOffset + p + displacement;

    half4 centerSample = backdrop.eval(samplePoint);
    half4 blurX = backdrop.eval(samplePoint + float2(frost, 0.0));
    half4 blurY = backdrop.eval(samplePoint + float2(0.0, frost));
    half4 blurNX = backdrop.eval(samplePoint - float2(frost, 0.0));
    half4 blurNY = backdrop.eval(samplePoint - float2(0.0, frost));
    half4 frosted = (centerSample * 4.0 + blurX + blurY + blurNX + blurNY) / 8.0;

    half red = backdrop.eval(samplePoint + chroma).r;
    half green = frosted.g;
    half blue = backdrop.eval(samplePoint - chroma).b;
    half4 color = saturated(half4(red, green, blue, frosted.a), saturation);

    float angle = radians(sheenAngle);
    float2 light = float2(cos(angle), sin(angle));
    float directional = pow(max(dot(normal, -light), 0.0), max(0.1, sheenFalloff));
    float highlightBand = pow(edge, max(0.2, sheenWidth));
    float innerGlow = pow(edge, max(0.2, glowFalloff)) * glow * glowSpread;
    float highlight = directional * highlightBand * sheen * specular + innerGlow;

    color.rgb = color.rgb + half3(highlight + brightness * 0.08);
    color.rgb = mix(color.rgb, tint.rgb, tint.a * 0.18);
    color.a = half(mask);
    return color;
}
"""
