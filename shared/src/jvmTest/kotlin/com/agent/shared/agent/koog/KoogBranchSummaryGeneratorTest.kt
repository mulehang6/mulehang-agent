package com.agent.shared.agent.koog

import com.agent.shared.agent.api.BranchSummaryRequest
import com.agent.shared.agent.api.GeneratedBranchSummary
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 验证分支摘要生成器保持单次无工具调用边界和自定义指令协议。 */
class KoogBranchSummaryGeneratorTest {
    /** 注入 runner 的结果和请求必须原样透传，便于控制器安全处理提交时机。 */
    @Test
    fun `should delegate request and return generated summary`() = runTest {
        var captured: BranchSummaryRequest? = null
        val generator = KoogBranchSummaryGenerator(
            agentRunner = { request ->
                captured = request
                GeneratedBranchSummary("保留的分支摘要", inputTokens = 12L, outputTokens = 5L)
            },
        )
        val request = request()

        val result = generator.generate(request)

        assertEquals("保留的分支摘要", result.summary)
        assertEquals(12L, result.inputTokens)
        assertEquals(5L, result.outputTokens)
        assertSame(request, captured)
    }

    /** 模型调用失败必须向上传播，控制器才能保证失败时不移动 leaf。 */
    @Test
    fun `should propagate runner failure`() = runTest {
        val generator = KoogBranchSummaryGenerator(agentRunner = { error("network failed") })

        val error = assertFailsWith<IllegalStateException> { generator.generate(request()) }

        assertEquals("network failed", error.message)
    }

    /** 自定义摘要文本是附加指令，而离开分支内容保持独立边界。 */
    @Test
    fun `should keep branch content separate from custom instructions`() {
        val prompt = branchSummaryUserPrompt(request())

        assertTrue(prompt.contains("Assistant: changed file"))
        assertTrue(prompt.contains("Additional summarization instructions:"))
        assertTrue(prompt.endsWith("只保留未完成事项"))
    }

    /** 创建固定摘要请求。 */
    private fun request(): BranchSummaryRequest = BranchSummaryRequest(
        branchContent = "Assistant: changed file",
        customInstructions = "只保留未完成事项",
        profile = ConfigProfile(
            id = "openai-main",
            providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com/v1",
            apiKey = "key",
            model = "gpt-4.1",
            enabled = true,
            layer = ConfigLayer.PROJECT,
        ),
    )
}
