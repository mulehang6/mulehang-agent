package com.agent.app.chat.presentation

import com.agent.app.chat.state.isStoppable
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.settings.resolver.ModelCapabilitiesResolver
import com.agent.shared.settings.resolver.ModelVariant

/**
 * 返回 provider 类型的展示文案。
 */
internal fun providerLabel(providerType: ProviderType): String = when (providerType) {
    ProviderType.OPENAI_RESPONSES -> "OpenAI"
    ProviderType.OPENAI_CHAT_COMPLETIONS -> "OpenAI Compatible"
    ProviderType.ANTHROPIC -> "Anthropic"
    ProviderType.GOOGLE -> "Google"
}

/**
 * 返回 profile 支持的模型变体。
 */
internal fun modelVariantsFor(profile: ConfigProfile): List<ModelVariant> =
    ModelCapabilitiesResolver.resolve(profile).variants.values.toList()

/**
 * 按 providerId 对 profile 分组。
 */
internal fun groupProfilesByProvider(profiles: List<ConfigProfile>): Map<String, List<ConfigProfile>> =
    profiles.groupBy { it.providerId }

/**
 * 生成上下文圆环 hover 文案。
 */
internal fun buildContextTooltip(usageFraction: Float): String =
    "${formatContextUsagePercent(usageFraction)} used"

/**
 * 生成上下文圆环旁的可见百分比文案。
 */
internal fun buildContextUsageLabel(usageFraction: Float): String =
    formatContextUsagePercent(usageFraction)

/**
 * composer 主按钮的展示状态。
 */
internal data class ComposerPrimaryActionVisual(
    val symbol: String,
    val danger: Boolean,
)

/**
 * 根据执行状态生成 composer 主按钮视觉。
 */
internal fun buildComposerPrimaryActionVisual(executionState: ExecutionState): ComposerPrimaryActionVisual =
    if (executionState.isStoppable()) {
        ComposerPrimaryActionVisual(symbol = "■", danger = true)
    } else {
        ComposerPrimaryActionVisual(symbol = "↑", danger = false)
    }

/**
 * 将上下文占比换算为圆环 sweep angle，并为非零占用保留最小可见弧度。
 */
internal fun contextRingSweepAngle(usageFraction: Float): Float {
    val clampedFraction = usageFraction.coerceIn(0f, 1f)
    if (clampedFraction <= 0f) return 0f
    if (clampedFraction >= 1f) return 360f
    return (clampedFraction * 360f).coerceAtLeast(MIN_VISIBLE_CONTEXT_SWEEP_ANGLE)
}

/**
 * 格式化上下文占用百分比。
 */
private fun formatContextUsagePercent(usageFraction: Float): String {
    val clamped = usageFraction.coerceIn(0f, 1f)
    if (clamped in 0f..0.001f && clamped > 0f) {
        return "<0.1%"
    }
    return "${(clamped * 100).toInt()}%"
}

private const val MIN_VISIBLE_CONTEXT_SWEEP_ANGLE = 6f
