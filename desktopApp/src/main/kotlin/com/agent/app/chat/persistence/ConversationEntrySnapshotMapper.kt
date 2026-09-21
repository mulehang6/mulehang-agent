package com.agent.app.chat.persistence

import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.ToolEventStatus
import com.agent.shared.chat.persistence.PersistedTaskEntry
import com.agent.shared.tool.model.FileDiffPreview
import com.agent.shared.tool.model.QuestionAnswer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 将稳定会话条目显式编码为数据库负载，并负责无损恢复全部条目类型。 */
internal object ConversationEntrySnapshotMapper {
    private val json = Json

    /** 编码单个会话条目。 */
    fun encode(source: ConversationEntry): PersistedTaskEntry {
        val (type, payload) = when (source) {
            is ConversationEntry.Message -> "message" to buildJsonObject {
                put("role", source.message.role.name)
                put("content", source.message.content)
                put("inputParts", buildJsonArray { source.inputParts.forEach { add(encodeInputPart(it)) } })
            }

            is ConversationEntry.Reasoning -> "reasoning" to buildJsonObject {
                putNullable("summaryText", source.summaryText)
                putNullable("rawText", source.rawText)
                put("expanded", source.expanded)
                put("isStreaming", source.isStreaming)
                put("startedAtMillis", source.startedAtMillis)
                putNullable("durationMillis", source.durationMillis)
            }

            is ConversationEntry.ToolCall -> "tool_call" to buildJsonObject {
                put("toolName", source.toolName)
                putNullable("preview", source.preview)
                putNullable("operationIntent", source.operationIntent)
                putNullable("toolCallId", source.toolCallId)
                putNullable("resultDisplay", source.resultDisplay)
                put("fileDiffs", json.encodeToJsonElement(source.fileDiffs))
            }

            is ConversationEntry.ToolResult -> "tool_result" to buildJsonObject {
                put("toolName", source.toolName)
                put("status", source.status.name)
                putNullable("errorMessage", source.errorMessage)
                putNullable("toolCallId", source.toolCallId)
                putNullable("resultPreview", source.resultPreview)
                putNullable("resultDisplay", source.resultDisplay)
                put("fileDiffs", json.encodeToJsonElement(source.fileDiffs))
            }

            is ConversationEntry.Answers -> "answers" to buildJsonObject {
                put("answers", buildJsonArray {
                    source.answers.forEach { answer ->
                        add(buildJsonObject {
                            put("question", answer.question)
                            put("answer", answer.answer)
                        })
                    }
                })
            }

            is ConversationEntry.BranchSummary -> "branch_summary" to buildJsonObject {
                put("fromEntryId", source.fromEntryId)
                put("summary", source.summary)
                putNullable("details", source.details)
                putNullable("inputTokens", source.inputTokens)
                putNullable("outputTokens", source.outputTokens)
            }

            is ConversationEntry.Label -> "label" to buildJsonObject {
                put("targetEntryId", source.targetEntryId)
                put("label", source.label)
            }

            is ConversationEntry.ModelChange -> "model_change" to buildJsonObject {
                putNullable("profileId", source.profileId)
            }

            is ConversationEntry.ReasoningEffortChange -> "reasoning_effort_change" to buildJsonObject {
                put("reasoningEffort", source.reasoningEffort)
            }

            is ConversationEntry.Custom -> "custom" to buildJsonObject {
                put("customType", source.type)
                put("text", source.text)
                put("inputParts", buildJsonArray { source.inputParts.forEach { add(encodeInputPart(it)) } })
            }
        }
        return PersistedTaskEntry(
            id = source.id,
            parentId = source.parentId,
            createdAt = source.createdAt,
            type = type,
            payloadJson = payload.toString(),
        )
    }

