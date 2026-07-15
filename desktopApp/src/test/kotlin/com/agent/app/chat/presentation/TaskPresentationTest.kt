package com.agent.app.chat.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 验证新任务工作区选择规则。
 */
class TaskPresentationTest {

    /**
     * 侧栏应同时保留“沿用当前工作区新建线程”和“强制选择新工作区”两条路径。
     */
    @Test
    fun `should keep both current workspace and directory picker task creation paths`() {
        var pickerCalls = 0
        val pickedWorkspace = "D:\\repo\\new-workspace"

        val currentWorkspaceResult = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = "D:\\repo\\current",
            forceDirectoryPicker = false,
        ) {
            pickerCalls += 1
            pickedWorkspace
        }
        val pickedWorkspaceResult = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = "D:\\repo\\current",
            forceDirectoryPicker = true,
        ) {
            pickerCalls += 1
            pickedWorkspace
        }
        val fallbackWorkspaceResult = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = null,
            forceDirectoryPicker = false,
        ) {
            pickerCalls += 1
            pickedWorkspace
        }
        val cancelledWorkspaceResult = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = "D:\\repo\\current",
            forceDirectoryPicker = true,
        ) {
            pickerCalls += 1
            null
        }

        assertEquals("D:\\repo\\current", currentWorkspaceResult)
        assertEquals(pickedWorkspace, pickedWorkspaceResult)
        assertEquals(pickedWorkspace, fallbackWorkspaceResult)
        assertNull(cancelledWorkspaceResult)
        assertEquals(3, pickerCalls)
    }
}
