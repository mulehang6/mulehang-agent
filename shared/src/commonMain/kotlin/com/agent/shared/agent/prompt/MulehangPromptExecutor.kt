package com.agent.shared.agent.prompt

import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.IllegalConfigExceptions
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.settings.resolver.supportsImageInput
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 根据 profile 创建 Koog 运行时模型，确保请求使用配置文件中的模型 id。
 */
@Suppress("UnstableApiUsage")
internal fun buildLlmModel(config: ConfigProfile): LLModel {
    val endpointCapability = when (config.providerType) {
        ProviderType.OPENAI_CHAT_COMPLETIONS -> LLMCapability.OpenAIEndpoint.Completions
        ProviderType.OPENAI_RESPONSES -> LLMCapability.OpenAIEndpoint.Responses
        ProviderType.ANTHROPIC -> null
        else -> throw IllegalConfigExceptions { "暂不支持的 providerType: ${config.providerType}" }
    }
    val capabilities = buildList {
        add(LLMCapability.Completion)
        add(LLMCapability.Tools)
        add(LLMCapability.ToolChoice)
        add(LLMCapability.Schema.JSON.Basic)
        add(LLMCapability.Schema.JSON.Standard)
        if (!config.reasoningEfforts.isNullOrEmpty()) {
            add(LLMCapability.Thinking)
        }
        if (config.supportsImageInput()) {
            add(LLMCapability.Vision.Image)
        }
        if (endpointCapability != null) {
            add(endpointCapability)
        }
    }

    return LLModel(
        provider = config.toLlmProvider(),
        id = config.model,
        capabilities = capabilities,
    )
}
/**
 * 根据 provider 与推理强度构造 Koog 运行参数。
 */
@Suppress("UnstableApiUsage")
internal fun buildPromptParams(
    config: ConfigProfile,
    reasoningEffort: ReasoningEffort?,
): LLMParams = when (config.providerType) {
    ProviderType.OPENAI_CHAT_COMPLETIONS -> {
        val additionalProperties = config.reasoningAdditionalProperties(reasoningEffort)
        OpenAIChatParams(
            reasoningEffort = null,
            additionalProperties = additionalProperties,
        )
    }

    ProviderType.OPENAI_RESPONSES -> {
        val additionalProperties = config.reasoningAdditionalProperties(reasoningEffort)
        OpenAIResponsesParams(
            reasoning = null,
            additionalProperties = additionalProperties,
        )
    }

    ProviderType.ANTHROPIC -> AnthropicParams(
        additionalProperties = config.reasoningAdditionalProperties(reasoningEffort),
    )
    else -> throw IllegalConfigExceptions { "暂不支持的 providerType: ${config.providerType}" }
}

/**
 * 创建 OpenAI-compatible client settings，并将 baseUrl 视为服务端声明的精确路径前缀。
 *
 * 版本路径由配置承担，避免客户端臆测追加 `/v1` 而破坏使用根路径的兼容服务端。
 */
internal fun buildOpenAIClientSettings(config: ConfigProfile): OpenAIClientSettings {
    val baseUrl = config.baseUrl.trimEnd('/')
    return OpenAIClientSettings(
        baseUrl = baseUrl,
        chatCompletionsPath = "chat/completions",
        responsesAPIPath = "responses",
        embeddingsPath = "embeddings",
        moderationsPath = "moderations",
        modelsPath = "models",
    )
}

/**
 * 将项目 providerType 映射到 Koog provider。
 */
private fun ConfigProfile.toLlmProvider(): LLMProvider = when (providerType) {
    ProviderType.OPENAI_CHAT_COMPLETIONS, ProviderType.OPENAI_RESPONSES -> LLMProvider.OpenAI

    ProviderType.ANTHROPIC -> LLMProvider.Anthropic
    else -> throw IllegalConfigExceptions { "暂不支持的 providerType: $providerType" }
}

/**
 * 按 endpoint 类型生成原始推理请求字段，绕过 Koog 的标准档位枚举限制。
 *
 * `providerType` 已是用户选择的请求契约：Responses 使用统一 `reasoning`，
 * Chat Completions 使用 legacy `reasoning_effort`，直连 Anthropic 使用
 * `output_config.effort`。因此无需在模型配置中重复声明协议。
 */
private fun ConfigProfile.reasoningAdditionalProperties(
    reasoningEffort: ReasoningEffort?,
): Map<String, JsonElement>? {
    val effort = reasoningEffort ?: return null
    return when (providerType) {
        ProviderType.OPENAI_RESPONSES -> mapOf(
            "reasoning" to buildJsonObject {
                put("effort", effort.wireValue)
            },
        )

        ProviderType.OPENAI_CHAT_COMPLETIONS -> mapOf(
            "reasoning_effort" to JsonPrimitive(effort.wireValue),
        )

        ProviderType.ANTHROPIC -> mapOf(
            "output_config" to buildJsonObject {
                put("effort", effort.wireValue)
            },
        )

        else -> null
    }
}
