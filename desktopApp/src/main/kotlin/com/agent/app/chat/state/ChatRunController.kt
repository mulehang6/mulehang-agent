package com.agent.app.chat.state

import com.agent.app.tool.interaction.ApprovalResponse
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.agent.resource.AgentCommandExpansion
import com.agent.shared.agent.resource.AgentResourceDiagnostic
import com.agent.shared.agent.resource.AgentResourceDiagnosticSeverity
import com.agent.shared.agent.resource.expandSlashCommand
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.AnsweredQuestionsItem
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.settings.resolver.supportsImageInput
import com.agent.shared.tool.model.QuestionAnswer
import com.agent.shared.tool.model.QuestionPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.agent.shared.agent.api.AgentRunTiming
import java.util.UUID

/** 管理消息执行、流式事件及挂起交互的状态转换。 */
internal class ChatRunController(private val window: ChatWindowState) {
    /**
     * 兼容旧调用方式的直接发送入口。
     */
    fun send(message: String) {
        with(window) {
            updateDraft(message)
            sendDraft()
        }
    }

    /**
     * 取消当前正在执行的轮次，并恢复到可继续输入的空闲态。
     */
    fun cancelActiveRun() {
        with(window) {
            activeRunJob?.cancel()
            activeRunJob = null
            clearPendingOwnership(ui.activeTaskId)
            mutateActiveConversation { conversation ->
                if (conversation.executionState.isStoppable()) {
                    conversation.copy(
                        progressMessage = null,
                        executionState = ExecutionState.Idle,
                        pendingQuestion = null,
                        pendingApproval = null,
                    )
                } else {
                    conversation
                }
            }
        }
    }

    /**
     * 回答当前挂起问题，并恢复同一轮 agent 执行。
     */
    fun answerPendingQuestion(answer: String) {
        with(window) {
            val targetConversationId = resolvePendingQuestionConversationId() ?: return
            val pending = findConversation(targetConversationId).pendingQuestion ?: return
            val question = pending.effectiveQuestions.singleOrNull()?.question ?: return
            submitPendingQuestionAnswers(
                answers = listOf(QuestionAnswer(question = question, answer = answer)),
                toolResponse = answer,
            )
        }
    }

    /**
     * 一次提交当前批量问题的完整回答，并恢复发起问题的同一轮 Agent。
     */
    fun answerPendingQuestions(answers: List<QuestionAnswer>) {
        submitPendingQuestionAnswers(
            answers = answers,
            toolResponse = formatQuestionAnswers(answers),
        )
    }

    /**
     * 写入问答记录、解除挂起并向等待中的工具调用提交指定文本结果。
     */
    private fun submitPendingQuestionAnswers(
        answers: List<QuestionAnswer>,
        toolResponse: String,
    ) {
        with(window) {
            val targetConversationId = resolvePendingQuestionConversationId() ?: return
            val pending = findConversation(targetConversationId).pendingQuestion ?: return
            if (!isCompleteQuestionAnswerSet(pending = pending, answers = answers)) return
            if (!toolInteractionCoordinator.submitQuestion(toolResponse)) return
            pendingQuestionConversationId = null
            mutateConversation(targetConversationId) { conversation ->
                conversation.copy(
                    items = conversation.items + AnsweredQuestionsItem(answers = answers),
                    pendingQuestion = null,
                    progressMessage = null,
                    executionState = ExecutionState.Running,
                )
            }
        }
    }

    /**
     * 验证答案必须按当前问卷顺序完整覆盖，且每项都包含非空文本。
     */
    private fun isCompleteQuestionAnswerSet(
        pending: PendingQuestionUiState,
        answers: List<QuestionAnswer>,
    ): Boolean {
        return pending.effectiveQuestions.map(QuestionPrompt::question) == answers.map(QuestionAnswer::question) &&
            answers.all { it.answer.isNotBlank() }
    }

    /**
     * 将批量回答编码为稳定的纯文本，供挂起中的 Agent 工具调用继续读取。
     */
    private fun formatQuestionAnswers(answers: List<QuestionAnswer>): String {
        return answers.joinToString("\n\n") { answer ->
        "Question: ${answer.question}\nAnswer: ${answer.answer.trim()}"
    }
    }

    /**
     * 提交当前挂起审批；拒绝时停止当前 agent 轮次，其余选择恢复同一轮执行。
     */
    fun answerPendingApproval(response: ApprovalResponse) {
        with(window) {
            if (!toolInteractionCoordinator.submitApproval(response)) return
            if (response == ApprovalResponse.REJECT_AND_STOP) {
                cancelActiveRun()
                return
            }
            val targetConversationId = resolvePendingApprovalConversationId() ?: return
            pendingApprovalConversationId = null
            mutateConversation(targetConversationId) { conversation ->
                conversation.copy(
                    pendingApproval = null,
                    progressMessage = null,
                    executionState = ExecutionState.Running,
                )
            }
        }
    }

