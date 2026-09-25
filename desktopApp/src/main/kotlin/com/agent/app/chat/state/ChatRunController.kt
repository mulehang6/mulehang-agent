package com.agent.app.chat.state

import com.agent.app.tool.interaction.ApprovalResponse
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.agent.resource.AgentCommandExpansion
import com.agent.shared.agent.resource.expandSlashCommand
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.settings.resolver.supportsImageInput
import com.agent.shared.agent.status.SavedAgentStatus
import com.agent.shared.tool.model.QuestionAnswer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import com.agent.shared.agent.api.AgentRunTiming
import com.agent.shared.agent.koog.isContextOverflowMessage
import java.util.UUID

/** 管理消息执行、流式事件及挂起交互的状态转换。 */
internal class ChatRunController(private val window: ChatWindowState) {
    private val pendingInteractions = ChatPendingInteractionController(window)
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
            val runConversationId = activeRunConversationId ?: ui.activeTaskId
            val runningJob = activeRunJob
            runningJob?.cancel()
            activeRunJob = null
            activeRunRequest = null
            pauseRequestedConversationId = null
            if (runningJob == null) activeRunConversationId = null
            clearPendingOwnership(runConversationId)
            interactionRequestRepository?.cancelPending(runConversationId)
            mutateConversation(runConversationId) { conversation ->
                if (conversation.executionState.isStoppable()) {
                    conversation.copy(
                        progressMessage = null,
                        executionState = ExecutionState.Idle,
                        pendingQuestion = null,
                        pendingApproval = null,
                        streamingAssistantEntryId = null,
                        streamingReasoningEntryId = null,
                    )
                } else {
                    conversation
                }
            }
        }
    }

    /** 回答当前挂起问题，并恢复同一轮执行。 */
    fun answerPendingQuestion(answer: String) = pendingInteractions.answerQuestion(answer)

    /** 提交当前批量问题的完整回答。 */
    fun answerPendingQuestions(answers: List<QuestionAnswer>) = pendingInteractions.answerQuestions(answers)

    /** 提交当前挂起审批。 */
    fun answerPendingApproval(response: ApprovalResponse) = pendingInteractions.answerApproval(response)

    /** 兼容既有二元审批调用。 */
    fun answerPendingApproval(approved: Boolean) = pendingInteractions.answerApproval(approved)

    /**
     * 发送当前草稿，并把流式结果归入当前活动会话。
     */
    fun sendDraft() {
        with(window) {
            val prompt = ui.draft.trim()
            if (prompt.isBlank()) return
            val sourceTarget = ui.activeTarget
            val existingConversation = ui.activeConversationOrNull
            val acceptedDraft = ComposerDraft(ui.draft, ui.draftSelectionStart, activeDraftAttachments)
            val workspacePath = existingConversation?.workspacePath ?: ui.newWorkspacePath
            fun rejectBeforeCreation(error: AppError) {
                if (existingConversation == null) ui = ui.copy(newConversationError = error)
                else mutateConversation(existingConversation.id) { current ->
                    current.copy(progressMessage = null, executionState = ExecutionState.Failed(error))
                }
            }
            if (workspacePath.isBlank()) {
                rejectBeforeCreation(AppError("未选择工作区", "请先选择工作目录。"))
                return
            }
            if (activeRunConversationId != null || resourceReloadInProgress) {
                rejectBeforeCreation(AppError("已有任务在执行", "请等待当前任务完成，或先停止当前任务再启动新的 task。"))
                return
            }
            if (existingConversation?.executionState == ExecutionState.Paused ||
                existingConversation?.executionState == ExecutionState.Interrupted
            ) {
                return
            }
            workspaceIssueForPath(workspacePath)?.let { message ->
                rejectBeforeCreation(AppError("工作目录不可用", message))
                return
            }
            val profile = existingConversation?.let(::profileForConversation) ?: activeProfile
            if (profile == null) {
                rejectBeforeCreation(AppError("缺少可用配置", "请先在 settings.json 中配置并启用至少一个 profile。"))
                return
            }

            val inputParts = buildOrderedDraftInputParts(
                draft = prompt,
                attachments = activeDraftAttachments,
            )
            if (inputParts.any { part -> part is UserInputPart.Image } && !profile.supportsImageInput()) {
                rejectBeforeCreation(AppError("当前模型不支持图片输入", "请切换到支持视觉输入的模型后再发送图像。"))
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

            val sourceConversation = existingConversation ?: newConversation(
                workspacePath = workspacePath,
                contextWindow = contextWindowFor(profile),
                profileId = profile.id,
                reasoningEffort = ui.newReasoningEffort,
                permissionPreset = ui.permissionPreset,
            ).copy(workspaceName = ui.tasks.firstOrNull { it.workspacePath == workspacePath }?.workspaceName)
            val targetConversationId = sourceConversation.id
            if (existingConversation == null) {
                ui = ui.copy(tasks = listOf(sourceConversation) + ui.tasks, activeTaskId = targetConversationId)
            }
            val requestHistory = sourceConversation.history
            val shouldGenerateConversationTitle = sourceConversation.title == DEFAULT_CONVERSATION_TITLE &&
                    sourceConversation.history.none { message -> message is AgentConversationHistoryMessage.User } &&
                    conversationTitleGenerator != null
            val reasoningEffort = supportedReasoningEffort(
                profile = profile,
                conversation = sourceConversation,
            )
            val userEntryId = UUID.randomUUID().toString()
            mutateConversation(targetConversationId, schedulePersistence = false) { conversation ->
                val nextItems = conversation.items + ChatMessageItem(ChatMessage(ChatRole.User, prompt))
                val titledConversation = conversation.copy(
                    title = conversation.title.takeUnless { it == DEFAULT_CONVERSATION_TITLE }
                        ?: buildConversationTitle(prompt),
                    titleState = if (shouldGenerateConversationTitle) {
                        ConversationTitleState.GENERATING
                    } else {
                        conversation.titleState
                    },
                )
                val withUserEntry = if (conversation.treeFormatVersion > 0) {
                    appendUserConversationEntry(
                        conversation = titledConversation,
                        prompt = prompt,
                        inputParts = inputParts,
                        entryId = userEntryId,
                        createdAt = clock(),
                    )
                } else {
                    titledConversation.copy(
                        items = nextItems,
                        history = conversation.history + AgentConversationHistoryMessage.User(
                            content = prompt,
                            inputParts = inputParts,
                        ),
                    )
                }
                withUserEntry.copy(
                    attachments = emptyList(),
                    providerContextUsageFraction = null,
                    progressMessage = null,
                    executionState = ExecutionState.Running,
                    streamingAssistantItemIndex = null,
                    streamingReasoningItemIndex = null,
                    streamingAssistantHistoryIndex = null,
                    streamingAssistantEntryId = null,
                    streamingReasoningEntryId = null,
                    contextUsageFraction = estimateContextUsage(
                        items = withUserEntry.items,
                        attachmentCount = 0,
                        contextWindow = contextWindowFor(profile),
                    ),
                )
            }
            consumeDraftAfterSend(sourceTarget)
            activeRunConversationId = targetConversationId
            val acceptedTasks = ui.tasks
            val traceId = UUID.randomUUID().toString()
            val runRequest = AgentRunRequest(
                traceId = traceId,
                userEntryId = userEntryId,
                contextUsageFraction = findConversationOrNull(targetConversationId)?.contextUsageFraction,
                contextWindow = contextWindowFor(profile),
                contextCompactionThresholdPercent = snapshot.contextCompactionThresholdPercent,
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
            )
            activeRunRequest = runRequest
            val timing = AgentRunTiming(traceId)
            timing.mark("message_accepted")
            activeRunJob = scope.launch {
                var turnPersisted = false
                try {
                    persistenceCoordinator?.saveAcceptedUserTurn(
                        tasks = acceptedTasks,
                        before = existingConversation,
                        conversationId = targetConversationId,
                        userEntryId = userEntryId,
                    )
                    turnPersisted = true
                    if (shouldGenerateConversationTitle &&
                        findConversationOrNull(targetConversationId)?.titleState == ConversationTitleState.GENERATING
                    ) {
                        requestConversationTitle(
                            conversationId = targetConversationId,
                            firstUserMessage = prompt,
                            profile = fasterProfileForInternalTask(profile, snapshot.fasterProfiles),
                        )
                    }
                    val runResources = timing.phase("resource_prepare", "正在准备资源…", { event ->
                        applyAgentEvent(targetConversationId, event)
                    }) { loadRunResourceSnapshot(sourceConversation.workspacePath) }
                    val preparedRequest = runRequest.copy(
                        runtimeResources = runResources.toRuntimeResources(),
                        hookSettings = runResources.hookSettings,
                    )
                    activeRunRequest = preparedRequest
                    sendMessageUseCase(preparedRequest).collect { event ->
                        applyAgentEvent(targetConversationId, event)
                    }
                } catch (_: CancellationException) {
                    clearPendingOwnership(targetConversationId)
                    mutateConversation(targetConversationId) { conversation ->
                        if (conversation.executionState.isStoppable()) {
                            conversation.copy(
                                progressMessage = null,
                                executionState = if (pauseRequestedConversationId == targetConversationId) {
                                    ExecutionState.Paused
                                } else {
                                    ExecutionState.Idle
                                },
                                pendingQuestion = null,
                                pendingApproval = null,
                            )
                        } else {
                            conversation
                        }
                    }
                } catch (exception: Exception) {
                    if (!turnPersisted) {
                        ui = ui.copy(tasks = if (existingConversation == null) {
                            ui.tasks.filterNot { it.id == targetConversationId }
                        } else {
                            ui.tasks.map { if (it.id == targetConversationId) existingConversation else it }
                        })
                        if (existingConversation == null) {
                            showNewConversation(workspacePath)
                            updateDraft(acceptedDraft.text, acceptedDraft.selectionStart)
                            updateDraftAttachments(acceptedDraft.attachments)
                        } else {
                            showExistingConversation(targetConversationId, acceptedDraft)
                        }
                        setPersistenceError("用户消息保存失败：${exception.message ?: "未知错误"}")
                        return@launch
                    }
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
                    attentionController.record(
                        targetConversationId,
                        AgentStreamEvent.Failed(exception.message ?: "执行过程中发生未知错误。"),
                    )
                } finally {
                    if (activeRunConversationId == targetConversationId) {
                        activeRunConversationId = null
                    }
                    if (activeRunJob === currentCoroutineContext().job) activeRunJob = null
                    if (pauseRequestedConversationId == targetConversationId) {
                        pauseRequestedConversationId = null
                    } else if (activeRunRequest?.sessionId == targetConversationId) {
                        activeRunRequest = null
                    }
                }
            }
        }
    }

    /**
     * 将 agent 事件应用到指定活动会话。
     */
    internal suspend fun applyAgentEvent(conversationId: String, event: AgentStreamEvent) {
        val shouldRefreshTodos =
            event is AgentStreamEvent.ToolCallFinished || event is AgentStreamEvent.ToolCallFailed ||
            event is AgentStreamEvent.ToolCallInterrupted
        val refreshedTodos = if (shouldRefreshTodos) {
            withContext(window.resourceDispatcher) { window.todoRepository?.list(conversationId).orEmpty() }
        } else {
            null
        }
        val contextOverflowFailure = (event as? AgentStreamEvent.Failed)
            ?.reason
            ?.let(::isContextOverflowMessage) == true
        val hasRecoveryCheckpoint = if (contextOverflowFailure) {
            withContext(window.resourceDispatcher) {
                window.recoveryRepository?.interruptedRun(conversationId)?.hasCheckpoint == true
            }
        } else {
            false
        }
        with(window) {
            if (event is AgentStreamEvent.StatusSnapshotUpdated) {
                mutateConversation(conversationId) { conversation ->
                    conversation.copy(agentStatus = SavedAgentStatus(event.snapshot, event.modelMessageText))
                }
                return
            }
            window.reportMcpResourceDiagnostic(event)
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
                val updated = applyConversationEntryEvent(
                    conversation = reduceAgentEvent(conversation, event, contextWindow),
                    event = event,
                    idFactory = { UUID.randomUUID().toString() },
                    clock = clock,
                )
                val recoverable = contextOverflowFailure && hasRecoveryCheckpoint
                val withRecovery = if (recoverable) updated.copy(executionState = ExecutionState.Interrupted) else updated
                val withActualUsage = withRecovery.providerContextUsageFraction?.let { actual ->
                    withRecovery.copy(contextUsageFraction = actual)
                } ?: withRecovery
                if (shouldRefreshTodos) {
                    withActualUsage.copy(agentTodos = refreshedTodos.orEmpty())
                } else {
                    withActualUsage
                }
            }
            attentionController.record(conversationId, event)
        }
    }

    /**
     * 当指定会话结束或失败后，清理挂起请求的归属记录。
     */
    fun clearPendingOwnership(conversationId: String) = window.clearPendingInteractionOwnership(conversationId)

}
