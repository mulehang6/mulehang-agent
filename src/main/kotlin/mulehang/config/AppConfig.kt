package mulehang.config

import kotlinx.serialization.Serializable

/**
 * 描述应用运行时可加载的顶层配置。
 */
@Serializable
data class AppConfig(
    val defaultProvider: String = "openrouter",
    val defaultModel: String = "openrouter.gpt4o",
    val providers: Map<String, ProviderConfig> = emptyMap(),
    val mcp: Map<String, McpServerConfig> = emptyMap(),
    val skills: SkillConfig = SkillConfig()
)

/**
 * 描述单个大模型 provider 的本地配置覆盖项。
 */
@Serializable
data class ProviderConfig(
    val enabled: Boolean = true,
    val apiKey: String? = null,
    val apiKeyEnv: String? = null,
    val baseUrl: String? = null,
    val headers: Map<String, String> = emptyMap()
)

/**
 * 描述单个 MCP 服务端的连接配置。
 */
@Serializable
data class McpServerConfig(
    val command: List<String> = emptyList(),
    val url: String? = null,
    val env: Map<String, String> = emptyMap()
)

/**
 * 描述本地与远端技能目录的配置入口。
 */
@Serializable
data class SkillConfig(
    val paths: List<String> = emptyList(),
    val remote: List<String> = emptyList()
)
