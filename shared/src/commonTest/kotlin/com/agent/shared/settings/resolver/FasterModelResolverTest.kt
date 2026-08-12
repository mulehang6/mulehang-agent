package com.agent.shared.settings.resolver

import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.FasterModelProfile
import com.agent.shared.settings.model.ModelProfile
import com.agent.shared.settings.model.ProviderProfile
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.settings.model.SettingsDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertEquals

/** 验证 AUTO 审批模型的固定回退顺序。 */
class FasterModelResolverTest {
    /** provider 本地快速模型必须优先于全局快速模型。 */
    @Test
    fun `prefers provider faster model`() {
        val provider = provider(fasterModel = faster("provider-fast"))
        val result = FasterModelResolver.resolve(provider, SettingsDocument(fasterModel = faster("global-fast")), ConfigLayer.PROJECT)
        assertEquals("provider-fast", result?.model)
    }

    /** 没有 fasterModel 时只取 JSON 原始第一项，不能被 defaultModel 改写。 */
    @Test
    fun `uses first raw provider model and ignores default model`() {
        val result = FasterModelResolver.resolve(provider(), SettingsDocument(), ConfigLayer.PROJECT)
        assertEquals("first", result?.model)
    }

    /** 第一项禁用时不得跳到后续模型，必须改为人工审批。 */
    @Test
    fun `returns null when first raw model is disabled`() {
        val result = FasterModelResolver.resolve(provider(firstEnabled = false), SettingsDocument(), ConfigLayer.PROJECT)
        assertNull(result)
    }

    /** 不完整的本地 fast 配置不能阻断完整的全局 fast 配置。 */
    @Test
    fun `skips incomplete provider faster model`() {
        val incomplete = FasterModelProfile(
            providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://example.test/v1",
            apiKey = "",
            model = "broken",
        )

        val result = FasterModelResolver.resolveDetailed(
            provider(fasterModel = incomplete),
            SettingsDocument(fasterModel = faster("global-fast")),
            ConfigLayer.PROJECT,
        )

        assertEquals("global-fast", result.profile?.model)
        assertEquals(FasterModelSource.GLOBAL_FAST, result.source)
    }

    /** Provider 禁用时不得把任意模型交给 AUTO 审批。 */
    @Test
    fun `returns manual reason when provider is disabled`() {
        val result = FasterModelResolver.resolveDetailed(
            provider().copy(enabled = false),
            SettingsDocument(),
            ConfigLayer.PROJECT,
        )

        assertNull(result.profile)
        assertEquals("provider_disabled", result.reason)
    }

    private fun provider(fasterModel: FasterModelProfile? = null, firstEnabled: Boolean? = null) = ProviderProfile(
        id = "provider",
        providerType = ProviderType.OPENAI_RESPONSES,
        baseUrl = "https://example.test/v1",
        apiKey = "placeholder",
        models = listOf(ModelProfile("first", enabled = firstEnabled), ModelProfile("default")),
        defaultModel = "default",
        fasterModel = fasterModel,
    )

    private fun faster(model: String) = FasterModelProfile(
        providerType = ProviderType.OPENAI_RESPONSES,
        baseUrl = "https://example.test/v1",
        apiKey = "placeholder",
        model = model,
    )
}
