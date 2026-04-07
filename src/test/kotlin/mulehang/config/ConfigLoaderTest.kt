package mulehang.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigLoaderTest {
    @Test
    fun `should read provider config from mulehang-agent json`() {
        val dir = Files.createTempDirectory("mulehang-config").toFile()
        dir.resolve("mulehang-agent.json").writeText(
            """
            {
              "defaultProvider": "openrouter",
              "defaultModel": "openrouter.gpt4o",
              "providers": {
                "openrouter": {
                  "apiKey": "file-key",
                  "baseUrl": "https://openrouter.ai/api/v1"
                }
              }
            }
            """.trimIndent()
        )

        val cfg = ConfigLoader(root = dir.toPath(), env = emptyMap()).load()

        assertEquals("openrouter", cfg.defaultProvider)
        assertEquals("openrouter.gpt4o", cfg.defaultModel)
        assertEquals("file-key", cfg.providers.getValue("openrouter").apiKey)
    }

    @Test
    fun `should resolve api key from env when apiKeyEnv is provided`() {
        val dir = Files.createTempDirectory("mulehang-config-env").toFile()
        dir.resolve("mulehang-agent.json").writeText(
            """
            {
              "providers": {
                "openrouter": {
                  "apiKeyEnv": "OPENROUTER_API_KEY",
                  "baseUrl": "https://openrouter.ai/api/v1"
                }
              }
            }
            """.trimIndent()
        )

        val cfg = ConfigLoader(
            root = dir.toPath(),
            env = mapOf("OPENROUTER_API_KEY" to "env-key")
        ).load()

        assertEquals("env-key", cfg.providers.getValue("openrouter").apiKey)
    }
}
