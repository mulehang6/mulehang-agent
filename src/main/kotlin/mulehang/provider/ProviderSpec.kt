package mulehang.provider

data class ProviderSpec(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val models: Map<String, ModelSpec>,
    val supportsCustomBaseUrl: Boolean = true
)

data class ModelSpec(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val supportsTools: Boolean,
    val supportsStreaming: Boolean,
    val supportsReasoning: Boolean
)
