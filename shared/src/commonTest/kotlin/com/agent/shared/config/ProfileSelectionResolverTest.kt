package com.agent.shared.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 验证按项目记忆的 profile 选择逻辑。
 */
class ProfileSelectionResolverTest {

    /**
     * 记忆的 profile 仍启用时应优先恢复。
     */
    @Test
    fun `should restore remembered profile when enabled`() {
        val profiles = listOf(
            configProfile("openai-main", enabled = true),
            configProfile("google-main", enabled = true),
        )

        val selected = ProfileSelectionResolver.selectActiveProfile(
            profiles = profiles,
            rememberedProfileId = "google-main",
        )

        assertEquals("google-main", selected?.id)
    }

    /**
     * 记忆的 profile 被禁用时应回退到第一个启用 profile。
     */
    @Test
    fun `should fall back to first enabled profile when remembered profile is disabled`() {
        val profiles = listOf(
            configProfile("openai-main", enabled = true),
            configProfile("anthropic-main", enabled = false),
        )

        val selected = ProfileSelectionResolver.selectActiveProfile(
            profiles = profiles,
            rememberedProfileId = "anthropic-main",
        )

        assertEquals("openai-main", selected?.id)
    }

    /**
     * 没有启用 profile 时应返回 null，让 UI 提示用户配置。
     */
    @Test
    fun `should return null when no enabled profile exists`() {
        val selected = ProfileSelectionResolver.selectActiveProfile(
            profiles = listOf(configProfile("disabled", enabled = false)),
            rememberedProfileId = null,
        )

        assertNull(selected)
    }

    private fun configProfile(id: String, enabled: Boolean): ConfigProfile = ConfigProfile(
        id = id,
        providerType = ProviderType.OPENAI_RESPONSES,
        baseUrl = "https://api.openai.com/v1",
        apiKey = "key",
        model = "gpt-4.1",
        enabled = enabled,
        layer = ConfigLayer.USER,
    )
}
