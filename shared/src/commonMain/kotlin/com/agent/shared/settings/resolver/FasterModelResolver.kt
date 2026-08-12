package com.agent.shared.settings.resolver

import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.FasterModelProfile
import com.agent.shared.settings.model.ProviderProfile
import com.agent.shared.settings.model.SettingsDocument

/** AUTO 审批模型的解析来源，用于安全日志和审计而非向模型暴露凭据。 */
enum class FasterModelSource(val wireValue: String) {
    PROVIDER_FAST("provider-faster"),
    GLOBAL_FAST("global-faster"),
    PROVIDER_FIRST("provider-first"),
}

/** 单个 Provider 的审核模型解析结果；[profile] 为 null 时必须交由人工审批。 */
data class FasterModelResolution(
    val profile: ConfigProfile?,
    val source: FasterModelSource? = null,
    val reason: String,
)

/** 按固定优先级解析 AUTO 审批模型，且绝不使用 `defaultModel` 重排原始 models。 */
object FasterModelResolver {
    /** 仅返回可调用 profile，兼容现有调用方。 */
    fun resolve(
        activeProvider: ProviderProfile,
        settings: SettingsDocument,
        layer: ConfigLayer,
    ): ConfigProfile? = resolveDetailed(activeProvider, settings, layer).profile

    /** 返回来源和降级原因，供桌面配置日志准确说明 AUTO 行为。 */
    fun resolveDetailed(
        activeProvider: ProviderProfile,
        settings: SettingsDocument,
        layer: ConfigLayer,
    ): FasterModelResolution {
        if (!activeProvider.isEnabled()) {
            return FasterModelResolution(null, reason = "provider_disabled")
        }
        activeProvider.fasterModel?.takeIf { it.isUsable() }?.let { fast ->
            return resolved(fast.toConfigProfile(activeProvider, layer, FasterModelSource.PROVIDER_FAST), FasterModelSource.PROVIDER_FAST)
        }
        settings.fasterModel?.takeIf { it.isUsable() }?.let { fast ->
            return resolved(fast.toConfigProfile(activeProvider, layer, FasterModelSource.GLOBAL_FAST), FasterModelSource.GLOBAL_FAST)
        }
        val first = activeProvider.models.firstOrNull()
            ?: return FasterModelResolution(null, reason = "provider_models_missing")
        if (!first.isEnabled()) {
            return FasterModelResolution(null, reason = "provider_first_model_disabled")
        }
        return resolved(
            ConfigProfile(
                id = "${activeProvider.id}:auto-review:${first.id}",
                providerId = activeProvider.id,
                providerLabel = activeProvider.label ?: activeProvider.id,
                modelLabel = first.label,
                providerType = activeProvider.providerType,
                baseUrl = activeProvider.baseUrl,
                apiKey = activeProvider.apiKey,
                model = first.id,
                enabled = true,
                layer = layer,
                limit = first.limit ?: com.agent.shared.settings.model.ModelLimit(context = 256_000),
            ),
            FasterModelSource.PROVIDER_FIRST,
        )
    }

    /** 仅完整且启用的显式快速模型可优先于 Provider 原始第一模型。 */
    private fun FasterModelProfile.isUsable(): Boolean = isEnabled() && isComplete()

    /** 构造可调用 profile 并保留来源。 */
    private fun resolved(profile: ConfigProfile, source: FasterModelSource): FasterModelResolution =
        FasterModelResolution(profile = profile, source = source, reason = source.wireValue)

    /** 将显式快速连接转换为运行时 profile。 */
    private fun FasterModelProfile.toConfigProfile(
        activeProvider: ProviderProfile,
        layer: ConfigLayer,
        source: FasterModelSource,
    ): ConfigProfile = ConfigProfile(
        id = "${activeProvider.id}:${source.wireValue}:$model",
        providerId = activeProvider.id,
        providerLabel = activeProvider.label ?: activeProvider.id,
        providerType = providerType,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        enabled = true,
        layer = layer,
        limit = limit ?: com.agent.shared.settings.model.ModelLimit(context = 256_000),
    )
}
