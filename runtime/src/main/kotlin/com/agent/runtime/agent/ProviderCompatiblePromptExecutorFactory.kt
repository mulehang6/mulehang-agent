package com.agent.runtime.agent

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import com.agent.runtime.provider.ProviderBinding

/**
 * 负责创建支持自定义 endpoint 的 OpenAI-compatible executor。
 */
object ProviderCompatiblePromptExecutorFactory {

    /**
     * 根据 binding 创建 OpenAI-compatible prompt executor。
     */
    fun createOpenAICompatible(binding: ProviderBinding): PromptExecutor {
        val client = OpenAILLMClient(
            apiKey = binding.apiKey,
            settings = OpenAIClientSettings(baseUrl = binding.baseUrl),
        )
        return MultiLLMPromptExecutor(client)
    }

    /**
     * 根据 binding 创建 Anthropic-compatible prompt executor。
     */
    fun createAnthropicCompatible(binding: ProviderBinding): PromptExecutor {
        val client = AnthropicLLMClient(
            apiKey = binding.apiKey,
            settings = AnthropicClientSettings(baseUrl = binding.baseUrl),
        )
        return MultiLLMPromptExecutor(client)
    }

    fun createGoogleCompatible(binding: ProviderBinding): PromptExecutor {
        val client = GoogleLLMClient(
            apiKey = binding.apiKey,
            settings = GoogleClientSettings(baseUrl = binding.baseUrl),
        )

        return MultiLLMPromptExecutor(client)
    }
}
