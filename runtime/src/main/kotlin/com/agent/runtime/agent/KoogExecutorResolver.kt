@file:Suppress("UnstableApiUsage")

package com.agent.runtime.agent

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.agent.runtime.provider.OpenAIEndpointMode
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType.*

/**
 * 表示 provider binding 解析后的 Koog executor 与模型绑定。
 */
data class ResolvedKoogModelBinding(
    val binding: ProviderBinding,
    val promptExecutor: PromptExecutor,
    val llmModel: LLModel,
)

/**
 * 负责把仓库内 ProviderBinding 解析为 Koog 可执行绑定。
 */
class KoogExecutorResolver {

    /**
     * 根据 provider 类型解析 Koog executor 与模型对象。
     */
    fun resolve(binding: ProviderBinding): ResolvedKoogModelBinding {
        validateBinding(binding)
        return when (binding.providerType) {
            OPENAI -> ResolvedKoogModelBinding(
                binding = binding,
                promptExecutor = ProviderCompatiblePromptExecutorFactory.createOpenAICompatible(binding),
                llmModel = createOpenAiCompatibleModel(binding),
            )

            OPENAI_COMPATIBLE -> ResolvedKoogModelBinding(
                binding = binding,
                promptExecutor = ProviderCompatiblePromptExecutorFactory.createOpenAICompatible(binding),
                llmModel = createOpenAiCompatibleModel(binding),
            )

            ANTHROPIC_COMPATIBLE -> ResolvedKoogModelBinding(
                binding = binding,
                promptExecutor = ProviderCompatiblePromptExecutorFactory.createAnthropicCompatible(binding),
                llmModel = createModel(
                    modelId = binding.modelId,
                    provider = LLMProvider.Anthropic,
                ),
            )

            GEMINI_COMPATIBLE -> ResolvedKoogModelBinding(
                binding = binding,
                promptExecutor = ProviderCompatiblePromptExecutorFactory.createGoogleCompatible(binding),
                llmModel = createModel(
                    modelId = binding.modelId,
                    provider = LLMProvider.Google,
                )
            )
        }
    }

    /**
     * 校验 Koog executor/model 构造所需的最小 binding 字段。
     */
    private fun validateBinding(binding: ProviderBinding) {
        require(binding.baseUrl.isNotBlank()) {
            "Provider '${binding.providerId}' baseUrl must not be blank."
        }
        require(binding.apiKey.isNotBlank()) {
            "Provider '${binding.providerId}' apiKey must not be blank."
        }
        require(binding.modelId.isNotBlank()) {
            "Provider '${binding.providerId}' modelId must not be blank."
        }
    }

    /**
     * 为当前阶段创建支持 tool calling 的最小模型定义。
     */
    private fun createModel(
        provider: LLMProvider,
        modelId: String,
        capabilities: List<LLMCapability> = DEFAULT_MODEL_CAPABILITIES,
    ): LLModel = LLModel(
        provider = provider,
        id = modelId,
        capabilities = capabilities,
    )

    /**
     * 为 OpenAI-compatible 协议模型补充明确 endpoint 能力，避免 Koog 按原生 OpenAI 模型表推断失败。
     */
    private fun createOpenAiCompatibleModel(binding: ProviderBinding): LLModel = createModel(
        provider = LLMProvider.OpenAI,
        modelId = binding.modelId,
        capabilities = DEFAULT_MODEL_CAPABILITIES + binding.openAIEndpointCapability(),
    )

    /**
     * 根据 binding 的 OpenAI endpoint 选项决定 Koog 应调用 Responses 还是 chat/completions。
     */
    private fun ProviderBinding.openAIEndpointCapability(): LLMCapability.OpenAIEndpoint =
        when (options.openAIEndpointMode ?: OpenAIEndpointMode.RESPONSES) {
            OpenAIEndpointMode.RESPONSES -> LLMCapability.OpenAIEndpoint.Responses
            OpenAIEndpointMode.CHAT_COMPLETIONS -> LLMCapability.OpenAIEndpoint.Completions
        }

    private companion object {
        private val DEFAULT_MODEL_CAPABILITIES = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
        )
    }
}
