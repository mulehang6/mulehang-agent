package com.agent.shared.settings.model

import com.agent.shared.agent.api.ReasoningEffort
import kotlinx.serialization.json.JsonObject

/**
 * 合并用户级、项目级与环境变量覆盖后的最终 profile。
 */
data class ConfigProfile(
    val id: String,
    val providerId: String = id.substringBefore(':', id),
    val providerLabel: String = providerId,
    val modelLabel: String? = null,
    val providerType: ProviderType,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean,
    val layer: ConfigLayer,
    val limit: ModelLimit? = null,
    val reasoningEfforts: List<ReasoningEffort>? = null,
    val defaultReasoningEffort: ReasoningEffort? = null,
    /** 模型的图片输入能力；null 表示由运行时的内建模型规则保守判断。 */
    val supportsVision: Boolean? = null,
    /** 运行时已合并的扩展请求头。 */
    val requestHeaders: Map<String, String> = emptyMap(),
    /** 运行时已合并的静态请求体覆盖。 */
    val requestBody: JsonObject = JsonObject(emptyMap()),
    /** 按思考档位生效且优先级最高的请求体覆盖。 */
    val reasoningBodyByEffort: Map<String, JsonObject> = emptyMap(),
    /** 已解析为 Koog 正整数的全局循环上限。 */
    val maxIterations: Int = AgentIterationLimit.DEFAULT,
)
