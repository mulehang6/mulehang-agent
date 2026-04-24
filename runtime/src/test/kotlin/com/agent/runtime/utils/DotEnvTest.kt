package com.agent.runtime.utils

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证测试工具里的 .env 解析逻辑。
 */
class DotEnvTest {

    @Test
    fun `should read simple dotenv entries and trim matching quotes`() {
        val dotEnvPath = Files.createTempFile("mulehang-agent", ".env")
        Files.writeString(
            dotEnvPath,
            """
            # comment
            OPENROUTER_API_KEY="sk-test"
            export OTHER_KEY='other-value'
            EMPTY_LINE_IGNORED=
            """.trimIndent(),
        )

        val values = readDotEnv(dotEnvPath)

        assertEquals("sk-test", values["OPENROUTER_API_KEY"])
        assertEquals("other-value", values["OTHER_KEY"])
        assertEquals("", values["EMPTY_LINE_IGNORED"])
    }
}
