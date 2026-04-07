package mulehang.provider

/**
 * 描述一次实际执行请求时使用的 provider 绑定结果。
 */
data class ExecutorBinding(
    val providerId: String,
    val modelId: String,
    val apiKey: String,
    val baseUrl: String,
    val headers: Map<String, String>,
    val supportsTools: Boolean,
    val supportsStreaming: Boolean,
    val supportsReasoning: Boolean
)
