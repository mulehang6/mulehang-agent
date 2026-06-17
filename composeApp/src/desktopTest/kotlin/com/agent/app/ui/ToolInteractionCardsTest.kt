package com.agent.app.ui

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
}
