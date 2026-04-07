package mulehang.config

import java.nio.file.Path
import kotlinx.serialization.json.Json
import utils.loadEnv

/**
 * 负责从项目根目录读取 JSON 配置，并合并进程环境变量与 `.env` 中的密钥。
 */
class ConfigLoader(
    private val root: Path = Path.of("").toAbsolutePath().normalize(),
    private val env: Map<String, String> = System.getenv()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * 读取并解析应用配置，按优先级补齐 provider 的密钥信息。
     */
    fun load(): AppConfig {
        val resolvedEnv = loadEnv(root) + env
        val file = root.resolve("mulehang-agent.json").toFile()
        if (!file.exists()) {
            return AppConfig()
        }

        val cfg = json.decodeFromString<AppConfig>(file.readText())
        return cfg.copy(
            providers = cfg.providers.mapValues { (_, item) ->
                val apiKey = item.apiKey ?: item.apiKeyEnv?.let(resolvedEnv::get)
                item.copy(apiKey = apiKey)
            }
        )
    }
}
