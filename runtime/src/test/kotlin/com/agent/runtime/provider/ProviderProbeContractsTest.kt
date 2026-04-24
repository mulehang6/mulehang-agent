package com.agent.runtime.provider

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 provider probe 与 discovery 适配器按提供商类型分派。
 */
class ProviderProbeContractsTest {

    @Test
    fun `should resolve probe adapter by provider type`() = runTest {
        val profile = CustomProviderProfile(
            id = "provider-1",
            baseUrl = "https://api.example.com",
            apiKey = "test-key",
            providerType = ProviderType.GEMINI,
        )
        val adapters = ConnectionProbeAdapters(
            adapters = listOf(
                FakeProbeAdapter(ProviderType.OPENAI_COMPATIBLE),
                FakeProbeAdapter(ProviderType.ANTHROPIC),
                FakeProbeAdapter(ProviderType.GEMINI),
            ),
        )

        val result = adapters.resolve(profile.providerType).probe(profile)

        assertEquals(ProviderType.GEMINI, result.providerType)
        assertTrue(result.isReachable)
    }

    @Test
    fun `should resolve discovery adapter by provider type`() = runTest {
        val profile = CustomProviderProfile(
            id = "provider-2",
            baseUrl = "https://api.example.com",
            apiKey = "test-key",
            providerType = ProviderType.ANTHROPIC,
        )
        val adapters = ModelDiscoveryAdapters(
            adapters = listOf(
                FakeDiscoveryAdapter(ProviderType.OPENAI_COMPATIBLE),
                FakeDiscoveryAdapter(ProviderType.ANTHROPIC),
                FakeDiscoveryAdapter(ProviderType.GEMINI),
            ),
        )

        val result = adapters.resolve(profile.providerType).discover(profile)

        assertEquals(ProviderType.ANTHROPIC, result.providerType)
        assertEquals(listOf(DiscoveredModel(id = "anthropic-model")), result.models)
    }

    /**
     * 提供稳定 probe 结果的测试适配器。
     */
    private class FakeProbeAdapter(
        override val providerType: ProviderType,
    ) : ConnectionProbeAdapter {
        override suspend fun probe(profile: CustomProviderProfile): ConnectionProbeResult = ConnectionProbeResult(
            providerId = profile.id,
            providerType = providerType,
            isReachable = true,
        )
    }

    /**
     * 提供稳定模型目录的测试适配器。
     */
    private class FakeDiscoveryAdapter(
        override val providerType: ProviderType,
    ) : ModelDiscoveryAdapter {
        override suspend fun discover(profile: CustomProviderProfile): ModelDiscoveryResult = ModelDiscoveryResult(
            providerId = profile.id,
            providerType = providerType,
            models = listOf(DiscoveredModel(id = providerType.name.lowercase().replace('_', '-') + "-model")),
            defaultModelId = providerType.name.lowercase().replace('_', '-') + "-model",
        )
    }
}
