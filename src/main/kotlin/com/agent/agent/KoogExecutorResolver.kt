package com.agent.agent

import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.agent.provider.ProviderBinding
import com.agent.provider.ProviderType

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
    fun resolve(binding: ProviderBinding): ResolvedKoogModelBinding = when (binding.providerType) {
        ProviderType.OPENAI_COMPATIBLE -> ResolvedKoogModelBinding(
            binding = binding,
            promptExecutor = OpenAiCompatibleExecutorFactory.create(binding),
            llmModel = createModel(provider = LLMProvider.OpenAI, modelId = binding.modelId),
        )

        ProviderType.ANTHROPIC_COMPATIBLE -> {
            require(binding.baseUrl == DEFAULT_ANTHROPIC_BASE_URL) {
                "Koog 0.8.0 does not expose a verified Anthropic-compatible custom baseUrl path."
            }
            ResolvedKoogModelBinding(
                binding = binding,
                promptExecutor = simpleAnthropicExecutor(binding.apiKey),
                llmModel = createModel(provider = LLMProvider.Anthropic, modelId = binding.modelId),
            )
        }

        ProviderType.GEMINI_COMPATIBLE -> {
            require(binding.baseUrl == DEFAULT_GEMINI_BASE_URL) {
                "Koog 0.8.0 does not expose a verified Gemini-compatible custom baseUrl path."
            }
            ResolvedKoogModelBinding(
                binding = binding,
                promptExecutor = simpleGoogleAIExecutor(binding.apiKey),
                llmModel = createModel(provider = LLMProvider.Google, modelId = binding.modelId),
            )
        }
    }

    /**
     * 为当前阶段创建支持 tool calling 的最小模型定义。
     */
    private fun createModel(
        provider: LLMProvider,
        modelId: String,
    ): LLModel = LLModel(
        provider = provider,
        id = modelId,
        capabilities = DEFAULT_MODEL_CAPABILITIES,
    )

    private companion object {
        private val DEFAULT_MODEL_CAPABILITIES = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
        )

        private const val DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com"
        private const val DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"
    }
}
