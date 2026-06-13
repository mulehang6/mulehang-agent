package com.agent.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.shared.agent.AgentRunRequest
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.agent.ReasoningEffort
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ModelCapabilitiesResolver
import com.agent.shared.state.AppError
import com.agent.shared.state.ChatMessage
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.state.ConversationItem
import com.agent.shared.state.ConversationState
import com.agent.shared.state.ExecutionState
import com.agent.shared.state.ReasoningItem
import com.agent.shared.state.ToolEventItem
import com.agent.shared.state.ToolEventStatus
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 附件在 composer 中的展示状态。
 */
data class ChatAttachmentUiState(
    val path: String,
    val name: String,
)

/**
 * 单个对话线程的窗口级展示状态。
 */
data class ChatConversationUiState(
    val id: String,
    val title: String,
    val workspacePath: String,
    val items: List<ConversationItem> = emptyList(),
    val attachments: List<ChatAttachmentUiState> = emptyList(),
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    val executionState: ExecutionState = ExecutionState.Idle,
    val streamingAssistantItemIndex: Int? = null,
    val streamingReasoningItemIndex: Int? = null,
    val contextUsageFraction: Float = 0.72f,
) {
    /**
     * 将对话线程折叠为旧的会话状态模型，兼容现有测试和渲染辅助函数。
     */
    fun toConversationState(activeProfileId: String?): ConversationState = ConversationState(
        items = items,
        executionState = executionState,
        activeProfileId = activeProfileId,
        streamingAssistantItemIndex = streamingAssistantItemIndex,
        streamingReasoningItemIndex = streamingReasoningItemIndex,
    )
}

/**
 * 同一工作目录下的对话分组。
 */
data class WorkspaceConversationGroupUiState(
    val workspacePath: String,
    val label: String,
    val conversations: List<ChatConversationUiState>,
)

/**
 * composer 权限档位。
 */
enum class PermissionPreset {
    AUTO,
    DEFAULT,
    EDIT_ALLOW,
    PLAN,
    BRAVE,
}

/**
 * 整个聊天窗口的 UI 状态。
 */
data class ChatWindowUiState(
    val workspaceGroups: List<WorkspaceConversationGroupUiState>,
    val activeConversationId: String,
    val draft: String = "",
    val selectedProfileId: String? = null,
    val permissionPreset: PermissionPreset = PermissionPreset.DEFAULT,
) {
    /**
     * 当前激活的对话线程。
     */
    val activeConversation: ChatConversationUiState
        get() = workspaceGroups
            .asSequence()
            .flatMap { it.conversations.asSequence() }
            .first { it.id == activeConversationId }

    /**
     * 当前激活线程所属的工作目录标签。
     */
    val activeWorkspaceLabel: String
        get() = workspaceGroups.first { group ->
            group.conversations.any { it.id == activeConversationId }
        }.label
}

/**
 * 窗口级状态持有者，负责多会话、composer 控件和流式消息归并。
 */
