package mulehang.provider

object ProviderRegistry {
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
