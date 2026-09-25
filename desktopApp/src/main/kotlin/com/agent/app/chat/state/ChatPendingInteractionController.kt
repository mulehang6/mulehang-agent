package com.agent.app.chat.state

import com.agent.app.tool.interaction.ApprovalResponse
import com.agent.shared.chat.attention.ConversationAttentionType
import com.agent.shared.chat.model.AnsweredQuestionsItem
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.QuestionAnswer
import com.agent.shared.tool.model.QuestionPrompt
import java.util.UUID

/** 持久化用户决定，再恢复等待中的工具或崩溃后的 Koog 检查点。 */
internal class ChatPendingInteractionController(private val window: ChatWindowState) {
    /** 回答当前挂起的单个问题。 */
    fun answerQuestion(answer: String) {
        val conversationId = window.resolvePendingQuestionConversationId() ?: return
        val pending = window.findConversation(conversationId).pendingQuestion ?: return
        val question = pending.effectiveQuestions.singleOrNull()?.question ?: return
        submitAnswers(listOf(QuestionAnswer(question, answer)), answer)
    }

    /** 一次提交批量问题的完整回答。 */
    fun answerQuestions(answers: List<QuestionAnswer>) {
        val toolResponse = answers.joinToString("\n\n") { answer ->
            "Question: ${answer.question}\nAnswer: ${answer.answer.trim()}"
        }
        submitAnswers(answers, toolResponse)
    }

    /** 先落库，之后才释放实时桥；无实时桥时从原检查点继续。 */
    private fun submitAnswers(answers: List<QuestionAnswer>, toolResponse: String) {
        with(window) {
            val conversationId = resolvePendingQuestionConversationId() ?: return
            val pending = findConversation(conversationId).pendingQuestion ?: return
            if (pending.effectiveQuestions.map(QuestionPrompt::question) != answers.map(QuestionAnswer::question) ||
                answers.any { it.answer.isBlank() }
            ) return
            val saved = interactionRequestRepository?.answer(pending.requestId, toolResponse) == true
            val live = toolInteractionCoordinator.submitQuestion(toolResponse)
            if (!live && !saved) return
            pendingQuestionConversationId = null
            mutateConversation(conversationId) { conversation ->
                appendAnswersConversationEntry(
                    conversation = conversation.copy(
                        items = conversation.items + AnsweredQuestionsItem(answers),
                        pendingQuestion = null,
                        progressMessage = null,
                        executionState = if (live) ExecutionState.Running else ExecutionState.Interrupted,
                    ),
                    answers = answers,
                    entryId = UUID.randomUUID().toString(),
                    createdAt = clock(),
                )
            }
            attentionController.resolve(conversationId, ConversationAttentionType.QUESTION)
            if (!live && ui.activeTaskId == conversationId) resumeActiveRun()
        }
    }

    /** 审批决定同样先持久化；拒绝在冷启动时直接终止原轮次。 */
    fun answerApproval(response: ApprovalResponse) {
        with(window) {
            val conversationId = resolvePendingApprovalConversationId() ?: return
            val pending = findConversation(conversationId).pendingApproval ?: return
            val approved = response != ApprovalResponse.REJECT_AND_STOP
            val saved = interactionRequestRepository?.answer(pending.requestId, response.name) == true
            val live = toolInteractionCoordinator.submitApproval(response)
            if (!live && !saved) return
            pendingApprovalConversationId = null
            attentionController.resolve(conversationId, ConversationAttentionType.APPROVAL)
            if (!approved) {
                if (live) cancelActiveRun()
                else {
                    recoveryRepository?.interruptedRun(conversationId)?.let { recoveryRepository.abandonRun(it.id) }
                    mutateConversation(conversationId) {
                        it.copy(pendingApproval = null, progressMessage = null, executionState = ExecutionState.Idle)
                    }
                }
                return
            }
            mutateConversation(conversationId) {
                it.copy(
                    pendingApproval = null,
                    progressMessage = null,
                    executionState = if (live) ExecutionState.Running else ExecutionState.Interrupted,
                )
            }
            if (!live && ui.activeTaskId == conversationId) resumeActiveRun()
        }
    }

    /** 兼容原有二元审批入口。 */
    fun answerApproval(approved: Boolean) = answerApproval(
        if (approved) ApprovalResponse.APPROVE_ONCE else ApprovalResponse.REJECT_AND_STOP,
    )
}
