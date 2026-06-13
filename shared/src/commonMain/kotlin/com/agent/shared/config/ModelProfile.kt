package com.agent.shared.config

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
) {
    /**
     * 配置未显式关闭时默认启用。
     */
    fun isEnabled(): Boolean = enabled ?: true
}
