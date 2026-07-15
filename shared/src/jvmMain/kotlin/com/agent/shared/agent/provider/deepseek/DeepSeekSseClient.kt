@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent.provider.deepseek

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import com.agent.shared.agent.DesktopKoogHttpClientFactoryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 打开 SSE 连接并按 chunk 产出 DeepSeek 原始流数据。
 */
internal fun openDeepSeekSseChunks(
    request: DeepSeekChatCompletionRequest,
    settings: OpenAIClientSettings,
    apiKey: String,
): Flow<DeepSeekChatCompletionChunk> = flow {
    val httpClient = createDeepSeekHttpClient(settings = settings, apiKey = apiKey)
    try {
        httpClient.sse(
            path = settings.chatCompletionsPath,
            requestBody = request,
            requestBodyType = DeepSeekChatCompletionRequest::class,
            dataFilter = { it != "[DONE]" },
            decodeStreamingResponse = DeepSeekResponseDecoder::decode,
            processStreamingChunk = { it },
        ).collect(::emit)
    } finally {
        httpClient.close()
    }
}

/**
 * 创建带鉴权和超时配置的 HTTP client。
 */
private fun createDeepSeekHttpClient(settings: OpenAIClientSettings, apiKey: String): KoogHttpClient =
    AbstractOpenAILLMClient.createConfiguredHttpClient(
        apiKey = apiKey,
        settings = settings,
        httpClientFactory = DesktopKoogHttpClientFactoryProvider.factory,
        clientName = "DeepSeekChatCompletionsStreamer",
    )
