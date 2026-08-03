package com.agent.app.chat.persistence

import com.agent.app.chat.state.ChatAttachmentUiState
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationItem
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import com.agent.shared.chat.persistence.PersistedHistoryItem
import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.PersistedTimelineItem
import com.agent.shared.tool.model.PermissionPreset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 在桌面聊天展示状态与共享持久化快照之间显式转换全部密封类型。
 */
internal object ChatTaskSnapshotMapper {
    private val json = Json

    /**
     * 将一条桌面会话编码为数据库无关的任务快照。
     */
    fun toPersistedTask(source: ChatConversationUiState): PersistedTask = PersistedTask(
        id = source.id,
        title = source.title,
        workspacePath = source.workspacePath,
        reasoningEffort = source.reasoningEffort.name,
        profileId = source.profileId,
        permissionPreset = source.permissionPreset.name,
        contextUsageFraction = source.contextUsageFraction,
        executionState = source.executionState.persistenceType(),
        executionErrorTitle = (source.executionState as? ExecutionState.Failed)?.error?.title,
        executionErrorMessage = (source.executionState as? ExecutionState.Failed)?.error?.message,
        attachmentsJson = json.encodeToString(JsonArray(source.attachments.map(::encodeAttachment))),
        timeline = source.items.mapIndexed(::encodeTimeline),
        history = source.history.mapIndexed(::encodeHistory),
    )

    /**
     * 恢复一条会话；不可续跑的运行态统一转换为安全失败态。
     */
    fun toConversation(source: PersistedTask): ChatConversationUiState = ChatConversationUiState(
        id = source.id,
        title = source.title,
        workspacePath = source.workspacePath,
        items = source.timeline.sortedBy(PersistedTimelineItem::sequence).map(::decodeTimeline),
        attachments = json.parseToJsonElement(source.attachmentsJson).jsonArray.map(::decodeAttachment),
        history = source.history.sortedBy(PersistedHistoryItem::sequence).map(::decodeHistory),
        profileId = source.profileId,
        reasoningEffort = source.reasoningEffort.toReasoningEffort(),
        permissionPreset = source.permissionPreset.toPermissionPreset(),
        executionState = source.recoveredExecutionState(),
        streamingAssistantItemIndex = null,
        streamingReasoningItemIndex = null,
        streamingAssistantHistoryIndex = null,
        contextUsageFraction = source.contextUsageFraction,
        pendingQuestion = null,
        pendingApproval = null,
    )

    /** 将附件编码为对象，保留原始路径。 */
    private fun encodeAttachment(source: ChatAttachmentUiState): JsonObject = buildJsonObject {
        put("path", source.path)
        put("name", source.name)
    }

    /** 从持久化对象恢复附件。 */
    private fun decodeAttachment(source: JsonElement): ChatAttachmentUiState {
        val objectValue = source.jsonObject
        return ChatAttachmentUiState(
            path = objectValue.requiredString("path"),
            name = objectValue.requiredString("name"),
        )
    }

    /** 编码单个时间线项。 */
    private fun encodeTimeline(sequence: Int, source: ConversationItem): PersistedTimelineItem = when (source) {
        is ChatMessageItem -> PersistedTimelineItem(sequence, "message", buildJsonObject {
            put("role", source.message.role.name)
            put("content", source.message.content)
        }.toString())

        is ReasoningItem -> PersistedTimelineItem(sequence, "reasoning", buildJsonObject {
            putNullable("summaryText", source.summaryText)
            putNullable("rawText", source.rawText)
            put("expanded", source.expanded)
            put("isStreaming", source.isStreaming)
            put("startedAtMillis", source.startedAtMillis)
            putNullable("durationMillis", source.durationMillis)
        }.toString())

        is ToolEventItem -> PersistedTimelineItem(sequence, "tool_event", buildJsonObject {
            put("toolName", source.toolName)
            put("status", source.status.name)
            putNullable("preview", source.preview)
            putNullable("errorMessage", source.errorMessage)
            putNullable("operationIntent", source.operationIntent)
            putNullable("toolCallId", source.toolCallId)
            putNullable("resultPreview", source.resultPreview)
            putNullable("resultDisplay", source.resultDisplay)
        }.toString())
    }

    /** 解码单个时间线项。 */
    private fun decodeTimeline(source: PersistedTimelineItem): ConversationItem {
        val objectValue = json.parseToJsonElement(source.payloadJson).jsonObject
        return when (source.type) {
            "message" -> ChatMessageItem(ChatMessage(ChatRole.valueOf(objectValue.requiredString("role")), objectValue.requiredString("content")))
            "reasoning" -> ReasoningItem(
                summaryText = objectValue.optionalString("summaryText"),
                rawText = objectValue.optionalString("rawText"),
                expanded = objectValue.requiredBoolean("expanded"),
                isStreaming = objectValue.requiredBoolean("isStreaming"),
                startedAtMillis = objectValue.requiredLong("startedAtMillis"),
                durationMillis = objectValue.optionalLong("durationMillis"),
            )

            "tool_event" -> ToolEventItem(
                toolName = objectValue.requiredString("toolName"),
                status = ToolEventStatus.valueOf(objectValue.requiredString("status")),
                preview = objectValue.optionalString("preview"),
                errorMessage = objectValue.optionalString("errorMessage"),
                operationIntent = objectValue.optionalString("operationIntent"),
                toolCallId = objectValue.optionalString("toolCallId"),
                resultPreview = objectValue.optionalString("resultPreview"),
                resultDisplay = objectValue.optionalString("resultDisplay"),
            )

            else -> error("Unsupported timeline item type: ${source.type}")
        }
    }

