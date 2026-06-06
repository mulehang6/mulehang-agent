package com.agent.shared.agent

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import com.agent.shared.exceptions.IllegalConfigExceptions



/**
 * 按配置创建 Koog prompt executor。
 */
internal fun buildPromptExecutor(config: ConfigProfile): MultiLLMPromptExecutor {
    when (config.providerType) {
        // TODO 区分responses和chat/completions
        ProviderType.OPENAI_CHAT_COMPLETIONS,
        ProviderType.OPENAI_RESPONSES
            -> {
            val openAILLMClient = OpenAILLMClient(config.apiKey)
            return MultiLLMPromptExecutor(openAILLMClient)
        }

        ProviderType.ANTHROPIC -> {
            val anthropicLLMClient = AnthropicLLMClient(config.apiKey)
            return MultiLLMPromptExecutor(anthropicLLMClient)
        }

        else -> {
            // TODO 目前版本无GoogleLLMClient
            /*val googleLLMClient = GoogleLLMClient(config.apiKey)
            return MultiLLMPromptExecutor(googleLLMClient)*/
            throw IllegalConfigExceptions { "暂不支持此类型，不关我的事，Koog没这个类型 🤔" }
        }
    }
}