    /**
     * 兼容既有二元审批调用。
     */
    fun answerPendingApproval(approved: Boolean) {
        with(window) {
            answerPendingApproval(
                if (approved) ApprovalResponse.APPROVE_ONCE else ApprovalResponse.REJECT_AND_STOP,
            )
        }
    }

    /**
     * 发送当前草稿，并把流式结果归入当前活动会话。
     */
    fun sendDraft() {
        with(window) {
            val prompt = ui.draft.trim()
            if (prompt.isBlank()) return

            if (ui.activeConversationOrNull == null) {
                ui = ui.copy(draft = prompt)
                return
            }

            val targetConversationId = ui.activeTaskId
            if (activeRunConversationId != null || resourceReloadInProgress) {
                mutateConversation(targetConversationId) { conversation ->
                    conversation.copy(
                        progressMessage = null,
                        executionState = ExecutionState.Failed(
                            AppError(
                                title = "已有任务在执行",
                                message = "请等待当前任务完成，或先停止当前任务再启动新的 task。",
                            ),
                        ),
                    )
                }
                return
            }

            val sourceConversation = findConversation(targetConversationId)
            workspaceIssue(sourceConversation)?.let { message ->
                mutateConversation(targetConversationId) { conversation ->
                    conversation.copy(
                        progressMessage = null,
                        executionState = ExecutionState.Failed(
                            AppError(
                                title = "工作目录不可用",
                                message = message,
                            ),
                        ),
                    )
                }
                return
            }
            val profile = profileForConversation(sourceConversation)
            if (profile == null) {
                mutateActiveConversation { conversation ->
                    conversation.copy(
                        progressMessage = null,
                        executionState = ExecutionState.Failed(
                            AppError(
                                title = "缺少可用配置",
                                message = "请先在 settings.json 中配置并启用至少一个 profile。",
                            ),
                        ),
                    )
                }
                return
            }

            val inputParts = buildOrderedDraftInputParts(
                draft = prompt,
                attachments = sourceConversation.attachments,
            )
            if (inputParts.any { part -> part is UserInputPart.Image } && !profile.supportsImageInput()) {
                mutateConversation(targetConversationId) { conversation ->
                    conversation.copy(
                        progressMessage = null,
                        executionState = ExecutionState.Failed(
                            AppError(
                                title = "当前模型不支持图片输入",
                                message = "请切换到支持视觉输入的模型后再发送图像。",
                            ),
                        ),
                    )
                }
                return
            }

            val runResources = resourceSnapshot
            when (val expansion = runResources.expandSlashCommand(prompt)) {
                AgentCommandExpansion.ReloadResources -> {
                    startResourceReload {
                        ui = ui.copy(draft = "")
                    }
                    return
                }

                is AgentCommandExpansion.InsertText -> {
                    ui = ui.copy(draft = expansion.text)
                    return
                }

                null -> Unit
            }

            val requestHistory = sourceConversation.history
            val shouldGenerateConversationTitle = sourceConversation.title == DEFAULT_CONVERSATION_TITLE &&
                    sourceConversation.history.none { message -> message is AgentConversationHistoryMessage.User } &&
                    conversationTitleGenerator != null
            val reasoningEffort = supportedReasoningEffort(
                profile = profile,
                conversation = sourceConversation,
            )
            mutateConversation(targetConversationId) { conversation ->
                val nextItems = conversation.items + ChatMessageItem(ChatMessage(ChatRole.User, prompt))
                conversation.copy(
                    title = conversation.title.takeUnless { it == DEFAULT_CONVERSATION_TITLE }
                        ?: buildConversationTitle(prompt),
                    titleState = if (shouldGenerateConversationTitle) {
                        ConversationTitleState.GENERATING
                    } else {
                        conversation.titleState
                    },
                    items = nextItems,
                    attachments = emptyList(),
                    history = conversation.history + AgentConversationHistoryMessage.User(
                        content = prompt,
                        inputParts = inputParts,
                    ),
                    progressMessage = null,
                    executionState = ExecutionState.Running,
                    streamingAssistantItemIndex = null,
                    streamingReasoningItemIndex = null,
                    streamingAssistantHistoryIndex = null,
                    contextUsageFraction = estimateContextUsage(
                        items = nextItems,
                        attachmentCount = 0,
                        contextWindow = contextWindowFor(profile),
                    ),
                )
            }
            ui = ui.copy(draft = "", draftSelectionStart = 0)
            if (shouldGenerateConversationTitle) {
                requestConversationTitle(
                    conversationId = targetConversationId,
                    firstUserMessage = prompt,
                    profile = fasterProfileForInternalTask(profile, snapshot.fasterProfiles),
                )
            }

            activeRunConversationId = targetConversationId
            val traceId = UUID.randomUUID().toString()
            val timing = AgentRunTiming(traceId)
            timing.mark("message_accepted")
            activeRunJob = scope.launch {
                try {
                    val runResources = timing.phase("resource_prepare", "正在准备资源…", { event ->
                        applyAgentEvent(targetConversationId, event)
                    }) { loadRunResourceSnapshot(sourceConversation.workspacePath) }
                    sendMessageUseCase(
                        AgentRunRequest(
                            traceId = traceId,
                            prompt = prompt,
                            profile = profile,
                            history = requestHistory,
                            inputParts = inputParts,
                            runtimeResources = runResources.toRuntimeResources(),
                            reasoningEffort = reasoningEffort,
                            workspacePath = sourceConversation.workspacePath,
                            permissionPreset = sourceConversation.permissionPreset,
                            fasterProfile = snapshot.fasterProfiles[profile.providerId],
                            sessionId = targetConversationId,
                            hookSettings = runResources.hookSettings,
                        ),
                    ).collect { event ->
                        applyAgentEvent(targetConversationId, event)
                    }
                } catch (_: CancellationException) {
                    clearPendingOwnership(targetConversationId)
                    mutateConversation(targetConversationId) { conversation ->
                        if (conversation.executionState.isStoppable()) {
                            conversation.copy(
                                progressMessage = null,
                                executionState = ExecutionState.Idle,
                                pendingQuestion = null,
                                pendingApproval = null,
                            )
                        } else {
                            conversation
                        }
                    }
                } catch (exception: Exception) {
                    mutateConversation(targetConversationId) { conversation ->
                        val reason = exception.message ?: "执行过程中发生未知错误。"
                        val withToolFailure = attachFailureToTimeline(
                            conversation = conversation,
                            reason = reason,
                            contextWindow = contextWindowForConversation(conversation),
                        )
                        withToolFailure.copy(
                            progressMessage = null,
                            executionState = ExecutionState.Failed(
                                AppError(
                                    title = "发送失败",
                                    message = reason,
                                ),
                            ),
                        )
                    }
                } finally {
                    if (activeRunConversationId == targetConversationId) {
                        activeRunConversationId = null
                    }
                    activeRunJob = null
                }
            }
        }
    }

