package mulehang.config

import java.nio.file.Path
import kotlinx.serialization.json.Json

class ConfigLoader(
    private val root: Path = Path.of("").toAbsolutePath().normalize(),
    private val env: Map<String, String> = System.getenv()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): AppConfig {
        val file = root.resolve("mulehang-agent.json").toFile()
        if (!file.exists()) {
            return AppConfig()
        }

        val cfg = json.decodeFromString<AppConfig>(file.readText())
        return cfg.copy(
            providers = cfg.providers.mapValues { (_, item) ->
                val apiKey = item.apiKey ?: item.apiKeyEnv?.let(env::get)
                item.copy(apiKey = apiKey)
            }
        )
    }
}
