package com.agent.shared.settings.model

import kotlinx.serialization.Serializable

/**
 * 一套 provider 凭据与其可用模型列表。
 */
@Serializable
data class ProviderProfile(
    val id: String,
    val label: String? = null,
    val providerType: ProviderType,
    val baseUrl: String,
    val apiKey: String,
    val models: List<ModelProfile> = emptyList(),
    val defaultModel: String? = null,
    val fasterModel: FasterModelProfile? = null,
    /** Provider 级的通用请求覆盖，模型级配置可进一步覆盖它。 */
    val request: RequestOverrides = RequestOverrides(),
    val enabled: Boolean? = null,
) {
    /**
     * 配置未显式关闭时默认启用。
     */
    fun isEnabled(): Boolean = enabled ?: true
}
