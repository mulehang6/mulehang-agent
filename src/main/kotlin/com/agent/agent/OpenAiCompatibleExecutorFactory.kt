package com.agent.agent

import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import com.agent.provider.ProviderBinding

/**
 * 负责创建支持自定义 endpoint 的 OpenAI-compatible executor。
 */
object OpenAiCompatibleExecutorFactory {

    /**
     * 根据 binding 创建 OpenAI-compatible prompt executor。
     */
    fun create(binding: ProviderBinding): PromptExecutor {
        val client = OpenAILLMClient(
            apiKey = binding.apiKey,
            settings = OpenAIClientSettings(baseUrl = binding.baseUrl),
        )
        return MultiLLMPromptExecutor(client)
    }
}
