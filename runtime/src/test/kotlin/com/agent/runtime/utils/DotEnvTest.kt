package com.agent.runtime.utils

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `should resolve default dotenv by walking up parent directories`() {
        val rootDir = Files.createTempDirectory("mulehang-agent-root")
        val nestedDir = rootDir.resolve("runtime").resolve("build").resolve("tmp")
        nestedDir.createDirectories()
        val dotEnvPath = rootDir.resolve(".env")
        dotEnvPath.writeText(
            """
            OPENROUTER_API_KEY=sk-parent
            """.trimIndent(),
        )

        val resolved = resolveDotEnvPath(Path.of(".env"), nestedDir)
        val values = readDotEnv(resolved ?: Path.of(".env"))

        assertTrue(resolved != null)
        assertEquals(dotEnvPath, resolved)
        assertEquals("sk-parent", values["OPENROUTER_API_KEY"])
    }
}
