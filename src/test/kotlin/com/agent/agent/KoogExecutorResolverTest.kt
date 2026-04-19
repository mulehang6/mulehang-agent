package com.agent.agent

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLMCapability
import com.agent.provider.ProviderBinding
import com.agent.provider.ProviderType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 验证 provider binding 到 Koog executor 与模型的解析路径。
 */
class KoogExecutorResolverTest {

    @Test
    fun `should resolve openai compatible binding with custom base url and arbitrary model id`() {
        val binding = ProviderBinding(
            providerId = "provider-openai",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.example.com/v1",
            apiKey = "test-key",
            modelId = "openai/gpt-4.1-mini",
        )

        val resolved = KoogExecutorResolver().resolve(binding)

        assertEquals(binding, resolved.binding)
        assertEquals(
            "ai.koog.prompt.executor.llms.SingleLLMPromptExecutor",
            resolved.promptExecutor::class.qualifiedName,
        )
        assertEquals(LLMProvider.OpenAI, resolved.llmModel.provider)
        assertEquals("openai/gpt-4.1-mini", resolved.llmModel.id)
        assertEquals(true, resolved.llmModel.supports(LLMCapability.OpenAIEndpoint.Completions))
    }

    @Test
    fun `should preserve arbitrary openai compatible routed model ids with chat completions capability`() {
        val binding = ProviderBinding(
            providerId = "provider-compatible",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "test-key",
            modelId = "google/gemma-4-26b-4b-it:free",
        )

        val resolved = KoogExecutorResolver().resolve(binding)

        assertEquals(binding, resolved.binding)
        assertEquals(LLMProvider.OpenAI, resolved.llmModel.provider)
        assertEquals("google/gemma-4-26b-4b-it:free", resolved.llmModel.id)
        assertEquals(true, resolved.llmModel.supports(LLMCapability.OpenAIEndpoint.Completions))
    }

    @Test
    fun `should fail openai compatible binding when model id is blank`() {
        val binding = ProviderBinding(
            providerId = "provider-openai",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.example.com/v1",
            apiKey = "test-key",
            modelId = " ",
        )

        val error = assertFailsWith<IllegalArgumentException> {
            KoogExecutorResolver().resolve(binding)
        }

        assertContains(error.message.orEmpty(), "modelId")
    }

    @Test
    fun `should resolve anthropic and gemini bindings through koog executors`() {
        val resolver = KoogExecutorResolver()

        val anthropic = resolver.resolve(
            ProviderBinding(
                providerId = "provider-anthropic",
                providerType = ProviderType.ANTHROPIC_COMPATIBLE,
                baseUrl = "https://api.anthropic.com",
                apiKey = "anthropic-key",
                modelId = "claude-sonnet-4-5",
            ),
        )
        val gemini = resolver.resolve(
            ProviderBinding(
                providerId = "provider-gemini",
                providerType = ProviderType.GEMINI_COMPATIBLE,
                baseUrl = "https://generativelanguage.googleapis.com",
                apiKey = "gemini-key",
                modelId = "gemini-2.5-flash",
            ),
        )

        assertEquals(
            "ai.koog.prompt.executor.llms.SingleLLMPromptExecutor",
            anthropic.promptExecutor::class.qualifiedName,
        )
        assertEquals(LLMProvider.Anthropic, anthropic.llmModel.provider)
        assertEquals("claude-sonnet-4-5", anthropic.llmModel.id)

        assertEquals(
            "ai.koog.prompt.executor.llms.SingleLLMPromptExecutor",
            gemini.promptExecutor::class.qualifiedName,
        )
        assertEquals(LLMProvider.Google, gemini.llmModel.provider)
        assertEquals("gemini-2.5-flash", gemini.llmModel.id)
    }

    @Test
    fun `should fail when anthropic custom endpoint is unsupported`() {
        val binding = ProviderBinding(
            providerId = "provider-anthropic",
            providerType = ProviderType.ANTHROPIC_COMPATIBLE,
            baseUrl = "https://custom-anthropic-endpoint.example.com",
            apiKey = "anthropic-key",
            modelId = "claude-sonnet-4-5",
        )

        val error = assertFailsWith<IllegalArgumentException> {
            KoogExecutorResolver().resolve(binding)
        }

        assertContains(error.message.orEmpty(), "Anthropic-compatible")
        assertContains(error.message.orEmpty(), "Koog 0.8.0")
    }

    @Test
    fun `should fail when gemini custom endpoint is unsupported`() {
        val binding = ProviderBinding(
            providerId = "provider-gemini",
            providerType = ProviderType.GEMINI_COMPATIBLE,
            baseUrl = "https://custom-gemini-endpoint.example.com",
            apiKey = "gemini-key",
            modelId = "gemini-2.5-flash",
        )

        val error = assertFailsWith<IllegalArgumentException> {
            KoogExecutorResolver().resolve(binding)
        }

        assertContains(error.message.orEmpty(), "Koog 0.8.0")
    }
}