    /** 解码单个会话条目。 */
    fun decode(source: PersistedTaskEntry): ConversationEntry {
        val payload = json.parseToJsonElement(source.payloadJson).jsonObject
        return when (source.type) {
            "message" -> ConversationEntry.Message(
                id = source.id,
                parentId = source.parentId,
                createdAt = source.createdAt,
                message = ChatMessage(
                    role = ChatRole.valueOf(payload.requiredString("role")),
                    content = payload.requiredString("content"),
                ),
                inputParts = payload["inputParts"]?.jsonArray?.map(::decodeInputPart).orEmpty(),
            )

            "reasoning" -> ConversationEntry.Reasoning(
                id = source.id,
                parentId = source.parentId,
                createdAt = source.createdAt,
                summaryText = payload.optionalString("summaryText"),
                rawText = payload.optionalString("rawText"),
                expanded = payload.requiredBoolean("expanded"),
                isStreaming = payload.requiredBoolean("isStreaming"),
                startedAtMillis = payload.requiredLong("startedAtMillis"),
                durationMillis = payload.optionalLong("durationMillis"),
            )

            "tool_call" -> ConversationEntry.ToolCall(
                id = source.id,
                parentId = source.parentId,
                createdAt = source.createdAt,
                toolName = payload.requiredString("toolName"),
                preview = payload.optionalString("preview"),
                operationIntent = payload.optionalString("operationIntent"),
                toolCallId = payload.optionalString("toolCallId"),
                resultDisplay = payload.optionalString("resultDisplay"),
                fileDiffs = payload.decodeDiffs(),
            )

            "tool_result" -> ConversationEntry.ToolResult(
                id = source.id,
                parentId = source.parentId,
                createdAt = source.createdAt,
                toolName = payload.requiredString("toolName"),
                status = ToolEventStatus.valueOf(payload.requiredString("status")),
                errorMessage = payload.optionalString("errorMessage"),
                toolCallId = payload.optionalString("toolCallId"),
                resultPreview = payload.optionalString("resultPreview"),
                resultDisplay = payload.optionalString("resultDisplay"),
                fileDiffs = payload.decodeDiffs(),
            )

            "answers" -> ConversationEntry.Answers(
                id = source.id,
                parentId = source.parentId,
                createdAt = source.createdAt,
                answers = payload.getValue("answers").jsonArray.map { answerElement ->
                    val answer = answerElement.jsonObject
                    QuestionAnswer(answer.requiredString("question"), answer.requiredString("answer"))
                },
            )

            "branch_summary" -> ConversationEntry.BranchSummary(
                id = source.id,
                parentId = source.parentId,
                createdAt = source.createdAt,
                fromEntryId = payload.requiredString("fromEntryId"),
                summary = payload.requiredString("summary"),
                details = payload.optionalString("details"),
                inputTokens = payload.optionalLong("inputTokens"),
                outputTokens = payload.optionalLong("outputTokens"),
            )

            "label" -> ConversationEntry.Label(
                id = source.id,
                parentId = source.parentId,
                createdAt = source.createdAt,
                targetEntryId = payload.requiredString("targetEntryId"),
                label = payload.requiredString("label"),
            )

            "model_change" -> ConversationEntry.ModelChange(
                id = source.id,
                parentId = source.parentId,
                createdAt = source.createdAt,
                profileId = payload.optionalString("profileId"),
            )

            "reasoning_effort_change" -> ConversationEntry.ReasoningEffortChange(
                id = source.id,
                parentId = source.parentId,
                createdAt = source.createdAt,
                reasoningEffort = payload.requiredString("reasoningEffort"),
            )

            "custom" -> ConversationEntry.Custom(
                id = source.id,
                parentId = source.parentId,
                createdAt = source.createdAt,
                type = payload.requiredString("customType"),
                text = payload.requiredString("text"),
                inputParts = payload["inputParts"]?.jsonArray?.map(::decodeInputPart).orEmpty(),
            )

            else -> error("Unsupported conversation entry type: ${source.type}")
        }
    }

    /** 编码用户有序输入片段。 */
    private fun encodeInputPart(source: UserInputPart): JsonObject = buildJsonObject {
        when (source) {
            is UserInputPart.Text -> {
                put("type", "text")
                put("text", source.text)
            }

            is UserInputPart.FileSnapshot -> {
                put("type", "file")
                put("path", source.path)
                put("content", source.content)
                put("mimeType", source.mimeType)
            }

            is UserInputPart.Image -> {
                put("type", "image")
                put("mediaId", source.mediaId)
                put("storagePath", source.storagePath)
                put("mimeType", source.mimeType)
                put("label", source.label)
            }
        }
    }

    /** 解码用户有序输入片段。 */
    private fun decodeInputPart(source: JsonElement): UserInputPart {
        val payload = source.jsonObject
        return when (payload.requiredString("type")) {
            "text" -> UserInputPart.Text(payload.requiredString("text"))
            "file" -> UserInputPart.FileSnapshot(
                path = payload.requiredString("path"),
                content = payload.requiredString("content"),
                mimeType = payload.requiredString("mimeType"),
            )

            "image" -> UserInputPart.Image(
                mediaId = payload.requiredString("mediaId"),
                storagePath = payload.requiredString("storagePath"),
                mimeType = payload.requiredString("mimeType"),
                label = payload.requiredString("label"),
            )

            else -> error("Unsupported entry input part type: ${payload.requiredString("type")}")
        }
    }

    /** 解码条目上的结构化文件差异。 */
    private fun JsonObject.decodeDiffs(): List<FileDiffPreview> = this["fileDiffs"]?.let { encoded ->
        json.decodeFromJsonElement<List<FileDiffPreview>>(encoded)
    }.orEmpty()

    /** 写入可空字符串字段。 */
    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
        put(key, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    /** 写入可空长整型字段。 */
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
