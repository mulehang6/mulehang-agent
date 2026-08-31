package com.agent.app.platform

import com.agent.shared.session.DesktopTerminalPreferences
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证 Windows 内嵌终端的 Shell 检测、启动命令和不可用回退。 */
class DesktopTerminalShellsTest {

    /** 系统固定位置应先于 PATH 结果加入候选列表，且检测范围不包含 WSL。 */
    @Test
    fun `should prioritize native shell locations without adding WSL`() {
        val pathPowerShell = Path.of("D:/Tools/powershell.exe")
        val candidates = terminalShellCandidates(
            environment = mapOf(
                "SystemRoot" to "C:/Windows",
                "ProgramFiles" to "C:/Program Files",
                "ComSpec" to "C:/Windows/System32/cmd.exe",
                "USERPROFILE" to "C:/Users/tester",
            ),
            findOnPath = { fileName ->
                when (fileName) {
                    "powershell.exe" -> listOf(pathPowerShell)
                    "pwsh.exe" -> listOf(Path.of("D:/Tools/pwsh.exe"))
                    "git.exe" -> listOf(Path.of("D:/Git/cmd/git.exe"))
                    "nu.exe" -> listOf(Path.of("D:/Tools/nu.exe"))
                    else -> emptyList()
                }
            },
        )

        assertEquals(
            Path.of("C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe"),
            candidates.getValue(DesktopTerminalShell.WINDOWS_POWERSHELL).first(),
        )
        assertEquals(
            pathPowerShell,
            candidates.getValue(DesktopTerminalShell.WINDOWS_POWERSHELL).last(),
        )
        assertTrue(candidates.getValue(DesktopTerminalShell.GIT_BASH).contains(Path.of("D:/Git/bin/bash.exe")))
        assertFalse(candidates.values.flatten().any { path -> path.toString().contains("wsl", ignoreCase = true) })
    }

    /** Shell 目录需按产品固定顺序保留第一个可用路径，并去除同一路径的重复类型。 */
    @Test
    fun `should preserve shell detection order and deduplicate executables`() {
        val sharedPowerShellPath = Path.of("C:/Shells/powershell.exe")
        val catalog = createTerminalShellCatalog(
            candidatesByShell = mapOf(
                DesktopTerminalShell.WINDOWS_POWERSHELL to listOf(sharedPowerShellPath),
                DesktopTerminalShell.POWERSHELL_7 to listOf(
                    sharedPowerShellPath,
                    Path.of("C:/Shells/pwsh.exe"),
                ),
                DesktopTerminalShell.COMMAND_PROMPT to listOf(Path.of("C:/Windows/System32/cmd.exe")),
                DesktopTerminalShell.GIT_BASH to listOf(Path.of("C:/Git/bin/bash.exe")),
                DesktopTerminalShell.MSYS2_BASH to listOf(Path.of("C:/msys64/usr/bin/bash.exe")),
                DesktopTerminalShell.CYGWIN_BASH to listOf(Path.of("C:/cygwin64/bin/bash.exe")),
                DesktopTerminalShell.NUSHELL to listOf(Path.of("C:/Tools/nu.exe")),
            ),
            isUsableExecutable = { true },
        )

        assertEquals(
            listOf(
                DesktopTerminalShell.WINDOWS_POWERSHELL,
                DesktopTerminalShell.POWERSHELL_7,
                DesktopTerminalShell.COMMAND_PROMPT,
                DesktopTerminalShell.GIT_BASH,
                DesktopTerminalShell.MSYS2_BASH,
                DesktopTerminalShell.CYGWIN_BASH,
                DesktopTerminalShell.NUSHELL,
            ),
            catalog.availableShells.map { descriptor -> descriptor.shell },
        )
    }

    /** 每种 Shell 的启动模板必须使用匹配的交互参数。 */
    @Test
    fun `should build native shell command templates`() {
        assertEquals(
            listOf("-NoLogo"),
            TerminalShellDescriptor(
                shell = DesktopTerminalShell.POWERSHELL_7,
                executable = Path.of("C:/Tools/pwsh.exe"),
            ).launchCommand().drop(1),
        )
        assertEquals(
            listOf("--login", "-i"),
            TerminalShellDescriptor(
                shell = DesktopTerminalShell.GIT_BASH,
                executable = Path.of("C:/Git/bin/bash.exe"),
            ).launchCommand().drop(1),
        )
        assertEquals(
            emptyList(),
            TerminalShellDescriptor(
                shell = DesktopTerminalShell.COMMAND_PROMPT,
                executable = Path.of("C:/Windows/System32/cmd.exe"),
            ).launchCommand().drop(1),
        )
        assertFalse(DesktopTerminalShell.entries.any { shell -> shell.storageId.contains("wsl", ignoreCase = true) })
    }

    /** 已保存但不可用的 Shell 必须保留其标识，并只对新终端回退到旧版 Windows PowerShell。 */
    @Test
    fun `should fall back to windows powershell without overwriting unavailable preference`() {
        val catalog = createTerminalShellCatalog(
            candidatesByShell = mapOf(
                DesktopTerminalShell.COMMAND_PROMPT to listOf(Path.of("C:/Windows/System32/cmd.exe")),
            ),
            isUsableExecutable = { true },
        )
        val preferences = DesktopTerminalPreferences(defaultShellId = DesktopTerminalShell.POWERSHELL_7.storageId)

        val resolved = catalog.resolve(preferences)

        assertFalse(resolved.preferredShellAvailable)
        assertEquals(DesktopTerminalShell.WINDOWS_POWERSHELL, resolved.descriptor.shell)
        assertEquals(listOf("powershell.exe", "-NoLogo"), resolved.descriptor.launchCommand())
        assertEquals(DesktopTerminalShell.POWERSHELL_7.storageId, preferences.defaultShellId)
    }
}
