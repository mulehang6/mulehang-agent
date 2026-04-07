package mulehang.provider

/**
 * 维护当前仓库内置支持的 provider 及模型目录。
 */
object ProviderRegistry {
    /**
     * 按 providerId 索引当前仓库内置的 provider 定义。
     */
    val providers = listOf(
        ProviderSpec(
            id = "openrouter",
            displayName = "OpenRouter",
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            models = mapOf(
                "openrouter.gpt4o" to ModelSpec(
                    providerId = "openrouter",
                    modelId = "openrouter.gpt4o",
                    displayName = "GPT-4o via OpenRouter",
                    supportsTools = true,
                    supportsStreaming = true,
                    supportsReasoning = true
                ),
                "openrouter.claude-sonnet-4-5" to ModelSpec(
                    providerId = "openrouter",
                    modelId = "openrouter.claude-sonnet-4-5",
                    displayName = "Claude Sonnet 4.5 via OpenRouter",
                    supportsTools = true,
                    supportsStreaming = true,
                    supportsReasoning = true
                )
            )
        ),
        ProviderSpec(
            id = "openai",
            displayName = "OpenAI",
            defaultBaseUrl = "https://api.openai.com/v1",
            models = mapOf(
                "openai.chat.gpt4_1" to ModelSpec(
                    providerId = "openai",
                    modelId = "openai.chat.gpt4_1",
                    displayName = "GPT-4.1",
                    supportsTools = true,
                    supportsStreaming = true,
                    supportsReasoning = true
                )
            )
        ),
        ProviderSpec(
            id = "ollama",
            displayName = "Ollama",
            defaultBaseUrl = "http://localhost:11434",
            models = mapOf(
                "ollama.meta.llama3.2" to ModelSpec(
                    providerId = "ollama",
                    modelId = "ollama.meta.llama3.2",
                    displayName = "Llama 3.2",
                    supportsTools = true,
                    supportsStreaming = true,
                    supportsReasoning = false
                )
            )
        )
    ).associateBy { it.id }
}
