package com.agent.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证内嵌终端默认使用系统自带的 Windows PowerShell。
 */
class DesktopPlatformActionsTest {

    @Test
    fun `should use windows powershell by default`() {
        assertEquals(
            listOf("powershell.exe", "-NoLogo"),
            buildPowerShellCommand(),
        )
    }
}
