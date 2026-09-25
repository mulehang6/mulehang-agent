package com.agent.shared.session

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 验证统一数据库中的桌面 UI 状态及用户级终端偏好。 */
class DesktopUiStateStoreTest {
    /** 缺失缩放应采用默认值，任意输入都应落到受支持的离散档位。 */
    @Test
    fun `should normalize desktop ui scale percent`() {
        assertEquals(100, normalizeDesktopUiScalePercent(null))
        assertEquals(50, normalizeDesktopUiScalePercent(-1))
        assertEquals(50, normalizeDesktopUiScalePercent(54))
        assertEquals(60, normalizeDesktopUiScalePercent(55))
        assertEquals(130, normalizeDesktopUiScalePercent(126))
        assertEquals(200, normalizeDesktopUiScalePercent(999))
    }

    /** 外观偏好应规范化后保存，并可在数据库重开后恢复。 */
    @Test
    fun `should persist normalized appearance preferences`() {
        val databasePath = Files.createTempDirectory("mulehang-ui-appearance-test").resolve("mulehang.db")
        DesktopUiStateStore(databasePath).use { store ->
            store.saveAppearancePreferences(DesktopAppearancePreferences(126, "Inter", "JetBrains Mono"))
        }

        DesktopUiStateStore(databasePath).use { store ->
            assertEquals(
                DesktopAppearancePreferences(130, "Inter", "JetBrains Mono"),
                store.loadAppearancePreferences(),
            )
        }
    }

    /** 项目选择、最近工作区、主题和 Shell 应共享同一个版本化文档。 */
    @Test
    fun `should merge independent ui preferences`() {
        val databasePath = Files.createTempDirectory("mulehang-ui-state-test").resolve("mulehang.db")
        DesktopUiStateStore(databasePath).use { store ->
            store.saveSelectedProfile("D:/workspace/demo", "openai-main")
            store.saveRecentWorkspace("D:/workspace/demo")
            store.saveThemeMode("light")
            store.saveTerminalPreferences(DesktopTerminalPreferences("powershell-7"))
        }

        DesktopUiStateStore(databasePath).use { reopened ->
            assertEquals("openai-main", reopened.loadSelectedProfile("D:/workspace/demo"))
            assertEquals("D:/workspace/demo", reopened.loadRecentWorkspace())
            assertEquals("light", reopened.loadThemeMode())
            assertEquals("powershell-7", reopened.loadTerminalPreferences().defaultShellId)
        }
    }

    /** 旧 ui-state.json 不应被读取、改写或自动删除。 */
    @Test
    fun `should not migrate or delete legacy ui state`() {
        val root = Files.createTempDirectory("mulehang-ui-legacy-test")
        val legacyPath = root.resolve("ui-state.json")
        Files.writeString(legacyPath, """{"themeMode":"light"}""")

        DesktopUiStateStore(root.resolve("mulehang.db")).use { store ->
            assertEquals(null, store.loadThemeMode())
            assertEquals("""{"themeMode":"light"}""", Files.readString(legacyPath))
            assertFalse(Files.notExists(legacyPath))
        }
    }
}
