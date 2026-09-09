package com.agent.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    /** Windows Shell 只把大于 32 的返回值视为已接受默认关联程序的打开请求。 */
    @Test
    fun `should recognize successful shell execute results`() {
        assertFalse(shellExecuteSucceeded(0))
        assertFalse(shellExecuteSucceeded(32))
        assertTrue(shellExecuteSucceeded(33))
    }
}
