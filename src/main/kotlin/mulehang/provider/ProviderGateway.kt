package mulehang.provider

import mulehang.config.AppConfig

class ProviderGateway(private val cfg: AppConfig) {
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
