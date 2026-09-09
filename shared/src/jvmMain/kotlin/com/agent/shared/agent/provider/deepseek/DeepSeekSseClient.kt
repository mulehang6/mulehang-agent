@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent.provider.deepseek

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import com.agent.shared.agent.koog.DesktopKoogHttpClientFactoryProvider
import com.agent.shared.agent.prompt.buildOpenAIClientSettings
import com.agent.shared.settings.model.ConfigProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 打开 SSE 连接并按 chunk 产出 DeepSeek 原始流数据。
 */
internal fun openDeepSeekSseChunks(
    request: DeepSeekChatCompletionRequest,
    settings: OpenAIClientSettings,
    apiKey: String,
    httpClientFactory: KoogHttpClient.Factory = DesktopKoogHttpClientFactoryProvider.factory,
): Flow<DeepSeekChatCompletionChunk> = flow {
    val httpClient = createDeepSeekHttpClient(
        settings = settings,
        apiKey = apiKey,
        httpClientFactory = httpClientFactory,
    )
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

/** 使用 profile 的同一请求头和传输适配器打开 DeepSeek 原始 SSE 流。 */
internal fun openDeepSeekSseChunks(
    request: DeepSeekChatCompletionRequest,
    config: ConfigProfile,
): Flow<DeepSeekChatCompletionChunk> = openDeepSeekSseChunks(
    request = request,
    settings = buildOpenAIClientSettings(config),
    apiKey = config.apiKey,
    httpClientFactory = DesktopKoogHttpClientFactoryProvider.factoryFor(config),
)

/**
 * 创建带鉴权和超时配置的 HTTP client。
 */
private fun createDeepSeekHttpClient(
    settings: OpenAIClientSettings,
    apiKey: String,
    httpClientFactory: KoogHttpClient.Factory,
): KoogHttpClient =
    AbstractOpenAILLMClient.createConfiguredHttpClient(
        apiKey = apiKey,
        settings = settings,
        httpClientFactory = httpClientFactory,
        clientName = "DeepSeekChatCompletionsStreamer",
    )
