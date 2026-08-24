package com.agent.build.env

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

/**
 * [DotEnv] 的解析行为测试。
 */
class DotEnvTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    /** 验证普通、export 与引号包裹的值均可读取。 */
    @Test
    fun `reads supported assignment forms`() {
        val dotEnvFile = writeDotEnv(
            """
            # 本地配置
            JCEF_HOME = "C:/tools/jbr-with-jcef"
            export OTHER_VALUE='other'
            """.trimIndent(),
        )

        assertEquals("C:/tools/jbr-with-jcef", DotEnv.readValue(dotEnvFile, "JCEF_HOME"))
        assertEquals("other", DotEnv.readValue(dotEnvFile, "OTHER_VALUE"))
    }

    /** 验证空值、无效行与不存在的键不会产生配置值。 */
    @Test
    fun `ignores empty malformed and missing values`() {
        val dotEnvFile = writeDotEnv(
            """
            JCEF_HOME=
            malformed-line
            """.trimIndent(),
        )

        assertNull(DotEnv.readValue(dotEnvFile, "JCEF_HOME"))
        assertNull(DotEnv.readValue(dotEnvFile, "MISSING_VALUE"))
    }

    /** 在临时目录中创建待解析的 `.env` 文件。 */
    private fun writeDotEnv(content: String): java.io.File =
        Files.writeString(temporaryDirectory.resolve(".env"), content).toFile()
}
