package com.agent.runtime.server

import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import com.agent.runtime.utils.readDotEnv

/**
 * 表示从 runtime 本地配置源解析默认 provider binding 的结果。
 */
data class RuntimeHttpProviderResolution(
    val binding: ProviderBinding?,
    val failureMessage: String,
)

/**
 * 从 `.env` 与系统环境变量读取默认 provider binding，供 HTTP server 在请求未显式带 provider 时复用。
 */
fun resolveDefaultHttpProviderBinding(
    values: Map<String, String> = readDotEnv() + System.getenv(),
): RuntimeHttpProviderResolution {
    val providerId = values[PROVIDER_ID_ENV]?.trim().orEmpty()
    val providerTypeText = values[PROVIDER_TYPE_ENV]?.trim().orEmpty()
    val baseUrl = values[PROVIDER_BASE_URL_ENV]?.trim().orEmpty()
    val apiKey = values[PROVIDER_API_KEY_ENV]?.trim().orEmpty()
    val modelId = values[PROVIDER_MODEL_ID_ENV]?.trim().orEmpty()
    val missingKeys = buildList {
        if (providerId.isBlank()) add(PROVIDER_ID_ENV)
        if (providerTypeText.isBlank()) add(PROVIDER_TYPE_ENV)
        if (baseUrl.isBlank()) add(PROVIDER_BASE_URL_ENV)
        if (apiKey.isBlank()) add(PROVIDER_API_KEY_ENV)
        if (modelId.isBlank()) add(PROVIDER_MODEL_ID_ENV)
    }

    if (missingKeys.isNotEmpty()) {
        return RuntimeHttpProviderResolution(
            binding = null,
            failureMessage = "Missing runtime provider configuration for HTTP request. Missing keys: ${missingKeys.joinToString()}",
        )
    }

    val providerType = runCatching { ProviderType.valueOf(providerTypeText) }.getOrNull()
    if (providerType == null) {
        return RuntimeHttpProviderResolution(
            binding = null,
            failureMessage = "Invalid runtime provider type for HTTP request: $providerTypeText",
        )
    }

    return RuntimeHttpProviderResolution(
        binding = ProviderBinding(
            providerId = providerId,
            providerType = providerType,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
            enableThinking = values[PROVIDER_ENABLE_THINKING_ENV]?.trim()?.lowercase() == "true",
        ),
        failureMessage = "",
    )
}

private const val PROVIDER_ID_ENV = "MULEHANG_PROVIDER_ID"
private const val PROVIDER_TYPE_ENV = "MULEHANG_PROVIDER_TYPE"
private const val PROVIDER_BASE_URL_ENV = "MULEHANG_PROVIDER_BASE_URL"
private const val PROVIDER_API_KEY_ENV = "MULEHANG_PROVIDER_API_KEY"
private const val PROVIDER_MODEL_ID_ENV = "MULEHANG_PROVIDER_MODEL_ID"
private const val PROVIDER_ENABLE_THINKING_ENV = "MULEHANG_PROVIDER_ENABLE_THINKING"
