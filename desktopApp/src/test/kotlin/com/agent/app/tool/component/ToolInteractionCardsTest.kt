package com.agent.app.tool.component

import com.agent.app.chat.state.PendingApprovalUiState
import com.agent.app.chat.state.PendingQuestionUiState
import com.agent.shared.tool.model.FileDiffLineKind
import com.agent.shared.tool.model.FileDiffLinePreview
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

        assertEquals(listOf("Pick one"), model.questions.map { it.question })
        assertEquals(listOf("A", "B", "C"), model.questions.single().options)
        assertTrue(model.allowFreeText)
    }

    /** 内置候选项最多五个，且全部题目回答后才允许一次提交。 */
    @Test
    fun `questionnaire caps built in choices and requires every answer`() {
        val answers = listOf("UI", "")

        assertEquals(
            listOf("1", "2", "3", "4", "5"),
            com.agent.shared.tool.model.normalizeQuestionPrompts(
                listOf(com.agent.shared.tool.model.QuestionPrompt("目标", listOf("1", "2", "3", "4", "5", "6"))),
            ).single().options,
        )
        assertEquals(false, canSubmitQuestionnaire(answers))
        assertEquals(true, canSubmitQuestionnaire(listOf("UI", "中文")))
    }

    /** 问卷在最后一个标签页前只提供下一题动作，且不会越过末题。 */
    @Test
    fun `questionnaire navigates between question tabs before final submission`() {
        assertEquals("Next", questionnaireActionLabel(activeIndex = 0, questionCount = 3))
        assertEquals("Submit answers", questionnaireActionLabel(activeIndex = 2, questionCount = 3))
        assertEquals(1, nextQuestionnaireTabIndex(activeIndex = 0, questionCount = 3))
        assertEquals(2, nextQuestionnaireTabIndex(activeIndex = 2, questionCount = 3))
    }

    /** 自由输入仅在去除空白后仍含内容时才能提交。 */
    @Test
    fun `question free text submit should require non blank content`() {
        assertEquals(false, canSubmitQuestionFreeText("   "))
        assertEquals(true, canSubmitQuestionFreeText("使用第一种方式"))
    }

    /** 提交内容在交给状态层前必须去除空白，并拒绝空回答。 */
    @Test
    fun `question free text submission should trim content and reject blanks`() {
        assertEquals(null, questionFreeTextSubmission("   "))
        assertEquals("使用第一种方式", questionFreeTextSubmission("  使用第一种方式  "))
    }

    /** 选择自己输入时必须立即形成选中态，并在切换预设后保留自定义草稿。 */
    @Test
    fun `custom answer should select immediately and preserve its draft`() {
        val customSelected = selectQuestionCustomAnswer(QuestionAnswerDraft())
        val typed = updateQuestionCustomAnswer(customSelected, "使用本地文件")
        val presetSelected = selectQuestionPresetAnswer(typed, "使用默认方案")
        val customRestored = selectQuestionCustomAnswer(presetSelected)

        assertEquals(QuestionAnswerMode.CUSTOM, customSelected.mode)
        assertEquals("", questionAnswerValue(customSelected))
        assertEquals("使用默认方案", questionAnswerValue(presetSelected))
        assertEquals(QuestionAnswerMode.CUSTOM, customRestored.mode)
        assertEquals("使用本地文件", questionAnswerValue(customRestored))
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

    /** 长上下文默认折叠，展开当前区段后应恢复所有编辑器行。 */
    @Test
    fun `editor diff folds and expands unchanged context runs`() {
        val lines = (1..8).map { number ->
            FileDiffLinePreview(FileDiffLineKind.CONTEXT, number, number, "line-$number")
        }

        val collapsed = editorDiffRows(lines)
        val expanded = editorDiffRows(lines, expandedContextRuns = setOf(0))

        assertEquals(7, collapsed.size)
        assertTrue(collapsed.any { it is EditorDiffRow.CollapsedContext && it.hiddenLineCount == 2 })
        assertEquals(8, expanded.size)
        assertTrue(expanded.all { it is EditorDiffRow.Line })
    }

    /** 新增和删除的文本内容不应带入 patch 协议的正负号前缀。 */
    @Test
    fun `editor diff keeps markers outside code content`() {
        val rows = editorDiffRows(
            listOf(
                FileDiffLinePreview(FileDiffLineKind.REMOVED, 3, null, "old value"),
                FileDiffLinePreview(FileDiffLineKind.ADDED, null, 3, "new value"),
            ),
        )

        assertEquals(
            listOf("old value", "new value"),
            rows.filterIsInstance<EditorDiffRow.Line>().map { it.line.content },
        )
    }

    /** apply_patch 的补丁载荷不能与编辑器 Diff 重复显示。 */
    @Test
    fun `apply patch approval hides raw payload`() {
        val model = buildApprovalCardModel(
            PendingApprovalUiState(
                requestId = "patch-1",
                toolName = "apply_patch",
                summary = "修改文件",
                targetPath = "src/App.kt",
                payloadPreview = "*** Begin Patch",
            ),
        )

        assertEquals(null, visibleApprovalPayload(model))
    }
}
