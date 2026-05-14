package com.agent.runtime.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import kotlinx.coroutines.flow.Flow

/**
 * 负责创建支持自定义 endpoint 的 OpenAI-compatible executor。
 */
object ProviderCompatiblePromptExecutorFactory {

    /**
     * 根据 binding 创建 OpenAI-compatible prompt executor。
     */
    fun createOpenAICompatible(binding: ProviderBinding): PromptExecutor {
        val client = if (binding.providerType == ProviderType.OPENAI_COMPATIBLE &&
            binding.enableThinking &&
            binding.usesDeepSeekThinkingMode()
        ) {
            DeepSeekInterleavedOpenAILLMClient(
                apiKey = binding.apiKey,
                clientSettings = OpenAIClientSettings(baseUrl = binding.baseUrl),
            )
        } else {
            OpenAILLMClient(
                apiKey = binding.apiKey,
                settings = OpenAIClientSettings(baseUrl = binding.baseUrl),
            )
        }
        return applyBindingDefaults(
            executor = MultiLLMPromptExecutor(client),
            binding = binding,
        )
    }

    /**
     * 根据 binding 创建 Anthropic-compatible prompt executor。
     */
    fun createAnthropicCompatible(binding: ProviderBinding): PromptExecutor {
        val client = AnthropicLLMClient(
            apiKey = binding.apiKey,
            settings = AnthropicClientSettings(baseUrl = binding.baseUrl),
        )
        return applyBindingDefaults(
            executor = MultiLLMPromptExecutor(client),
            binding = binding,
        )
    }

    /**
     * 根据 binding 创建 Gemini-compatible prompt executor。
     */
    fun createGoogleCompatible(binding: ProviderBinding): PromptExecutor {
        val client = GoogleLLMClient(
            apiKey = binding.apiKey,
            settings = GoogleClientSettings(baseUrl = binding.baseUrl),
        )

        return applyBindingDefaults(
            executor = MultiLLMPromptExecutor(client),
            binding = binding,
        )
    }

    /**
     * 给默认仍为空参数的 prompt 注入 binding 级默认参数，避免 tool-enabled 路径丢失 thinking 配置。
     */
    internal fun applyBindingDefaults(
        executor: PromptExecutor,
        binding: ProviderBinding,
    ): PromptExecutor {
        val defaultParams = binding.toThinkingParams() ?: return executor
        return BindingDefaultsPromptExecutor(
            delegate = executor,
            defaultParams = defaultParams,
        )
    }
}

/**
 * 只在 prompt 尚未显式声明参数时补充 provider 默认参数，避免覆盖上层手动设置。
 */
internal class BindingDefaultsPromptExecutor(
    private val delegate: PromptExecutor,
    private val defaultParams: LLMParams,
) : PromptExecutor() {

    /**
     * 为普通请求补充 binding 默认参数。
     */
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ) = delegate.execute(
        prompt = prompt.withBindingDefaults(),
        model = model,
        tools = tools,
    )

    /**
     * 为 streaming 请求补充 binding 默认参数。
     */
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = delegate.executeStreaming(
        prompt = prompt.withBindingDefaults(),
        model = model,
        tools = tools,
    )

    /**
     * moderation 也沿用同一套默认参数，保持 provider 行为一致。
     */
    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = delegate.moderate(
        prompt = prompt.withBindingDefaults(),
        model = model,
    )

    /**
     * 透传底层 executor 关闭行为。
     */
    override fun close() {
        delegate.close()
    }

    /**
     * 仅在参数为空时注入默认 binding 参数，避免覆盖显式配置。
     */
    private fun Prompt.withBindingDefaults(): Prompt =
        if (params == LLMParams()) {
            withParams(defaultParams)
        } else {
            this
        }
}
