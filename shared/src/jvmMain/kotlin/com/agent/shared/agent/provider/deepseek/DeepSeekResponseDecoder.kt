@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.agent.shared.agent.provider.deepseek

import kotlinx.serialization.json.Json

/**
 * 将 DeepSeek SSE 的原始 JSON payload 解码为协议 chunk。
 */
internal object DeepSeekResponseDecoder {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * 解码单个 SSE data payload。
     */
    fun decode(raw: String): DeepSeekChatCompletionChunk =
        json.decodeFromString(DeepSeekChatCompletionChunk.serializer(), raw)
}
