@file:Suppress("UnstableApiUsage")

package com.agent.runtime.agent

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLMCapability
import com.agent.runtime.provider.OpenAIEndpointMode
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderBindingOptions
import com.agent.runtime.provider.ProviderType
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
            modelId = "openai/gpt-oss-120b:free",
        )

        val resolved = KoogExecutorResolver().resolve(binding)

        assertEquals(binding, resolved.binding)
        assertEquals(
            "ai.koog.prompt.executor.llms.MultiLLMPromptExecutor",
            resolved.promptExecutor::class.qualifiedName,
        )
        assertEquals(LLMProvider.OpenAI, resolved.llmModel.provider)
        assertEquals("openai/gpt-oss-120b:free", resolved.llmModel.id)
        assertEquals(true, resolved.llmModel.supports(LLMCapability.OpenAIEndpoint.Responses))
        assertEquals(false, resolved.llmModel.supports(LLMCapability.OpenAIEndpoint.Completions))
    }

    @Test
    fun `should preserve arbitrary openai compatible routed model ids with responses capability by default`() {
        val binding = ProviderBinding(
            providerId = "provider-compatible",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "test-key",
            modelId = "nvidia/nemotron-nano-12b-v2-vl:free",
        )

        val resolved = KoogExecutorResolver().resolve(binding)

        assertEquals(binding, resolved.binding)
        assertEquals(LLMProvider.OpenAI, resolved.llmModel.provider)
        assertEquals("nvidia/nemotron-nano-12b-v2-vl:free", resolved.llmModel.id)
        assertEquals(true, resolved.llmModel.supports(LLMCapability.OpenAIEndpoint.Responses))
        assertEquals(false, resolved.llmModel.supports(LLMCapability.OpenAIEndpoint.Completions))
    }

    @Test
    fun `should allow openai compatible binding to opt into chat completions endpoint`() {
        val binding = ProviderBinding(
            providerId = "provider-compatible",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "test-key",
            modelId = "nvidia/nemotron-nano-12b-v2-vl:free",
            options = ProviderBindingOptions(openAIEndpointMode = OpenAIEndpointMode.CHAT_COMPLETIONS),
        )

        val resolved = KoogExecutorResolver().resolve(binding)

        assertEquals(binding, resolved.binding)
        assertEquals(true, resolved.llmModel.supports(LLMCapability.OpenAIEndpoint.Completions))
        assertEquals(false, resolved.llmModel.supports(LLMCapability.OpenAIEndpoint.Responses))
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
            "ai.koog.prompt.executor.llms.MultiLLMPromptExecutor",
            anthropic.promptExecutor::class.qualifiedName,
        )
        assertEquals(LLMProvider.Anthropic, anthropic.llmModel.provider)
        assertEquals("claude-sonnet-4-5", anthropic.llmModel.id)

        assertEquals(
            "ai.koog.prompt.executor.llms.MultiLLMPromptExecutor",
            gemini.promptExecutor::class.qualifiedName,
        )
        assertEquals(LLMProvider.Google, gemini.llmModel.provider)
        assertEquals("gemini-2.5-flash", gemini.llmModel.id)
    }

    @Test
    fun `should resolve anthropic custom endpoint through koog client settings`() {
        val binding = ProviderBinding(
            providerId = "provider-anthropic",
            providerType = ProviderType.ANTHROPIC_COMPATIBLE,
            baseUrl = "https://custom-anthropic-endpoint.example.com",
            apiKey = "anthropic-key",
            modelId = "claude-sonnet-4-5",
        )

        val resolved = KoogExecutorResolver().resolve(binding)

        assertEquals(binding, resolved.binding)
        assertEquals(LLMProvider.Anthropic, resolved.llmModel.provider)
    }

    @Test
    fun `should resolve gemini custom endpoint through koog client settings`() {
        val binding = ProviderBinding(
            providerId = "provider-gemini",
            providerType = ProviderType.GEMINI_COMPATIBLE,
            baseUrl = "https://custom-gemini-endpoint.example.com",
            apiKey = "gemini-key",
            modelId = "gemini-2.5-flash",
        )

        val resolved = KoogExecutorResolver().resolve(binding)

        assertEquals(binding, resolved.binding)
        assertEquals(LLMProvider.Google, resolved.llmModel.provider)
    }
}
