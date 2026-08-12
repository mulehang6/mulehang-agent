package com.agent.shared.settings.model

import kotlinx.serialization.Serializable

/**
 * AUTO 审批专用的快速模型连接配置。
 *
 * 它独立于主会话模型，避免让执行模型自行审批自身的危险操作。
 */
@Serializable
data class FasterModelProfile(
    val providerType: ProviderType,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean? = null,
    val limit: ModelLimit? = null,
) {
    /** 未显式关闭的快速模型视为启用。 */
    fun isEnabled(): Boolean = enabled ?: true

    /** AUTO 审批只有在连接配置完整时才允许调用模型。 */
    fun isComplete(): Boolean = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}
