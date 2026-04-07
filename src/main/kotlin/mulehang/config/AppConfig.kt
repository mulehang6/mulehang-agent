package mulehang.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val defaultProvider: String = "openrouter",
    val defaultModel: String = "openrouter.gpt4o",
    val providers: Map<String, ProviderConfig> = emptyMap(),
    val mcp: Map<String, McpServerConfig> = emptyMap(),
    val skills: SkillConfig = SkillConfig()
)

@Serializable
data class ProviderConfig(
    val enabled: Boolean = true,
    val apiKey: String? = null,
    val apiKeyEnv: String? = null,
    val baseUrl: String? = null,
    val headers: Map<String, String> = emptyMap()
)

@Serializable
data class McpServerConfig(
    val command: List<String> = emptyList(),
    val url: String? = null,
    val env: Map<String, String> = emptyMap()
)

@Serializable
data class SkillConfig(
    val paths: List<String> = emptyList(),
    val remote: List<String> = emptyList()
)
