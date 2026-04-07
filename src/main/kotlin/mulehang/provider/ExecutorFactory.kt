package mulehang.provider

import mulehang.config.AppConfig

/**
 * 基于应用配置创建默认或指定模型的执行绑定。
 */
class ExecutorFactory(private val cfg: AppConfig) {
    private val gateway = ProviderGateway(cfg)

    /**
     * 构造当前默认 provider 与默认模型对应的执行绑定。
     */
    fun defaultBinding(): ExecutorBinding {
        return gateway.resolve(cfg.defaultProvider, cfg.defaultModel)
    }

    @Suppress("unused")
    /**
     * 构造指定 provider 与模型对应的执行绑定。
     */
    fun binding(providerId: String, modelId: String): ExecutorBinding {
        return gateway.resolve(providerId, modelId)
    }
}
