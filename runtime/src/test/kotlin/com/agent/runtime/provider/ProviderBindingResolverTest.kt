package com.agent.runtime.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证 provider binding 解析和协议切换后的刷新规则。
 */
class ProviderBindingResolverTest {

    @Test
    fun `should resolve runtime binding from profile probe and discovery`() {
        val profile = CustomProviderProfile(
            id = "provider-1",
            baseUrl = "https://api.example.com",
            apiKey = "test-key",
            providerType = ProviderType.OPENAI_COMPATIBLE,
        )
        val probe = ConnectionProbeResult(
            providerId = profile.id,
            providerType = profile.providerType,
            isReachable = true,
        )
        val discovery = ModelDiscoveryResult(
            providerId = profile.id,
            providerType = profile.providerType,
            models = listOf(DiscoveredModel(id = "openai/gpt-oss-120b:free")),
            defaultModelId = "openai/gpt-oss-120b:free",
        )
        val resolver = ProviderBindingResolver()

        val binding = resolver.resolve(
            profile = profile,
            probe = probe,
            discovery = discovery,
        )

        assertEquals(
            ProviderBinding(
                providerId = "provider-1",
                providerType = ProviderType.OPENAI_COMPATIBLE,
                baseUrl = "https://api.example.com",
                apiKey = "test-key",
                modelId = "openai/gpt-oss-120b:free",
            ),
            binding,
        )
    }

    @Test
    fun `should invalidate previous discovery when provider type changes`() {
        val previousSnapshot = ProviderResolutionSnapshot(
            providerId = "provider-1",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            discoveredModels = listOf(DiscoveredModel(id = "openai/gpt-oss-120b:free")),
            binding = ProviderBinding(
                providerId = "provider-1",
                providerType = ProviderType.OPENAI_COMPATIBLE,
                baseUrl = "https://api.example.com",
                apiKey = "test-key",
                modelId = "openai/gpt-oss-120b:free",
            ),
        )
        val unchangedProfile = CustomProviderProfile(
            id = "provider-1",
            baseUrl = "https://api.example.com",
            apiKey = "test-key",
            providerType = ProviderType.OPENAI_COMPATIBLE,
        )
        val updatedProfile = unchangedProfile.copy(providerType = ProviderType.ANTHROPIC)
        val resolver = ProviderBindingResolver()

        assertFalse(resolver.shouldRefresh(previousSnapshot = previousSnapshot, currentProfile = unchangedProfile))
        assertTrue(resolver.shouldRefresh(previousSnapshot = previousSnapshot, currentProfile = updatedProfile))
    }
}
