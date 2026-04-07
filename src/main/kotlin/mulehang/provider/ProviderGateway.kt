package mulehang.provider

import mulehang.config.AppConfig

/**
 * 负责把静态 provider 注册信息与本地配置合成为可执行绑定。
 */
class ProviderGateway(private val cfg: AppConfig) {
    /**
     * 解析指定 provider 和模型，返回最终可用于请求的执行参数。
     */
    fun resolve(providerId: String, modelId: String): ExecutorBinding {
        val spec = ProviderRegistry.providers.getValue(providerId)
        val model = spec.models.getValue(modelId)
        val item = cfg.providers[providerId]
        val apiKey = item?.apiKey.orEmpty()
        val baseUrl = item?.baseUrl ?: spec.defaultBaseUrl

        require(apiKey.isNotBlank() || providerId == "ollama") {
            "Missing apiKey for provider $providerId"
        }

        return ExecutorBinding(
            providerId = providerId,
            modelId = model.modelId,
            apiKey = apiKey,
            baseUrl = baseUrl,
            headers = item?.headers ?: emptyMap(),
            supportsTools = model.supportsTools,
            supportsStreaming = model.supportsStreaming,
            supportsReasoning = model.supportsReasoning
        )
    }
}
