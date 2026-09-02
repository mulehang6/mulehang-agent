package com.agent.shared.settings.model

import kotlinx.serialization.Serializable

/**
 * provider 下的单个模型配置。
 */
@Serializable
data class ModelProfile(
    val id: String,
    val label: String? = null,
    val enabled: Boolean? = null,
    val limit: ModelLimit? = null,
    val reasoningEfforts: List<String>? = null,
    val defaultReasoningEffort: String? = null,
    /** 显式声明模型能否接收图片；未声明时由内建已知模型规则保守推断。 */
    val supportsVision: Boolean? = null,
) {
    /**
     * 配置未显式关闭时默认启用。
     */
    fun isEnabled(): Boolean = enabled ?: true
}
