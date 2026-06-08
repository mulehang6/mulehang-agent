package com.agent.shared.application

import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证应用启动会话装配与 profile 记忆写回。
 */
class LoadAppSessionUseCaseTest {

    /**
     * 启动时应选择有效 profile，并把最终选择写回记忆状态。
     */
    @Test
    fun `should load active profile and persist resolved selection`() = runTest {
        val repository = FakeAppSessionRepository(
            profiles = listOf(
                configProfile("openai-main"),
                configProfile("google-main"),
            ),
            rememberedProfileId = "google-main",
        )

        val snapshot = LoadAppSessionUseCase(repository).invoke()

        assertEquals("google-main", snapshot.activeProfile?.id)
        assertEquals("google-main", repository.savedProfileId)
    }

    /**
     * 记忆值失效时应回退并保存新的 active profile。
     */
    @Test
    fun `should persist fallback profile when remembered selection is invalid`() = runTest {
        val repository = FakeAppSessionRepository(
            profiles = listOf(configProfile("openai-main")),
            rememberedProfileId = "missing",
        )

        val snapshot = LoadAppSessionUseCase(repository).invoke()

        assertEquals("openai-main", snapshot.activeProfile?.id)
        assertEquals("openai-main", repository.savedProfileId)
    }

    private class FakeAppSessionRepository(
        private val profiles: List<ConfigProfile>,
        private val rememberedProfileId: String?,
    ) : AppSessionRepository {
        var savedProfileId: String? = null

        override suspend fun loadProfiles(): List<ConfigProfile> = profiles

        override suspend fun loadRememberedProfileId(): String? = rememberedProfileId

        override suspend fun saveRememberedProfileId(profileId: String) {
            savedProfileId = profileId
        }
    }

    private fun configProfile(id: String): ConfigProfile = ConfigProfile(
        id = id,
        providerType = ProviderType.OPENAI_RESPONSES,
        baseUrl = "https://api.openai.com/v1",
        apiKey = "key",
        model = "gpt-4.1",
        enabled = true,
        layer = ConfigLayer.USER,
    )
}
