package com.agent.shared.tool.interaction

import com.agent.shared.persistence.DesktopPersistenceDatabase
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.QuestionRequest
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 崩溃后仍可找到的提问或审批请求。 */
sealed interface SavedInteractionRequest {
    val requestId: String

    /** 待回答的 ask_user 请求。 */
    data class Question(val request: QuestionRequest) : SavedInteractionRequest {
        override val requestId: String get() = request.requestId
    }

    /** 待审批的工具请求。 */
    data class Approval(val request: ApprovalRequest) : SavedInteractionRequest {
        override val requestId: String get() = request.requestId
    }
}

/** 恢复后的审批结果，并保留是否允许本轮后续同类工具。 */
data class SavedApprovalDecision(
    val approved: Boolean,
    val allowToolType: Boolean,
)

/** 请求和答复单独持久化；只有桥真正取走答复后才标记消费。 */
class InteractionRequestRepository(private val database: DesktopPersistenceDatabase) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /** 在 UI 展示之前保存原始问题与运行归属。 */
    fun recordQuestion(conversationId: String, runId: String, request: QuestionRequest): QuestionRequest =
        insert(request.requestId, conversationId, runId, "QUESTION", "ask_user",
            signature(json.encodeToString(request.copy(requestId = ""))), json.encodeToString(request), request,
        ) { payload -> json.decodeFromString<QuestionRequest>(payload) }

    /** 在 UI 展示之前保存原始审批与运行归属。 */
    fun recordApproval(conversationId: String, runId: String, request: ApprovalRequest): ApprovalRequest =
        insert(request.requestId, conversationId, runId, "APPROVAL", request.toolName,
            signature(json.encodeToString(request.copy(requestId = ""))), json.encodeToString(request), request,
        ) { payload -> json.decodeFromString<ApprovalRequest>(payload) }

    /** 读取崩溃时最近一项尚待处理的交互。 */
    fun pending(conversationId: String): SavedInteractionRequest? = database.read { queries ->
        queries.selectLatestPendingInteraction(conversationId).executeAsOneOrNull()
    }?.let { row ->
        runCatching {
            when (row.kind) {
                "QUESTION" -> SavedInteractionRequest.Question(json.decodeFromString<QuestionRequest>(row.request_json))
                "APPROVAL" -> SavedInteractionRequest.Approval(json.decodeFromString<ApprovalRequest>(row.request_json))
                else -> null
            }
        }.getOrNull()
    }

    /** 用户的决定先落库，重启时桥才能消费相同请求的答复。 */
    fun answer(requestId: String, response: String): Boolean = database.write { queries ->
        val row = queries.selectInteractionRequest(requestId).executeAsOneOrNull() ?: return@write false
        if (row.state == "PENDING") {
            queries.answerInteractionRequest(response, System.currentTimeMillis(), requestId)
            true
        } else row.state == "ANSWERED" && row.response_text == response
    }

    /** 实时桥返回后记录消费；没有 UI 仓库的测试桥也可自行补写回答。 */
    fun consumed(requestId: String, response: String) {
        database.write { queries ->
            val row = queries.selectInteractionRequest(requestId).executeAsOneOrNull() ?: return@write
            if (row.state == "PENDING") queries.answerInteractionRequest(response, System.currentTimeMillis(), requestId)
            queries.consumeInteractionRequest(System.currentTimeMillis(), requestId)
        }
    }

    /** 恢复的工具重新发出相同交互时，原子取得尚未消费的答复。 */
    fun claimQuestion(runId: String, request: QuestionRequest): String? = claim(
        runId, "QUESTION", signature(json.encodeToString(request.copy(requestId = ""))),
    )

    /** 恢复的审批工具重新发出相同交互时，原子取得尚未消费的决定。 */
    fun claimApproval(runId: String, request: ApprovalRequest): SavedApprovalDecision? = claim(
        runId, "APPROVAL", signature(json.encodeToString(request.copy(requestId = ""))),
    )?.let(::decodeApprovalDecision)

    /** 只有回答已落库但尚未交给工具时才允许该工具从恢复点重新进入。 */
    fun hasUnconsumedAnswerForTool(runId: String, toolName: String): Boolean = database.read { queries ->
        queries.selectAnsweredInteractionForTool(runId, toolName).executeAsOneOrNull() != null
    }

    /** 用户主动停止轮次时取消仍在等待的请求。 */
    fun cancelPending(conversationId: String) {
        database.write { it.cancelPendingInteractions(conversationId) }
    }

    /** 单事务消费，避免同一答复被并发恢复两次。 */
    private fun claim(runId: String, kind: String, signature: String): String? = database.write { queries ->
        val row = queries.selectAnsweredInteractionForReplay(runId, kind, signature).executeAsOneOrNull()
            ?: return@write null
        queries.consumeInteractionRequest(System.currentTimeMillis(), row.request_id)
        row.response_text
    }

    /** 请求 ID 唯一，插入保持和会话运行记录相同的外键归属。 */
    private fun <T> insert(
        requestId: String,
        conversationId: String,
        runId: String,
        kind: String,
        toolName: String,
        signature: String,
        payload: String,
        request: T,
        decodeExisting: (String) -> T,
    ): T = database.write { queries ->
        val existing = queries.selectPendingInteractionForReplay(runId, kind, signature).executeAsOneOrNull()
        if (existing != null) return@write decodeExisting(existing.request_json)
        queries.insertInteractionRequest(
            request_id = requestId,
            conversation_id = conversationId,
            run_id = runId,
            kind = kind,
            tool_name = toolName,
            signature = signature,
            request_json = payload,
            created_at = System.currentTimeMillis(),
        )
        request
    }

    /** 兼容旧版布尔答复，同时恢复本轮允许同类工具的选择。 */
    private fun decodeApprovalDecision(response: String): SavedApprovalDecision? = when (response) {
        "true", "APPROVE_ONCE" -> SavedApprovalDecision(approved = true, allowToolType = false)
        "false", "REJECT_AND_STOP" -> SavedApprovalDecision(approved = false, allowToolType = false)
        "APPROVE_TOOL_TYPE" -> SavedApprovalDecision(approved = true, allowToolType = true)
        else -> null
    }

    /** 请求内容签名忽略每次工具重建时新生成的随机 requestId。 */
    private fun signature(payload: String): String = MessageDigest.getInstance("SHA-256")
        .digest(payload.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
