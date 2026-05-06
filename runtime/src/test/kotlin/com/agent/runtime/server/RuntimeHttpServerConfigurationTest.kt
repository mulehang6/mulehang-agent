package com.agent.runtime.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证 runtime HTTP server 会从环境变量解析 host、port 与 token 鉴权模式。
 */
class RuntimeHttpServerConfigurationTest {

    @Test
    fun `should use explicit host port and token auth from environment`() {
        val configuration = resolveRuntimeHttpServerConfiguration(
            mapOf(
                "MULEHANG_RUNTIME_HOST" to "127.0.0.1",
                "MULEHANG_RUNTIME_PORT" to "43125",
                "MULEHANG_RUNTIME_TOKEN" to "secret-token",
                "MULEHANG_RUNTIME_SERVER_VERSION" to "test",
            ),
        )

        assertEquals("127.0.0.1", configuration.host)
        assertEquals(43125, configuration.port)
        assertEquals("token", configuration.metadata.authMode)
        assertEquals("test", configuration.metadata.serverVersion)
        assertTrue(configuration.auth.isAuthorized("secret-token"))
        assertFalse(configuration.auth.isAuthorized("wrong-token"))
    }

    @Test
    fun `should fall back to local defaults when environment is missing`() {
        val configuration = resolveRuntimeHttpServerConfiguration(emptyMap())

        assertEquals("127.0.0.1", configuration.host)
        assertEquals(8080, configuration.port)
        assertEquals("disabled", configuration.metadata.authMode)
        assertTrue(configuration.auth.isAuthorized(null))
    }
}