    /**
     * 将 agent 事件应用到指定活动会话。
     */
    private fun applyAgentEvent(conversationId: String, event: AgentStreamEvent) {
        with(window) {
            reportMcpResourceDiagnostic(event)
            when (event) {
                is AgentStreamEvent.QuestionRequested -> {
                    pendingQuestionConversationId = conversationId
                    pendingApprovalConversationId = null
                }

                is AgentStreamEvent.ApprovalRequested -> {
                    pendingApprovalConversationId = conversationId
                    pendingQuestionConversationId = null
                }

                is AgentStreamEvent.Completed,
                is AgentStreamEvent.Failed,
                    -> clearPendingOwnership(conversationId)

                else -> Unit
            }
            val contextWindow = findConversationOrNull(conversationId)
                ?.let(::contextWindowForConversation)
                ?: activeContextWindow()
            mutateConversation(conversationId) { conversation ->
                reduceAgentEvent(conversation, event, contextWindow)
            }
        }
    }

    /** 将 MCP 连接与工具冲突诊断同步到扩展中心，避免只在时间线里短暂可见。 */
    private fun reportMcpResourceDiagnostic(event: AgentStreamEvent) {
        with(window) {
            val failure = event as? AgentStreamEvent.ToolCallFailed ?: return
            if (!failure.name.startsWith("MCP:")) return
            val diagnostic = AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "${failure.name.removePrefix("MCP:")}：${failure.reason}",
            )
            if (diagnostic !in runtimeResourceDiagnostics) {
                runtimeResourceDiagnostics += diagnostic
            }
        }
    }

    /**
     * 找到当前挂起问题所属的会话；记录缺失时退回到真正挂起该问题的线程。
     */
    private fun resolvePendingQuestionConversationId(): String? {
        return with(window) {
            pendingQuestionConversationId
                ?: ui.tasks.firstOrNull { it.pendingQuestion != null }?.id
        }
    }

    /**
     * 找到当前挂起审批所属的会话；记录缺失时退回到真正挂起该审批的线程。
     */
    private fun resolvePendingApprovalConversationId(): String? {
        return with(window) {
            pendingApprovalConversationId
                ?: ui.tasks.firstOrNull { it.pendingApproval != null }?.id
        }
    }

    /**
     * 当指定会话结束或失败后，清理挂起请求的归属记录。
     */
    fun clearPendingOwnership(conversationId: String) {
        with(window) {
            if (pendingQuestionConversationId == conversationId) {
                pendingQuestionConversationId = null
            }
            if (pendingApprovalConversationId == conversationId) {
                pendingApprovalConversationId = null
            }
        }
    }

}
