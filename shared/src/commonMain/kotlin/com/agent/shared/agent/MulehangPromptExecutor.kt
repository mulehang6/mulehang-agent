package com.agent.shared.agent

import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.models.ReasoningConfig
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import com.agent.shared.exceptions.IllegalConfigExceptions
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort as KoogReasoningEffort

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
        if (config.isDeepSeekChatCompletionsProfile()) {
            add(LLMCapability.Thinking)
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
    ProviderType.OPENAI_CHAT_COMPLETIONS -> OpenAIChatParams(
        reasoningEffort = reasoningEffort?.toKoogReasoningEffort(),
    )

    ProviderType.OPENAI_RESPONSES -> OpenAIResponsesParams(
        reasoning = reasoningEffort?.toKoogReasoningEffort()?.let { effort ->
            ReasoningConfig(effort = effort)
        },
    )

    ProviderType.ANTHROPIC -> LLMParams()
    else -> throw IllegalConfigExceptions { "暂不支持的 providerType: ${config.providerType}" }
}

/**
 * 创建 OpenAI-compatible client settings，并兼容带 /v1 的 baseUrl。
 */
internal fun buildOpenAIClientSettings(config: ConfigProfile): OpenAIClientSettings {
    val baseUrl = config.baseUrl.trimEnd('/')
    val hasVersionPath = baseUrl.substringAfterLast('/').matches(Regex("v\\d+"))
    val prefix = if (hasVersionPath) "" else "v1/"
    return OpenAIClientSettings(
        baseUrl = baseUrl,
        chatCompletionsPath = "${prefix}chat/completions",
        responsesAPIPath = "${prefix}responses",
        embeddingsPath = "${prefix}embeddings",
        moderationsPath = "${prefix}moderations",
        modelsPath = "${prefix}models",
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
 * 判断当前 profile 是否走 DeepSeek 的 OpenAI chat-completions 兼容接口。
 */
internal fun ConfigProfile.isDeepSeekChatCompletionsProfile(): Boolean =
    providerType == ProviderType.OPENAI_CHAT_COMPLETIONS &&
        (baseUrl.contains("deepseek.com", ignoreCase = true) || model.startsWith("deepseek", ignoreCase = true))

/**
 * 将项目侧推理强度映射到 Koog 支持的 OpenAI 推理强度。
 *
 * Koog 当前最高只支持 `high`，因此 `MAX` 需要回退到最高兼容值。
 */
private fun ReasoningEffort.toKoogReasoningEffort(): KoogReasoningEffort = when (this) {
    ReasoningEffort.LOW -> KoogReasoningEffort.LOW
    ReasoningEffort.MEDIUM -> KoogReasoningEffort.MEDIUM
    ReasoningEffort.HIGH -> KoogReasoningEffort.HIGH
    ReasoningEffort.MAX -> KoogReasoningEffort.HIGH
}
