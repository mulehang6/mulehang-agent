package com.agent.runtime.server

import com.agent.runtime.utils.readDotEnv
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

private const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
private const val OPENROUTER_API_KEY_ENV = "OPENROUTER_API_KEY"
private const val OPENROUTER_COMPATIBLE_MODEL = "nvidia/nemotron-3-super-120b-a12b:free"
private const val OPENAI_RESPONSES = "OPENAI_RESPONSES"
private const val OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE"

/**
 * 通过 OpenRouter 真实发送 OpenAI-compatible 请求的集成测试。
 *
 * 默认优先从项目根目录 .env 读取 OPENROUTER_API_KEY；没有 key 时跳过，避免日常构建依赖外部网络和密钥。
 */
class OpenRouterRuntimeHttpIntegrationTest {

    @Test
    fun `should execute real request with openai routed free model`() = runTest(timeout = 120.seconds) {
        val response = DefaultRuntimeHttpService().run(
            request = requestFor(
                providerId = "provider-openrouter-openai",
                providerType = OPENAI_RESPONSES,
            ),
        )

        assertEquals(1, response.code, response.message)
        assertNotNull(response.data.output)
    }

    @Test
    fun `should execute real request with openai compatible routed free model`() = runTest(timeout = 120.seconds) {
        val response = DefaultRuntimeHttpService().run(
            request = requestFor(
                providerId = "provider-openrouter-compatible",
                providerType = OPENAI_COMPATIBLE,
            ),
        )

        assertEquals(1, response.code, response.message)
        assertNotNull(response.data.output)
    }

    private fun requestFor(
        providerId: String,
        providerType: String,
    ): RuntimeRunHttpRequest = RuntimeRunHttpRequest(
        prompt = "请只回复 MULEHANG_OK，不要添加其他内容。",
        provider = ProviderBindingHttpRequest(
            providerId = providerId,
            providerType = providerType,
            baseUrl = OPENROUTER_BASE_URL,
            apiKey = openRouterApiKeyOrSkip(),
            modelId = OPENROUTER_COMPATIBLE_MODEL,
        ),
    )

    /**
     * 优先读取项目根目录 .env 中的 OpenRouter key；系统环境变量仅作为 CI 或临时覆盖入口。
     */
    private fun openRouterApiKeyOrSkip(): String {
        val apiKey = readDotEnv()[OPENROUTER_API_KEY_ENV]
            ?: System.getenv(OPENROUTER_API_KEY_ENV)
            ?: ""
        assumeTrue(apiKey.isNotBlank(), "缺少 .env 中的 $OPENROUTER_API_KEY_ENV，跳过 OpenRouter 真实请求测试。")
        return apiKey
    }
}
