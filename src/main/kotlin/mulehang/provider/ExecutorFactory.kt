package mulehang.provider

import mulehang.config.AppConfig

class ExecutorFactory(private val cfg: AppConfig) {
    private val gateway = ProviderGateway(cfg)

    fun defaultBinding(): ExecutorBinding {
        return gateway.resolve(cfg.defaultProvider, cfg.defaultModel)
    }

    @Suppress("unused")
    fun binding(providerId: String, modelId: String): ExecutorBinding {
        return gateway.resolve(providerId, modelId)
    }
}
