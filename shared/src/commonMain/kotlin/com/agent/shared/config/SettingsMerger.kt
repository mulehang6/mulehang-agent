package com.agent.shared.config

/**
 * 负责把用户级、项目级与环境变量覆盖合并为最终 profile 列表。
 */
object SettingsMerger {
    private val environmentKeys = setOf(
        "MULEHANG_PROFILE_ID",
        "MULEHANG_PROVIDER_ID",
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
        val providers = linkedMapOf<String, LayeredProvider>()
        user?.providers.orEmpty().forEach { provider ->
            providers[provider.id] = LayeredProvider(provider, ConfigLayer.USER)
        }
        project?.providers.orEmpty().forEach { provider ->
            providers[provider.id] = LayeredProvider(provider, ConfigLayer.PROJECT)
        }

        if (providers.isEmpty()) {
            return environmentProfile(environment)?.let(::listOf).orEmpty()
        }

        val hasEnvironmentOverride = environmentKeys.any(environment::containsKey)
        return providers.values.flatMap { layered ->
            layered.provider.toConfigProfiles(
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
            id = buildProfileId(
                providerId = environment["MULEHANG_PROVIDER_ID"] ?: environment["MULEHANG_PROFILE_ID"] ?: "environment",
                model = (environment["MULEHANG_MODEL"] ?: "gpt-4.1").sanitizeModelName(),
            ),
            providerId = environment["MULEHANG_PROVIDER_ID"] ?: environment["MULEHANG_PROFILE_ID"] ?: "environment",
            providerLabel = environment["MULEHANG_PROVIDER_ID"] ?: environment["MULEHANG_PROFILE_ID"] ?: "environment",
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
     * 将 provider 下启用的模型展平成运行时 profile。
     */
    private fun ProviderProfile.toConfigProfiles(
        environment: Map<String, String>,
        layer: ConfigLayer,
    ): List<ConfigProfile> {
        val providerId = environment["MULEHANG_PROVIDER_ID"] ?: id
        val providerEnabled = environment["MULEHANG_ENABLED"]?.toBooleanStrictOrNull() ?: isEnabled()
        val configuredModels = models.orderDefaultFirst(defaultModel)
        val effectiveModels = environment["MULEHANG_MODEL"]
            ?.let { listOf(ModelProfile(id = it.sanitizeModelName())) }
            ?: configuredModels

        return effectiveModels
            .filter { model -> providerEnabled && model.isEnabled() }
            .map { model ->
                val modelId = model.id.sanitizeModelName()
                ConfigProfile(
                    id = environment["MULEHANG_PROFILE_ID"] ?: buildProfileId(providerId, modelId),
                    providerId = providerId,
                    providerLabel = label ?: providerId,
                    modelLabel = model.label,
                    providerType = environment["MULEHANG_PROVIDER_TYPE"]?.toProviderType() ?: providerType,
                    baseUrl = environment["MULEHANG_BASE_URL"] ?: baseUrl,
                    apiKey = environment["MULEHANG_API_KEY"] ?: apiKey,
                    model = modelId,
                    enabled = true,
                    layer = layer,
                    limit = environment.toModelLimit(default = model.limit),
                )
            }
    }

    /**
     * 合并环境变量里的 token 限制，未设置的字段保留 JSON 中的显式值。
     */
    private fun Map<String, String>.toModelLimit(default: ModelLimit?): ModelLimit {
        val context = this["MULEHANG_CONTEXT_WINDOW"]?.toPositiveIntOrNull() ?: default?.context ?: DEFAULT_CONTEXT_WINDOW
        val input = this["MULEHANG_INPUT_TOKEN_LIMIT"]?.toPositiveIntOrNull() ?: default?.input
        val output = this["MULEHANG_OUTPUT_TOKEN_LIMIT"]?.toPositiveIntOrNull() ?: default?.output ?: DEFAULT_OUTPUT_TOKEN_LIMIT
        return ModelLimit(context = context, input = input, output = output)
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

    private fun List<ModelProfile>.orderDefaultFirst(defaultModel: String?): List<ModelProfile> {
        if (defaultModel.isNullOrBlank()) return this
        return sortedBy { model -> if (model.id == defaultModel) 0 else 1 }
    }

    private fun buildProfileId(providerId: String, model: String): String =
        "$providerId:$model"

    /**
     * 保留原始 provider 与其来源层级。
     */
    private data class LayeredProvider(
        val provider: ProviderProfile,
        val layer: ConfigLayer,
    )

    private const val DEFAULT_CONTEXT_WINDOW = 1_000_000

    private const val DEFAULT_OUTPUT_TOKEN_LIMIT = 384_000
}
