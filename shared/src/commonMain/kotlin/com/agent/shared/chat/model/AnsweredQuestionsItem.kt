package com.agent.shared.chat.model

import com.agent.shared.tool.model.QuestionAnswer

/**
 * 时间线中已经提交给 Agent 的批量问答记录。
 */
data class AnsweredQuestionsItem(
    val answers: List<QuestionAnswer>,
) : ConversationItem {
    override val kind: ConversationItem.Kind = ConversationItem.Kind.Answers
}
