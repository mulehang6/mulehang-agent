package mulehang.provider

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