    /** 编码单个 Agent history 根项。 */
    private fun encodeHistory(sequence: Int, source: AgentConversationHistoryMessage): PersistedHistoryItem = when (source) {
        is AgentConversationHistoryMessage.User -> PersistedHistoryItem(sequence, "user", buildJsonObject {
            put("content", source.content)
        }.toString())

        is AgentConversationHistoryMessage.Assistant -> PersistedHistoryItem(sequence, "assistant", buildJsonObject {
            put("parts", buildJsonArray { source.parts.forEach { add(encodeHistoryPart(it)) } })
        }.toString())
    }

    /** 解码单个 Agent history 根项。 */
    private fun decodeHistory(source: PersistedHistoryItem): AgentConversationHistoryMessage {
        val objectValue = json.parseToJsonElement(source.payloadJson).jsonObject
        return when (source.type) {
            "user" -> AgentConversationHistoryMessage.User(objectValue.requiredString("content"))
            "assistant" -> AgentConversationHistoryMessage.Assistant(
                objectValue.getValue("parts").jsonArray.map(::decodeHistoryPart),
            )

            else -> error("Unsupported history item type: ${source.type}")
        }
    }

    /** 编码助手 history 的结构化 part。 */
    private fun encodeHistoryPart(source: AgentConversationHistoryPart): JsonObject = buildJsonObject {
        when (source) {
            is AgentConversationHistoryPart.Text -> {
                put("type", "text")
                put("text", source.text)
            }

            is AgentConversationHistoryPart.Reasoning -> {
                put("type", "reasoning")
                putNullable("summary", source.summary)
                putNullable("rawText", source.rawText)
            }

            is AgentConversationHistoryPart.ToolCall -> {
                put("type", "tool_call")
                putNullable("id", source.id)
                put("name", source.name)
                putNullable("argumentsPreview", source.argumentsPreview)
            }

            is AgentConversationHistoryPart.ToolResult -> {
                put("type", "tool_result")
                putNullable("id", source.id)
                put("name", source.name)
                putNullable("resultPreview", source.resultPreview)
            }
        }
    }

    /** 解码助手 history 的结构化 part。 */
    private fun decodeHistoryPart(source: JsonElement): AgentConversationHistoryPart {
        val objectValue = source.jsonObject
        return when (objectValue.requiredString("type")) {
            "text" -> AgentConversationHistoryPart.Text(objectValue.requiredString("text"))
            "reasoning" -> AgentConversationHistoryPart.Reasoning(objectValue.optionalString("summary"), objectValue.optionalString("rawText"))
            "tool_call" -> AgentConversationHistoryPart.ToolCall(objectValue.optionalString("id"), objectValue.requiredString("name"), objectValue.optionalString("argumentsPreview"))
            "tool_result" -> AgentConversationHistoryPart.ToolResult(objectValue.optionalString("id"), objectValue.requiredString("name"), objectValue.optionalString("resultPreview"))
            else -> error("Unsupported history part type: ${objectValue.requiredString("type")}")
        }
    }

    /** 将无效或过期的推理档位安全回退为默认档位。 */
    private fun String.toReasoningEffort(): ReasoningEffort =
        ReasoningEffort.entries.firstOrNull { it.name == this } ?: ReasoningEffort.MEDIUM

    /** 将无效或过期的权限档位安全回退为默认权限。 */
    private fun String.toPermissionPreset(): PermissionPreset =
        PermissionPreset.entries.firstOrNull { it.name == this } ?: PermissionPreset.DEFAULT

    /** 将原运行态恢复为不可续跑的错误状态。 */
    private fun PersistedTask.recoveredExecutionState(): ExecutionState = when (executionState) {
        "IDLE" -> ExecutionState.Idle
        "FAILED" -> ExecutionState.Failed(AppError(executionErrorTitle ?: "执行失败", executionErrorMessage.orEmpty()))
        else -> ExecutionState.Failed(AppError("执行已中断", "应用重启后无法继续此前的 Agent 执行。"))
    }

    /** 获取执行状态的稳定存储类型。 */
    private fun ExecutionState.persistenceType(): String = when (this) {
        ExecutionState.Idle -> "IDLE"
        ExecutionState.Running -> "RUNNING"
        ExecutionState.WaitingForUserInput -> "WAITING_FOR_USER_INPUT"
        ExecutionState.WaitingForApproval -> "WAITING_FOR_APPROVAL"
        is ExecutionState.Failed -> "FAILED"
    }

    /** 写入可空 JSON 字段。 */
    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
        put(key, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    /** 写入可空长整型 JSON 字段。 */
    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Long?) {
        put(key, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    /** 读取必填字符串字段。 */
    private fun JsonObject.requiredString(key: String): String = getValue(key).jsonPrimitive.content

    /** 读取可空字符串字段。 */
    private fun JsonObject.optionalString(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

    /** 读取必填布尔字段。 */
    private fun JsonObject.requiredBoolean(key: String): Boolean = getValue(key).jsonPrimitive.content.toBooleanStrict()

    /** 读取必填长整型字段。 */
    private fun JsonObject.requiredLong(key: String): Long = getValue(key).jsonPrimitive.content.toLong()

    /** 读取可空长整型字段。 */
    private fun JsonObject.optionalLong(key: String): Long? = get(key)?.jsonPrimitive?.contentOrNull?.toLong()
}
