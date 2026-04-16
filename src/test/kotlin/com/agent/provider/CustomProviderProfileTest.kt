package com.agent.provider

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 custom provider 配置模型的默认值和可变边界。
 */
class CustomProviderProfileTest {

    @Test
    fun `should default provider type to openai compatible`() {
        val profile = CustomProviderProfile(
            id = "provider-1",
            baseUrl = "https://api.example.com",
            apiKey = "test-key",
        )

        assertEquals(ProviderType.OPENAI_COMPATIBLE, profile.providerType)
    }

    @Test
    fun `should allow changing provider type after creation`() {
        val profile = CustomProviderProfile(
            id = "provider-1",
            baseUrl = "https://api.example.com",
            apiKey = "test-key",
        )

        val updatedProfile = profile.copy(providerType = ProviderType.ANTHROPIC_COMPATIBLE)

        assertEquals(ProviderType.ANTHROPIC_COMPATIBLE, updatedProfile.providerType)
    }
}
