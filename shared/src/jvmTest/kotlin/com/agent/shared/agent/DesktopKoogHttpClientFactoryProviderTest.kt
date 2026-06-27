@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent

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
     * Desktop 统一工厂应固定产出基于 JDK HttpClient 的 Ktor 客户端，避免回退到 Apache5。
     */
    @Test
    fun `should create koog http client backed by ktor java engine`() {
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

        val ktorClient = assertIs<KtorKoogHttpClient>(client)
        assertEquals("test", ktorClient.clientName)
        assertTrue(ktorClient.ktorClient.engine::class.qualifiedName.orEmpty().contains(".java.", ignoreCase = true))
    }
}
