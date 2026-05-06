package com.agent.runtime.server

import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * 验证运行时 classpath 上存在可输出控制台日志的 SLF4J provider。
 */
class LoggingBackendConfigurationTest {

    @Test
    fun `should not fallback to nop slf4j logger factory`() {
        val loggerFactoryClassName = LoggerFactory.getILoggerFactory()::class.qualifiedName.orEmpty()

        assertFalse(
            actual = loggerFactoryClassName.endsWith("NOPLoggerFactory"),
            message = "当前 SLF4J loggerFactory=$loggerFactoryClassName，日志会被静默丢弃。",
        )
    }

    @Test
    fun `should keep default console logging on standard output`() {
        val logbackXml = checkNotNull(javaClass.classLoader.getResourceAsStream("logback.xml")) {
            "找不到 logback.xml 资源。"
        }.use { input ->
            input.readAllBytes().toString(StandardCharsets.UTF_8)
        }

        assertFalse(
            actual = "<target>System.err</target>" in logbackXml,
            message = "默认 logback.xml 不应把所有运行模式都切到 stderr，否则 HTTP 控制台日志会被误标红。",
        )
    }

    @Test
    fun `should route cli host console logging to stderr for stdio compatibility`() {
        val logbackXml = checkNotNull(javaClass.classLoader.getResourceAsStream("logback-cli-host.xml")) {
            "找不到 logback-cli-host.xml 资源。"
        }.use { input ->
            input.readAllBytes().toString(StandardCharsets.UTF_8)
        }

        assertTrue(
            actual = "<target>System.err</target>" in logbackXml,
            message = "CLI host 专用 logback 配置未把控制台日志路由到 stderr，会污染 stdio host 的协议 stdout。",
        )
    }
}
