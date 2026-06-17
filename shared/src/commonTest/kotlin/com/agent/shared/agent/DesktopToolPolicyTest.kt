package com.agent.shared.agent

import com.agent.shared.state.PermissionPreset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证桌面工具权限矩阵。
 */
class DesktopToolPolicyTest {
    /**
     * `DEFAULT` 只自动放行只读工具。
     */
    @Test
    fun `default should auto allow read only`() {
        assertTrue(DesktopToolPolicy.canRunRead(PermissionPreset.DEFAULT))
        assertFalse(DesktopToolPolicy.canAutoApproveWrite(PermissionPreset.DEFAULT))
        assertFalse(DesktopToolPolicy.canAutoApproveExecute(PermissionPreset.DEFAULT))
    }

    /**
     * `EDIT_ALLOW` 自动放行工作区内写入，但不自动放行命令执行。
     */
    @Test
    fun `edit allow should auto approve write but not execute`() {
        assertTrue(DesktopToolPolicy.canAutoApproveWrite(PermissionPreset.EDIT_ALLOW))
        assertFalse(DesktopToolPolicy.canAutoApproveExecute(PermissionPreset.EDIT_ALLOW))
    }

    /**
     * `BRAVE` 自动放行命令执行。
     */
    @Test
    fun `brave should auto approve execute`() {
        assertTrue(DesktopToolPolicy.canAutoApproveExecute(PermissionPreset.BRAVE))
    }

    /**
     * `PLAN` 禁止写入和命令执行。
     */
    @Test
    fun `plan should deny write and execute`() {
        assertTrue(DesktopToolPolicy.isWriteDenied(PermissionPreset.PLAN))
        assertTrue(DesktopToolPolicy.isExecuteDenied(PermissionPreset.PLAN))
    }
}
