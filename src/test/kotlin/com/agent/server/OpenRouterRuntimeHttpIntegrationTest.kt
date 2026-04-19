package com.agent.server

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
private const val OPENROUTER_API_KEY_ENV = "OPENROUTER_API_KEY"
private const val OPENROUTER_OPENAI_MODEL = "openai/gpt-oss-120b:free"
private const val OPENROUTER_COMPATIBLE_MODEL = "nvidia/nemotron-nano-12b-v2-vl:free"

/**
 * 通过 OpenRouter 真实发送 OpenAI-compatible 请求的集成测试。
 *
 * 默认不会执行；只有设置 OPENROUTER_API_KEY 后才会启用，避免日常构建依赖外部网络、密钥和免费模型可用性。
 */
@EnabledIfEnvironmentVariable(named = OPENROUTER_API_KEY_ENV, matches = ".+")
class OpenRouterRuntimeHttpIntegrationTest {

    @Test
    fun `should execute real request with openai routed free model`() = runTest(timeout = 120.seconds) {
        val response = DefaultRuntimeHttpService().run(
            request = requestFor(
                providerId = "provider-openrouter-openai",
                modelId = OPENROUTER_OPENAI_MODEL,
            ),
        )

        assertTrue(response.success, response.failure?.message.orEmpty())
        assertNotNull(response.output)
    }

    @Test
    fun `should execute real request with openai compatible routed free model`() = runTest(timeout = 120.seconds) {
        val response = DefaultRuntimeHttpService().run(
            request = requestFor(
                providerId = "provider-openrouter-compatible",
                modelId = OPENROUTER_COMPATIBLE_MODEL,
            ),
        )

        assertTrue(response.success, response.failure?.message.orEmpty())
        assertNotNull(response.output)
    }

    private fun requestFor(
        providerId: String,
        modelId: String,
    ): RuntimeRunHttpRequest = RuntimeRunHttpRequest(
        prompt = "请只回复 MULEHANG_OK，不要添加其他内容。",
        provider = ProviderBindingHttpRequest(
            providerId = providerId,
            providerType = "OPENAI_COMPATIBLE",
            baseUrl = OPENROUTER_BASE_URL,
            apiKey = System.getenv(OPENROUTER_API_KEY_ENV).orEmpty(),
            modelId = modelId,
        ),
    )
}
