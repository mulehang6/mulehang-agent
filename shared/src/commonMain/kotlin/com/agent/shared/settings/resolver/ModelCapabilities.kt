package com.agent.shared.settings.resolver

import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.ModelLimit

/** 单个模型变体，表示 UI 可选项和请求使用的 reasoning 档位。 */
data class ModelVariant(val id: String, val reasoningEffort: ReasoningEffort?)

/** 描述 UI 和发送链路可消费的模型能力。 */
data class ModelCapabilities(
    val variants: Map<String, ModelVariant>,
    val limit: ModelLimit? = null,
    private val configuredDefaultReasoningEffort: ReasoningEffort? = null,
) {
    /** 当前模型是否支持可选 reasoning 档位。 */
    val supportsReasoning: Boolean = variants.isNotEmpty()

    /** UI 可展示的 reasoning 档位，按配置或 provider 声明的顺序返回。 */
    val reasoningEfforts: List<ReasoningEffort> = variants.values.mapNotNull { it.reasoningEffort }

    /**
     * 当前模型的 reasoning 默认档位。
     *
     * 显式配置优先；未配置时按通用规则选择 `medium`，若模型不支持该档位则选择
     * `reasoningEfforts` 中的第一个。没有 reasoning 能力时保持为空。
     */
    val defaultReasoningEffort: ReasoningEffort? =
        configuredDefaultReasoningEffort ?: ReasoningEffort.MEDIUM.takeIf { it in reasoningEfforts }
            ?: reasoningEfforts.firstOrNull()
}

/** 从 reasoning 档位构建 UI 与发送层共用的能力结果。 */
internal fun modelCapabilitiesOf(
    efforts: List<ReasoningEffort>,
    limit: ModelLimit? = null,
    defaultReasoningEffort: ReasoningEffort? = null,
): ModelCapabilities = ModelCapabilities(
    variants = efforts.associate { effort ->
        effort.wireValue to ModelVariant(id = effort.wireValue, reasoningEffort = effort)
    },
    limit = limit,
    configuredDefaultReasoningEffort = defaultReasoningEffort,
)

/** 没有可识别能力的模型的共享结果。 */
internal val noModelCapabilities = ModelCapabilities(variants = emptyMap())
