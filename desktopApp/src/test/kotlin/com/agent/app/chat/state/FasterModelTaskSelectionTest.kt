package com.agent.app.chat.state

import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import kotlin.test.Test
import kotlin.test.assertEquals

/** 快速模型在内部任务中的统一选择规则回归测试。 */
class FasterModelTaskSelectionTest {

    /** 已解析的同 Provider 快速模型优先用于内部标题等低延迟任务。 */
    @Test
    fun `should use configured faster profile for matching provider`() {
        val primary = profile(id = "primary", model = "reasoning")
        val faster = profile(id = "fast", model = "flash")

        assertEquals(faster, fasterProfileForInternalTask(primary, mapOf("gateway" to faster)))
    }

    /** 没有可用快速模型时必须复用主模型，不能让标题任务失去执行配置。 */
    @Test
    fun `should fall back to primary profile when faster model is unavailable`() {
        val primary = profile(id = "primary", model = "reasoning")

        assertEquals(primary, fasterProfileForInternalTask(primary, emptyMap()))
    }

    /** 创建满足运行时模型配置最小约束的测试 profile。 */
    private fun profile(id: String, model: String): ConfigProfile = ConfigProfile(
        id = id,
        providerId = "gateway",
        providerType = ProviderType.OPENAI_RESPONSES,
        baseUrl = "https://gateway.example.test/v1",
        apiKey = "placeholder",
        model = model,
        enabled = true,
        layer = ConfigLayer.USER,
    )
}