class ChatWindowState(
    private val sendMessageUseCase: SendMessageUseCase,
    private val snapshot: AppSessionSnapshot,
    projectPath: String = "",
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 当前窗口可选的全部 profile。
     */
    val availableProfiles: List<ConfigProfile>
        get() = snapshot.profiles

    /**
     * 当前窗口的完整 UI 状态。
     */
    var ui by mutableStateOf(
        initialUiState(snapshot = snapshot, projectPath = projectPath),
    )
        private set

    /**
     * 兼容旧渲染逻辑的活动会话状态投影。
     */
    val state: ConversationState
        get() = ui.activeConversation.toConversationState(activeProfile?.id)

    /**
     * 当前激活 profile。
     */
    val activeProfile: ConfigProfile?
        get() = snapshot.profiles.firstOrNull { it.id == ui.selectedProfileId } ?: snapshot.activeProfile

    /**
     * 当前失败状态对应的 UI 可见错误文本。
     */
    val errorMessage: String?
        get() = (ui.activeConversation.executionState as? ExecutionState.Failed)?.error?.let { error ->
            "${error.title}: ${error.message}"
        }

    /**
     * 更新当前输入框草稿。
     */
    fun updateDraft(value: String) {
        ui = ui.copy(draft = value)
    }

    /**
     * 切换当前激活对话。
     */
    fun selectConversation(conversationId: String) {
        if (findConversationOrNull(conversationId) != null) {
            ui = ui.copy(activeConversationId = conversationId)
        }
    }

    /**
     * 在指定工作目录下新建对话并切换焦点。
     */
    fun createConversationForWorkspace(workspacePath: String) {
        val conversation = newConversation(workspacePath, activeContextWindow())
        val existingGroup = ui.workspaceGroups.firstOrNull { it.workspacePath == workspacePath }
        val updatedGroups = if (existingGroup == null) {
            ui.workspaceGroups + WorkspaceConversationGroupUiState(
                workspacePath = workspacePath,
                label = buildWorkspaceLabel(workspacePath),
                conversations = listOf(conversation),
            )
        } else {
            ui.workspaceGroups.map { group ->
                if (group.workspacePath == workspacePath) {
                    group.copy(conversations = listOf(conversation) + group.conversations)
                } else {
                    group
                }
            }
        }
        ui = ui.copy(
            workspaceGroups = updatedGroups,
            activeConversationId = conversation.id,
            draft = "",
        )
    }

    /**
     * 为当前会话挂载附件。
     */
    fun attachFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        mutateActiveConversation { conversation ->
            val attachments = conversation.attachments + paths.map { path ->
                ChatAttachmentUiState(
                    path = path,
                    name = path.substringAfterLast('\\').substringAfterLast('/'),
                )
            }
            conversation.copy(
                attachments = attachments.distinctBy { it.path },
                contextUsageFraction = estimateContextUsage(
                    items = conversation.items,
                    attachmentCount = attachments.size,
                    contextWindow = activeContextWindow(),
                ),
            )
        }
    }

    /**
     * 调整当前会话的权限档位。
     */
    fun updatePermission(permissionPreset: PermissionPreset) {
        ui = ui.copy(permissionPreset = permissionPreset)
    }

    /**
     * 切换当前 profile。
     */
    fun selectProfile(profileId: String) {
        if (snapshot.profiles.any { it.id == profileId }) {
            ui = ui.copy(selectedProfileId = profileId)
        }
    }

    /**
     * 调整当前活动会话的推理强度档位。
     */
    fun updateReasoningEffort(reasoningEffort: ReasoningEffort) {
        mutateActiveConversation { conversation ->
            conversation.copy(reasoningEffort = reasoningEffort)
        }
    }

    /**
     * 兼容旧调用方式的直接发送入口。
     */
    fun send(message: String) {
        updateDraft(message)
        sendDraft()
    }

    /**
     * 发送当前草稿，并把流式结果归入当前活动会话。
     */
    fun sendDraft() {
        val prompt = ui.draft.trim()
        if (prompt.isBlank()) return

        val profile = activeProfile
        if (profile == null) {
            mutateActiveConversation { conversation ->
                conversation.copy(
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

        val targetConversationId = ui.activeConversationId
        mutateConversation(targetConversationId) { conversation ->
            val nextItems = conversation.items + ChatMessageItem(ChatMessage(ChatRole.User, prompt))
                conversation.copy(
                    title = conversation.title.takeUnless { it == DEFAULT_CONVERSATION_TITLE }
                    ?: prompt.take(24).ifBlank { DEFAULT_CONVERSATION_TITLE },
                items = nextItems,
                attachments = emptyList(),
                executionState = ExecutionState.Running,
                streamingAssistantItemIndex = null,
                streamingReasoningItemIndex = null,
                contextUsageFraction = estimateContextUsage(
                    items = nextItems,
                    attachmentCount = 0,
                    contextWindow = contextWindowFor(profile),
                ),
            )
        }
        ui = ui.copy(draft = "")

        scope.launch {
            try {
                sendMessageUseCase(
                    AgentRunRequest(
                        prompt = prompt,
                        profile = profile,
                        reasoningEffort = supportedReasoningEffort(
                            profile = profile,
                            conversation = findConversation(targetConversationId),
                        ),
                    ),
                ).collect { event ->
                    applyAgentEvent(targetConversationId, event)
                }
            } catch (exception: Exception) {
                mutateConversation(targetConversationId) { conversation ->
                    conversation.copy(
                        executionState = ExecutionState.Failed(
                            AppError(
                                title = "发送失败",
                                message = exception.message ?: "执行过程中发生未知错误。",
                            ),
                        ),
                    )
                }
            }
        }
    }

    /**
     * 查找指定对话，供测试或布局辅助调用。
     */
    fun findConversation(conversationId: String): ChatConversationUiState =
        findConversationOrNull(conversationId)
            ?: error("Conversation $conversationId not found.")

    /**
     * 仅当当前 profile 支持所选档位时才将 reasoning effort 送入执行链路。
     */
    private fun supportedReasoningEffort(
        profile: ConfigProfile,
        conversation: ChatConversationUiState,
    ): ReasoningEffort? {
        val capabilities = ModelCapabilitiesResolver.resolve(profile)
        val variants = capabilities.variants.values
        return conversation.reasoningEffort.takeIf { effort ->
            variants.any { variant -> variant.reasoningEffort == effort }
        } ?: capabilities.defaultReasoningEffort
    }

    /**
     * 当前 profile 的上下文窗口；显式配置优先，其次使用 provider/model 默认能力。
     */
    private fun activeContextWindow(): Int? = activeProfile?.let(::contextWindowFor)

    /**
     * 解析指定 profile 的上下文窗口。
     */
    private fun contextWindowFor(profile: ConfigProfile): Int? =
        resolveContextWindow(profile)

    /**
     * 将 agent 事件应用到指定活动会话。
     */
    private fun applyAgentEvent(conversationId: String, event: AgentStreamEvent) {
        when (event) {
            AgentStreamEvent.Started -> mutateConversation(conversationId) { conversation ->
                conversation.copy(executionState = ExecutionState.Running)
            }

            is AgentStreamEvent.TextDelta -> mutateConversation(conversationId) { conversation ->
                appendAssistantDelta(conversation, event.text)
            }

            is AgentStreamEvent.ToolCallStarted -> mutateConversation(conversationId) { conversation ->
                appendToolEvent(
                    conversation = conversation,
                    toolName = event.name,
                    status = ToolEventStatus.Started,
                    preview = event.argumentsPreview,
                )
            }

            is AgentStreamEvent.ToolCallFinished -> mutateConversation(conversationId) { conversation ->
                appendToolEvent(
                    conversation = conversation,
                    toolName = event.name,
                    status = ToolEventStatus.Finished,
                    preview = event.resultPreview,
                )
            }

            is AgentStreamEvent.Status -> mutateConversation(conversationId) { conversation ->
                appendToolEvent(
                    conversation = conversation,
                    toolName = "status",
                    status = ToolEventStatus.Status,
                    preview = event.message,
                )
            }

            is AgentStreamEvent.ReasoningDelta -> mutateConversation(conversationId) { conversation ->
                appendReasoningDelta(
                    conversation = conversation,
                    summary = event.summary,
                    rawText = event.rawText,
                )
            }

            is AgentStreamEvent.ReasoningCompleted -> mutateConversation(conversationId) { conversation ->
                completeReasoning(
                    conversation = conversation,
                    summary = event.summary,
                    rawText = event.rawText,
                )
            }

            is AgentStreamEvent.Completed -> mutateConversation(conversationId) { conversation ->
                completeAssistantMessage(conversation, event.text)
            }

            is AgentStreamEvent.Failed -> mutateConversation(conversationId) { conversation ->
                val closedConversation = closeStreamingReasoning(conversation)
                closedConversation.copy(
                    executionState = ExecutionState.Failed(
                        AppError(
                            title = "Agent 执行失败",
                            message = event.reason,
                        ),
                    ),
                    streamingAssistantItemIndex = null,
                    streamingReasoningItemIndex = null,
                )
            }
        }
    }

    /**
     * 在指定对话上执行原子更新。
     */
    private fun mutateConversation(
        conversationId: String,
        transform: (ChatConversationUiState) -> ChatConversationUiState,
    ) {
        ui = ui.copy(
            workspaceGroups = ui.workspaceGroups.map { group ->
                if (group.conversations.none { it.id == conversationId }) {
                    group
                } else {
                    group.copy(
                        conversations = group.conversations.map { conversation ->
                            if (conversation.id == conversationId) {
                                transform(conversation)
                            } else {
                                conversation
                            }
                        },
                    )
                }
            },
        )
    }

    /**
     * 更新当前活动会话。
     */
    private fun mutateActiveConversation(transform: (ChatConversationUiState) -> ChatConversationUiState) {
        mutateConversation(ui.activeConversationId, transform)
    }

    /**
     * 查找指定对话，如果不存在则返回空。
     */
    private fun findConversationOrNull(conversationId: String): ChatConversationUiState? = ui.workspaceGroups
        .asSequence()
        .flatMap { group -> group.conversations.asSequence() }
        .firstOrNull { it.id == conversationId }

    /**
     * 将文本增量拼接到当前正在生成的助手消息。
     */
    private fun appendAssistantDelta(
        conversation: ChatConversationUiState,
        delta: String,
    ): ChatConversationUiState {
        if (delta.isEmpty()) {
            return conversation
        }
        val normalizedConversation = closeStreamingReasoning(conversation)
        val currentIndex = normalizedConversation.streamingAssistantItemIndex
        return if (currentIndex == null) {
            val nextItems = normalizedConversation.items + ChatMessageItem(ChatMessage(ChatRole.Assistant, delta))
            normalizedConversation.copy(
                items = nextItems,
                streamingAssistantItemIndex = normalizedConversation.items.size,
                contextUsageFraction = estimateContextUsage(
                    items = nextItems,
                    attachmentCount = normalizedConversation.attachments.size,
                    contextWindow = activeContextWindow(),
                ),
            )
        } else {
            val existingItem = normalizedConversation.items[currentIndex] as? ChatMessageItem ?: return normalizedConversation
            val updatedItems = normalizedConversation.items.toMutableList()
            updatedItems[currentIndex] = existingItem.copy(
                message = existingItem.message.copy(content = existingItem.message.content + delta),
            )
            normalizedConversation.copy(items = updatedItems)
        }
    }

    /**
     * 将工具或状态事件追加到时间线。
     */
    private fun appendToolEvent(
        conversation: ChatConversationUiState,
        toolName: String,
        status: ToolEventStatus,
        preview: String?,
    ): ChatConversationUiState {
        val normalizedConversation = closeStreamingReasoning(conversation)
        val nextItems = normalizedConversation.items + ToolEventItem(
            toolName = toolName,
            status = status,
            preview = preview,
        )
        return normalizedConversation.copy(
            items = nextItems,
            contextUsageFraction = estimateContextUsage(
                items = nextItems,
                attachmentCount = normalizedConversation.attachments.size,
                contextWindow = activeContextWindow(),
            ),
        )
    }

    /**
     * 将思考增量拼接到当前思考块。
     */
    private fun appendReasoningDelta(
        conversation: ChatConversationUiState,
        summary: String?,
        rawText: String?,
    ): ChatConversationUiState {
        if (summary.isNullOrEmpty() && rawText.isNullOrEmpty()) {
            return conversation
        }
        val currentIndex = conversation.streamingReasoningItemIndex
        return if (currentIndex == null) {
            val nextItems = conversation.items + ReasoningItem(
                summaryText = summary,
                rawText = rawText ?: summary,
                expanded = true,
                isStreaming = true,
            )
            conversation.copy(
                items = nextItems,
                streamingReasoningItemIndex = conversation.items.size,
                contextUsageFraction = estimateContextUsage(
                    items = nextItems,
                    attachmentCount = conversation.attachments.size,
                    contextWindow = activeContextWindow(),
                ),
            )
        } else {
            val existingItem = conversation.items[currentIndex] as? ReasoningItem ?: return conversation
            val updatedItems = conversation.items.toMutableList()
            updatedItems[currentIndex] = existingItem.copy(
                summaryText = existingItem.summaryText.orEmpty().appendNullable(summary),
                rawText = existingItem.rawText.orEmpty().appendNullable(rawText ?: summary),
                expanded = true,
                isStreaming = true,
            )
            conversation.copy(items = updatedItems)
        }
    }

    /**
     * 收到 reasoning 完整事件后收尾当前思考块。
     */
    private fun completeReasoning(
        conversation: ChatConversationUiState,
        summary: String?,
        rawText: String?,
    ): ChatConversationUiState {
        val currentIndex = conversation.streamingReasoningItemIndex ?: run {
            val nextItems = conversation.items + ReasoningItem(
                summaryText = summary,
                rawText = rawText ?: summary,
                expanded = true,
                isStreaming = false,
            )
            return conversation.copy(
                items = nextItems,
                contextUsageFraction = estimateContextUsage(
                    items = nextItems,
                    attachmentCount = conversation.attachments.size,
                    contextWindow = activeContextWindow(),
                ),
            )
        }
        val existingItem = conversation.items[currentIndex] as? ReasoningItem ?: return conversation
        val updatedItems = conversation.items.toMutableList()
        updatedItems[currentIndex] = existingItem.copy(
            summaryText = summary ?: existingItem.summaryText,
            rawText = rawText ?: existingItem.rawText,
            expanded = true,
            isStreaming = false,
        )
        return conversation.copy(
            items = updatedItems,
            streamingReasoningItemIndex = null,
        )
    }

    /**
     * 在完成时补齐最终正文，并清理流式状态。
     */
    private fun completeAssistantMessage(
        conversation: ChatConversationUiState,
        finalText: String,
    ): ChatConversationUiState {
        val normalizedConversation = closeStreamingReasoning(conversation)
        val currentIndex = normalizedConversation.streamingAssistantItemIndex ?: run {
            val nextItems = appendCompletedAssistantIfNeeded(normalizedConversation.items, finalText)
            return normalizedConversation.copy(
                items = nextItems,
                executionState = ExecutionState.Idle,
                streamingAssistantItemIndex = null,
                contextUsageFraction = estimateContextUsage(
                    items = nextItems,
                    attachmentCount = normalizedConversation.attachments.size,
                    contextWindow = activeContextWindow(),
                ),
            )
        }
        val existingItem = normalizedConversation.items[currentIndex] as? ChatMessageItem ?: return normalizedConversation.copy(
            executionState = ExecutionState.Idle,
            streamingAssistantItemIndex = null,
        )
        val updatedItems = normalizedConversation.items.toMutableList()
        if (finalText.isNotBlank() && existingItem.message.content != finalText) {
            updatedItems[currentIndex] = existingItem.copy(
                message = existingItem.message.copy(content = finalText),
            )
        }
        return normalizedConversation.copy(
            items = updatedItems,
            executionState = ExecutionState.Idle,
            streamingAssistantItemIndex = null,
            contextUsageFraction = estimateContextUsage(
                items = updatedItems,
                attachmentCount = normalizedConversation.attachments.size,
                contextWindow = activeContextWindow(),
            ),
        )
    }

    /**
     * 当底层只返回完成文本时补一条助手消息。
     */
    private fun appendCompletedAssistantIfNeeded(
        items: List<ConversationItem>,
        finalText: String,
    ): List<ConversationItem> {
        if (finalText.isBlank()) {
            return items
        }
        return items + ChatMessageItem(ChatMessage(ChatRole.Assistant, finalText))
    }

    /**
     * 在进入工具或正文阶段前关闭仍处于流式中的思考块。
     */
    private fun closeStreamingReasoning(source: ChatConversationUiState): ChatConversationUiState {
        val reasoningIndex = source.streamingReasoningItemIndex ?: return source
        val reasoningItem = source.items[reasoningIndex] as? ReasoningItem ?: return source.copy(
            streamingReasoningItemIndex = null,
        )
        if (!reasoningItem.isStreaming) {
            return source.copy(streamingReasoningItemIndex = null)
        }
        val updatedItems = source.items.toMutableList()
        updatedItems[reasoningIndex] = reasoningItem.copy(isStreaming = false, expanded = true)
        return source.copy(
            items = updatedItems,
            streamingReasoningItemIndex = null,
        )
    }
}

/**
 * 基于当前项目路径和 profile 快照建立初始 UI 状态。
 */
private fun initialUiState(
    snapshot: AppSessionSnapshot,
    projectPath: String,
): ChatWindowUiState {
    val workspacePath = projectPath.ifBlank { "Unknown Workspace" }
    val initialConversation = newConversation(
        workspacePath = workspacePath,
        contextWindow = snapshot.activeProfile?.let(::resolveContextWindow),
    )
    return ChatWindowUiState(
        workspaceGroups = listOf(
            WorkspaceConversationGroupUiState(
                workspacePath = workspacePath,
                label = buildWorkspaceLabel(workspacePath),
                conversations = listOf(initialConversation),
            ),
        ),
        activeConversationId = initialConversation.id,
        selectedProfileId = snapshot.activeProfile?.id ?: snapshot.profiles.firstOrNull()?.id,
    )
}

/**
 * 创建一条空白会话。
 */
private fun newConversation(
    workspacePath: String,
    contextWindow: Int?,
): ChatConversationUiState = ChatConversationUiState(
    id = UUID.randomUUID().toString(),
    title = DEFAULT_CONVERSATION_TITLE,
    workspacePath = workspacePath,
    contextUsageFraction = estimateContextUsage(
        items = emptyList(),
        attachmentCount = 0,
        contextWindow = contextWindow,
    ),
)

/**
 * 解析 profile 的上下文窗口；profile 显式配置优先，其次使用模型能力默认值。
 */
private fun resolveContextWindow(profile: ConfigProfile): Int? =
    profile.limit?.context ?: ModelCapabilitiesResolver.resolve(profile).limit?.context

/**
 * 依据已有消息和附件粗略估计上下文占用比例；没有 context 窗口时回退为 0。
 */
private fun estimateContextUsage(
    items: List<ConversationItem>,
    attachmentCount: Int,
    contextWindow: Int?,
): Float {
    val window = contextWindow?.takeIf { it > 0 } ?: return 0f
    val estimatedTokens = items.sumOf(::estimateTokens) + attachmentCount * ATTACHMENT_TOKEN_ESTIMATE
    return (estimatedTokens.toFloat() / window).coerceIn(0f, 1f)
}

/**
 * 粗略估算单个时间线项的 token 数，后续可被 provider usage 或 tokenizer 替换。
 */
private fun estimateTokens(item: ConversationItem): Int = when (item) {
    is ChatMessageItem -> estimateTextTokens(item.message.content)
    is ReasoningItem -> estimateTextTokens(item.rawText ?: item.displayText)
    is ToolEventItem -> estimateTextTokens(item.preview.orEmpty()) + estimateTextTokens(item.toolName)
}

/**
 * 使用常见的 4 字符约 1 token 经验值估算文本 token 数。
 */
private fun estimateTextTokens(text: String): Int =
    (text.length + CHARS_PER_TOKEN_ESTIMATE - 1) / CHARS_PER_TOKEN_ESTIMATE

/**
 * 仅在有值时追加文本片段。
 */
private fun String.appendNullable(next: String?): String = if (next.isNullOrEmpty()) this else this + next

/**
 * 新对话的默认标题。
 */
private const val DEFAULT_CONVERSATION_TITLE = "新对话"

private const val CHARS_PER_TOKEN_ESTIMATE = 4

private const val ATTACHMENT_TOKEN_ESTIMATE = 64
