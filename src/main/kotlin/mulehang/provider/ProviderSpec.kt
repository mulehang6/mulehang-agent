package mulehang.provider

/**
 * 描述单个 provider 的静态元数据及其内置模型目录。
 */
data class ProviderSpec(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val models: Map<String, ModelSpec>,
    val supportsCustomBaseUrl: Boolean = true
)

/**
 * 描述单个模型在当前 provider 下的能力画像。
 */
data class ModelSpec(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val supportsTools: Boolean,
    val supportsStreaming: Boolean,
    val supportsReasoning: Boolean
)
