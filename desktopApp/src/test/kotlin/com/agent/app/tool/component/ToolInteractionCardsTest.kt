package com.agent.app.tool.component

import com.agent.app.chat.state.PendingApprovalUiState
import com.agent.app.chat.state.PendingQuestionUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证工具交互卡片的展示模型。
 */
class ToolInteractionCardsTest {
    /**
     * 问题卡片应暴露候选项和自由输入能力。
     */
    @Test
    fun `question card should show options and input field`() {
        val pending = PendingQuestionUiState(
            requestId = "q1",
            question = "Pick one",
            options = listOf("A", "B", "C"),
            allowFreeText = true,
        )

        val model = buildQuestionCardModel(pending)

        assertEquals("Pick one", model.title)
        assertEquals(listOf("A", "B", "C"), model.options)
        assertTrue(model.allowFreeText)
    }

    /** 自由输入仅在去除空白后仍含内容时才能提交。 */
    @Test
    fun `question free text submit should require non blank content`() {
        assertEquals(false, canSubmitQuestionFreeText("   "))
        assertEquals(true, canSubmitQuestionFreeText("使用第一种方式"))
    }

    /**
     * 审批卡片应保留工具摘要和目标路径。
     */
    @Test
    fun `approval card should expose tool summary and target path`() {
        val pending = PendingApprovalUiState(
            requestId = "a1",
            toolName = "write_file",
            summary = "写入工作区文件",
            targetPath = "E:\\repo\\notes.txt",
            payloadPreview = "hello",
        )

        val model = buildApprovalCardModel(pending)

        assertEquals("写入工作区文件", model.title)
        assertEquals("write_file", model.toolName)
        assertEquals("E:\\repo\\notes.txt", model.targetPath)
    }

    /**
     * PowerShell 审批卡必须将原始命令与操作意图拆开呈现。
     */
    @Test
    fun `powershell approval card should expose operation intent and raw command`() {
        val pending = PendingApprovalUiState(
            requestId = "terminal-1",
            toolName = "run_powershell",
            summary = "列出当前目录内容",
            targetPath = null,
            payloadPreview = "Get-ChildItem",
        )

        val model = buildApprovalCardModel(pending)

        assertEquals("列出当前目录内容", model.operationIntent)
        assertEquals("Get-ChildItem", model.rawCommand)
    }
}
