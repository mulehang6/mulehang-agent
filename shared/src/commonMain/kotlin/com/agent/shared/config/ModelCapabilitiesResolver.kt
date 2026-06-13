package com.agent.shared.config

import com.agent.shared.agent.ReasoningEffort

/**
 * 单个模型变体，表示 UI 可选项以及发送链路需要写入的 provider 参数。
 */
data class ModelVariant(
    val id: String,
    val reasoningEffort: ReasoningEffort?,
)

/**
 * 描述 UI 和发送链路可消费的模型能力。
 */
data class ModelCapabilities(
    val variants: Map<String, ModelVariant>,
) {
    /**
     * 当前模型是否有可选 thinking/reasoning 变体。
     */
    val supportsReasoning: Boolean = variants.isNotEmpty()

    /**
     * UI 可展示的 reasoning effort 档位，顺序从弱到强。
     */
    val reasoningEfforts: List<ReasoningEffort> = variants.values.mapNotNull { it.reasoningEffort }

    /**
     * 当前产品默认使用的 reasoning effort。
     */
    val defaultReasoningEffort: ReasoningEffort? =
        ReasoningEffort.MEDIUM.takeIf { it in reasoningEfforts } ?: reasoningEfforts.firstOrNull()
}

/**
 * 模型能力解析结果的构造辅助。
 */
private fun capabilitiesOf(efforts: List<ReasoningEffort>): ModelCapabilities =
    ModelCapabilities(
        variants = efforts.associate { effort ->
            effort.wireValue to ModelVariant(
                id = effort.wireValue,
                reasoningEffort = effort,
            )
        },
    )

private val noCapabilities = ModelCapabilities(
    variants = emptyMap(),
)

/**
 * 根据最终 profile 解析模型能力，避免 UI 层直接按模型名散落特判。
 */
object ModelCapabilitiesResolver {
    private val deepSeekReasoningEfforts = listOf(
        ReasoningEffort.HIGH,
        ReasoningEffort.MAX,
    )

    /**
     * 将 provider、baseUrl 和模型名折叠为当前产品支持的能力集合。
     */
    fun resolve(profile: ConfigProfile): ModelCapabilities =
        when (profile.providerType) {
            ProviderType.OPENAI_CHAT_COMPLETIONS -> resolveOpenAICompatible(profile)
            else -> noCapabilities
        }

    private fun resolveOpenAICompatible(profile: ConfigProfile): ModelCapabilities {
        val model = profile.model.lowercase()
        return when {
            profile.isDeepSeekProfile() -> capabilitiesOf(deepSeekReasoningEfforts)
            model.isGptReasoningFamily() -> capabilitiesOf(widelySupportedReasoningEfforts)
            else -> noCapabilities
        }
    }

    private fun ConfigProfile.isDeepSeekProfile(): Boolean =
        model.startsWith("deepseek", ignoreCase = true) ||
            baseUrl.contains("deepseek", ignoreCase = true)

    private fun String.isGptReasoningFamily(): Boolean =
        contains("gpt-5") || contains("codex")

    private val widelySupportedReasoningEfforts = listOf(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
    )
}
