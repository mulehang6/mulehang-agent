package com.agent.shared.agent.koog

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证注入给每轮 Koog Agent 的统一系统约束。 */
class AgentSystemPromptTest {

    /** 系统提示词要求模型直接用可见文本答复并产生可解析的 Markdown。 */
    @Test
    fun `should guide direct responses and well formed markdown`() {
        val prompt = agentSystemPrompt()

        assertTrue(prompt.contains("直接在回复正文中回答用户"))
        assertFalse(prompt.contains("say_to_user"))
        assertTrue(prompt.contains("标题井号后必须保留一个空格"))
        assertTrue(prompt.contains("```plantuml"))
        assertTrue(prompt.contains("优先使用 PlantUML"))
        assertTrue(prompt.contains("只有用户明确要求 Mermaid 时才使用 ```mermaid 围栏"))
        assertTrue(prompt.contains("# 工具与环境边界"))
        assertTrue(prompt.contains("不要把尚未完成的推测称为事实"))
        assertTrue(prompt.length > 2_000)
        assertTrue(agentSystemPromptEstimatedTokenCount() > 500)
    }
}
