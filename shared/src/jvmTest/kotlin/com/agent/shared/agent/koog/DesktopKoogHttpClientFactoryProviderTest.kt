@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent.koog

import ai.koog.http.client.ktor.KtorKoogHttpClient
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 锁定 Desktop 平台 Koog HTTP 工厂的默认引擎选择。
 */
class DesktopKoogHttpClientFactoryProviderTest {

    /**
     * Desktop 统一工厂应保留基于 JDK HttpClient 的 Ktor client，并加上 SSE 终止标记过滤。
     */
    @Test
    fun `should create sse filtering client backed by ktor java engine`() {
        val client = DesktopKoogHttpClientFactoryProvider.factory.create(
            clientName = "test",
            baseUrl = "https://api.deepseek.com/v1",
            headers = emptyMap(),
            queryParameters = emptyMap(),
            requestTimeoutMillis = 1_000,
            connectTimeoutMillis = 1_000,
            socketTimeoutMillis = 1_000,
            json = Json,
        )

        val filteringClient = assertIs<SseTerminatorFilteringKoogHttpClient>(client)
        assertEquals("test", filteringClient.clientName)
        val ktorClient = assertIs<KtorKoogHttpClient>(filteringClient.delegate)
        assertTrue(ktorClient.ktorClient.engine::class.qualifiedName.orEmpty().contains(".java.", ignoreCase = true))
    }
}
