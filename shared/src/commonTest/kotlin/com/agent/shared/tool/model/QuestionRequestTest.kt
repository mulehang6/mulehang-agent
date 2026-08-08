package com.agent.shared.tool.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证批量提问请求在进入交互层前的规整规则。
 */
class QuestionRequestTest {

    /** 空题目会被忽略，每题候选项去重后最多保留五项。 */
    @Test
    fun `normalizes prompts and caps choices at five`() {
        val prompts = normalizeQuestionPrompts(
            listOf(
                QuestionPrompt(" 目标 ", listOf("UI", "", "UI", "Bug", "Feature", "Review", "Extra")),
                QuestionPrompt("   ", listOf("ignored")),
                QuestionPrompt("语言", emptyList()),
            ),
        )

        assertEquals(
            listOf(
                QuestionPrompt("目标", listOf("UI", "Bug", "Feature", "Review", "Extra")),
                QuestionPrompt("语言", emptyList()),
            ),
            prompts,
        )
    }
}
