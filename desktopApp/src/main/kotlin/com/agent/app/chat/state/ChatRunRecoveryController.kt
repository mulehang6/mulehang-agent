package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.koog.UnavailableAgentCheckpointException
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.conversationEntryPath
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

/** 暂停当前 Koog 执行，并从安全检查点或未开始工具的初始请求继续。 */
internal class ChatRunRecoveryController(private val window: ChatWindowState) {
    /** 立即取消当前请求和工具进程，保留原用户轮次及已完成的图节点。 */
    fun pauseActiveRun() {
        with(window) {
            val conversationId = activeRunConversationId ?: return
            val job = activeRunJob ?: return
            pauseRequestedConversationId = conversationId
            mutateConversation(conversationId) { conversation ->
                conversation.copy(
                    executionState = ExecutionState.Paused,
                    progressMessage = null,
                    pendingQuestion = null,
                    pendingApproval = null,
                    streamingAssistantItemIndex = null,
                    streamingReasoningItemIndex = null,
                    streamingAssistantHistoryIndex = null,
                    streamingAssistantEntryId = null,
                    streamingReasoningEntryId = null,
                )
            }
            job.cancel()
        }
    }

    /** 按关系化运行记录判断能否安全继续，恢复过程不要求用户再次确认。 */
    fun resumeActiveRun() {
        with(window) {
            val conversation = ui.activeConversationOrNull ?: return
            if (conversation.executionState != ExecutionState.Paused &&
                conversation.executionState != ExecutionState.Interrupted
            ) return
            if (activeRunConversationId != null || activeRunJob != null) return
            val baseRequest = activeRunRequest?.takeIf { it.sessionId == conversation.id }
                ?: buildRequestFromConversation(conversation)
                ?: run {
                    failResume(conversation.id, "找不到当前轮次的用户消息或模型配置。")
                    return
                }
            activeRunConversationId = conversation.id
            mutateConversation(conversation.id) { current ->
                current.copy(executionState = ExecutionState.Running, progressMessage = "正在检查恢复点…")
            }
            activeRunJob = scope.launch {
                try {
                    val target = withContext(resourceDispatcher) {
                        recoveryRepository?.interruptedRun(conversation.id)
                    }
                    val targetUserEntryId = target?.userEntryId
                    val matchingBaseRequest = if (targetUserEntryId != null &&
                        baseRequest.userEntryId != targetUserEntryId
                    ) {
                        if (conversation.treeFormatVersion == 0 && baseRequest.userEntryId.isBlank()) {
                            baseRequest.copy(userEntryId = targetUserEntryId)
                        } else {
                            throw UnavailableAgentCheckpointException("恢复点与当前用户消息不匹配。")
                        }
                    } else {
                        baseRequest
                    }
                    val request = when {
                        target == null -> matchingBaseRequest.copy(traceId = UUID.randomUUID().toString(), resumeRunId = null)
                        target.hasCheckpoint -> matchingBaseRequest.copy(traceId = target.id, resumeRunId = target.id)
                        target.hasToolCalls -> throw UnavailableAgentCheckpointException(
                            "运行曾启动工具，但没有可用检查点；为避免重复执行，当前轮次不能继续。",
                        )
                        else -> {
                            withContext(resourceDispatcher) { recoveryRepository?.abandonRun(target.id) }
                            matchingBaseRequest.copy(traceId = UUID.randomUUID().toString(), resumeRunId = null)
                        }
                    }
                    val resources = loadRunResourceSnapshot(conversation.workspacePath)
                    val prepared = request.copy(
                        runtimeResources = resources.toRuntimeResources(),
                        hookSettings = resources.hookSettings,
                    )
                    activeRunRequest = prepared
                    sendMessageUseCase(prepared).collect { event ->
                        runController.applyAgentEvent(conversation.id, event)
                    }
                } catch (_: CancellationException) {
                    mutateConversation(conversation.id) { current ->
                        current.copy(executionState = if (pauseRequestedConversationId == conversation.id) {
                            ExecutionState.Paused
                        } else {
                            ExecutionState.Idle
                        }, progressMessage = null)
                    }
                } catch (error: Exception) {
                    failResume(conversation.id, error.message ?: "恢复失败。")
                } finally {
                    if (activeRunConversationId == conversation.id) activeRunConversationId = null
                    if (activeRunJob === currentCoroutineContext().job) activeRunJob = null
                    if (pauseRequestedConversationId == conversation.id) {
                        pauseRequestedConversationId = null
                    } else {
                        activeRunRequest = null
                    }
                }
            }
        }
    }

    /** 在崩溃重开后由会话路径重新构造运行参数，检查点负责恢复 Koog 内部状态。 */
    private fun buildRequestFromConversation(conversation: ChatConversationUiState): AgentRunRequest? = with(window) {
        val profile = profileForConversation(conversation) ?: return null
        val userEntry = conversationEntryPath(conversation.entries, conversation.activeEntryId)
            .filterIsInstance<ConversationEntry.Message>()
            .lastOrNull { it.message.role == ChatRole.User }
        val lastUserIndex = conversation.history.indexOfLast { it is AgentConversationHistoryMessage.User }
        val lastUser = conversation.history.getOrNull(lastUserIndex) as? AgentConversationHistoryMessage.User
            ?: return null
        AgentRunRequest(
            prompt = lastUser.content,
            profile = profile,
            reasoningEffort = supportedReasoningEffort(profile, conversation),
            history = conversation.history.take(lastUserIndex),
            workspacePath = conversation.workspacePath,
            permissionPreset = conversation.permissionPreset,
            fasterProfile = snapshot.fasterProfiles[profile.providerId],
            sessionId = conversation.id,
            hookSettings = resourceSnapshot.hookSettings,
            inputParts = lastUser.inputParts,
            runtimeResources = resourceSnapshot.toRuntimeResources(),
            traceId = UUID.randomUUID().toString(),
            userEntryId = userEntry?.id.orEmpty(),
            contextUsageFraction = conversation.contextUsageFraction,
            contextWindow = contextWindowFor(profile),
            contextCompactionThresholdPercent = snapshot.contextCompactionThresholdPercent,
        )
    }

    /** 不兼容或损坏的检查点保留轨迹并显示不可恢复错误。 */
    private fun failResume(conversationId: String, reason: String) {
        window.mutateConversation(conversationId) { current ->
            current.copy(
                executionState = ExecutionState.Failed(AppError("无法继续当前运行", reason)),
                progressMessage = null,
            )
        }
        window.attentionController.record(conversationId, com.agent.shared.agent.api.AgentStreamEvent.Failed(reason))
    }
}
