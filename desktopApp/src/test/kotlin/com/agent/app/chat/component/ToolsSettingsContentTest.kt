package com.agent.app.chat.component

import com.agent.app.platform.DesktopTerminalShell
import com.agent.app.platform.createTerminalShellCatalog
import com.agent.shared.session.DEFAULT_DESKTOP_TERMINAL_SHELL_ID
import com.agent.shared.session.DesktopTerminalPreferences
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 验证全局工具设置的 Shell 选项与不可用偏好说明。 */
class ToolsSettingsContentTest {

    /** 缺失的已保存 Shell 应显示可用回退项，但不能改写持久化标识。 */
    @Test
    fun `should preserve unavailable shell preference while showing fallback selection`() {
        val catalog = createTerminalShellCatalog(
            candidatesByShell = mapOf(
                DesktopTerminalShell.WINDOWS_POWERSHELL to listOf(Path.of("C:/Windows/powershell.exe")),
                DesktopTerminalShell.COMMAND_PROMPT to listOf(Path.of("C:/Windows/cmd.exe")),
            ),
            isUsableExecutable = { true },
        )
        val preferences = DesktopTerminalPreferences(defaultShellId = DesktopTerminalShell.NUSHELL.storageId)
        val options = terminalShellOptions(catalog)

        assertEquals(0, terminalShellSelectionIndex(options, preferences))
        assertEquals(DEFAULT_DESKTOP_TERMINAL_SHELL_ID, options.first().storageId)
        val message = assertNotNull(unavailableTerminalShellMessage(preferences, catalog))
        assertEquals(true, message.contains("Nushell"))
        assertEquals(true, message.contains("Windows PowerShell"))
        assertEquals(DesktopTerminalShell.NUSHELL.storageId, preferences.defaultShellId)
    }
}
