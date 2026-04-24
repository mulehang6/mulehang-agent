package com.agent.server

import org.slf4j.LoggerFactory
import kotlin.test.Test
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
}
