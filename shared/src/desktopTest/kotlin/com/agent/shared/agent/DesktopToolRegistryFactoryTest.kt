package com.agent.shared.agent

import com.agent.shared.state.PermissionPreset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证首批桌面工具是否都已注册。
 */
class DesktopToolRegistryFactoryTest {
    /**
     * 工厂创建的注册表应包含首批工具名集合。
     */
    @Test
    fun `registry should contain first batch tool names`() {
        val registry = DesktopToolRegistryFactory(
            workspacePath = "D:\\repo",
            permissionPreset = PermissionPreset.DEFAULT,
            interactionBridge = fakeBridge(),
        ).create()

        val names = registry.tools.map { it.name }.toSet()

        assertEquals(
            setOf(
                "read_file",
                "list_dir",
                "glob_files",
                "grep_code",
                "write_file",
                "edit_file",
                "run_powershell",
                "ask_user",
                "say_to_user",
                "exit",
            ),
            names,
        )
    }
}

internal fun fakeBridge(): DesktopToolInteractionBridge = object : DesktopToolInteractionBridge {
    override suspend fun requestQuestion(request: QuestionRequest): String = "answer"

    override suspend fun requestApproval(request: ApprovalRequest): Boolean = true
}
