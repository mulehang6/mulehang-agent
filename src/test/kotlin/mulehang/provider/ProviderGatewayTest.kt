package mulehang.provider

import mulehang.config.AppConfig
import mulehang.config.ProviderConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderGatewayTest {
    @Test
    fun `should merge registry defaults with config overrides`() {
        val cfg = AppConfig(
            providers = mapOf(
                "openrouter" to ProviderConfig(
                    apiKey = "key-1",
                    baseUrl = "https://openrouter.example.com/v1"
                )
            )
        )

        val binding = ProviderGateway(cfg).resolve("openrouter", "openrouter.gpt4o")

        assertEquals("openrouter", binding.providerId)
        assertEquals("openrouter.gpt4o", binding.modelId)
        assertEquals("key-1", binding.apiKey)
        assertEquals("https://openrouter.example.com/v1", binding.baseUrl)
    }

    @Test
    fun `should reject provider bindings without api key`() {
        val cfg = AppConfig()

        val err = runCatching {
            ProviderGateway(cfg).resolve("openrouter", "openrouter.gpt4o")
        }.exceptionOrNull()

        requireNotNull(err)
        assertEquals("Missing apiKey for provider openrouter", err.message)
    }
}
