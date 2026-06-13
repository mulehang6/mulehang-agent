package com.agent.shared.config

/**
 * 负责把用户级、项目级与环境变量覆盖合并为最终 profile 列表。
 */
object SettingsMerger {
    private val environmentKeys = setOf(
        "MULEHANG_PROFILE_ID",
        "MULEHANG_PROVIDER_TYPE",
        "MULEHANG_BASE_URL",
        "MULEHANG_API_KEY",
        "MULEHANG_MODEL",
        "MULEHANG_ENABLED",
        "MULEHANG_CONTEXT_WINDOW",
        "MULEHANG_INPUT_TOKEN_LIMIT",
        "MULEHANG_OUTPUT_TOKEN_LIMIT",
    )

    /**
     * 生成最终 profile 列表。
     */
    fun merge(
        user: SettingsDocument?,
        project: SettingsDocument?,
        environment: Map<String, String>,
    ): List<ConfigProfile> {
        val profiles = linkedMapOf<String, LayeredProfile>()
        user?.profiles.orEmpty().forEach { profile ->
            profiles[profile.id] = LayeredProfile(profile, ConfigLayer.USER)
        }
        project?.profiles.orEmpty().forEach { profile ->
            profiles[profile.id] = LayeredProfile(profile, ConfigLayer.PROJECT)
        }

        if (profiles.isEmpty()) {
            return environmentProfile(environment)?.let(::listOf).orEmpty()
        }

        val hasEnvironmentOverride = environmentKeys.any(environment::containsKey)
        return profiles.values.map { layered ->
            layered.profile.toConfigProfile(
                environment = environment,
                layer = if (hasEnvironmentOverride) ConfigLayer.ENVIRONMENT else layered.layer,
            )
        }
    }

    /**
     * 从环境变量构造无 JSON 配置时可用的最小 profile。
     */
    private fun environmentProfile(environment: Map<String, String>): ConfigProfile? {
        if (!environmentKeys.any(environment::containsKey)) return null

        return ConfigProfile(
            id = environment["MULEHANG_PROFILE_ID"] ?: "environment",
            providerType = environment["MULEHANG_PROVIDER_TYPE"]?.toProviderType() ?: ProviderType.OPENAI_RESPONSES,
            baseUrl = environment["MULEHANG_BASE_URL"] ?: "https://api.openai.com/v1",
            apiKey = environment["MULEHANG_API_KEY"] ?: "",
            model = (environment["MULEHANG_MODEL"] ?: "gpt-4.1").sanitizeModelName(),
            enabled = environment["MULEHANG_ENABLED"]?.toBooleanStrictOrNull() ?: true,
            layer = ConfigLayer.ENVIRONMENT,
            limit = environment.toModelLimit(default = null),
        )
    }

    /**
     * 将原始 profile 应用环境变量覆盖后转为运行时 profile。
     */
    private fun AgentProfile.toConfigProfile(
        environment: Map<String, String>,
        layer: ConfigLayer,
    ): ConfigProfile = ConfigProfile(
        id = environment["MULEHANG_PROFILE_ID"] ?: id,
        providerType = environment["MULEHANG_PROVIDER_TYPE"]?.toProviderType() ?: providerType,
        baseUrl = environment["MULEHANG_BASE_URL"] ?: baseUrl,
        apiKey = environment["MULEHANG_API_KEY"] ?: apiKey,
        model = (environment["MULEHANG_MODEL"] ?: model).sanitizeModelName(),
        enabled = environment["MULEHANG_ENABLED"]?.toBooleanStrictOrNull() ?: isEnabled(),
        layer = layer,
        limit = environment.toModelLimit(default = limit),
    )

    /**
     * 合并环境变量里的 token 限制，未设置的字段保留 JSON 中的显式值。
     */
    private fun Map<String, String>.toModelLimit(default: ModelLimit?): ModelLimit? {
        val context = this["MULEHANG_CONTEXT_WINDOW"]?.toPositiveIntOrNull() ?: default?.context
        val input = this["MULEHANG_INPUT_TOKEN_LIMIT"]?.toPositiveIntOrNull() ?: default?.input
        val output = this["MULEHANG_OUTPUT_TOKEN_LIMIT"]?.toPositiveIntOrNull() ?: default?.output
        return ModelLimit(context = context, input = input, output = output)
            .takeUnless { it.context == null && it.input == null && it.output == null }
    }

    /**
     * 解析环境变量中的 providerType，兼容 JSON serial name 与枚举名。
     */
    private fun String.toProviderType(): ProviderType = when (trim().lowercase()) {
        "openai-responses" -> ProviderType.OPENAI_RESPONSES
        "openai-chat-completions" -> ProviderType.OPENAI_CHAT_COMPLETIONS
        "anthropic" -> ProviderType.ANTHROPIC
        "google" -> ProviderType.GOOGLE
        else -> ProviderType.valueOf(trim().uppercase())
    }

    private fun String.sanitizeModelName(): String =
        replace(Regex("\\u001B\\[[0-9;]*[A-Za-z]"), "")
            .replace(Regex("\\[[0-9;]*m$"), "")
            .trim()

    private fun String.toPositiveIntOrNull(): Int? =
        trim().toIntOrNull()?.takeIf { it > 0 }

    /**
     * 保留原始 profile 与其来源层级。
     */
    private data class LayeredProfile(
        val profile: AgentProfile,
        val layer: ConfigLayer,
    )
}
