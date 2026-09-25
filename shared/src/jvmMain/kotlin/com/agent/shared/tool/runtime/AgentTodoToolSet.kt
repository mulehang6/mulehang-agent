package com.agent.shared.tool.runtime

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.agent.shared.agent.status.AgentTodoDraft
import com.agent.shared.agent.status.AgentTodoRepository
import com.agent.shared.agent.status.AgentTodoStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Agent 可写、用户只读的会话 TODO 工具。 */
@Suppress("FunctionName")
@LLMDescription("Maintain the current conversation TODO list. The list persists across user turns. Use stable IDs and update existing items instead of replacing IDs when only status changes.")
class AgentTodoToolSet(
    private val conversationId: String,
    private val repository: AgentTodoRepository,
) : ToolSet {
    /** 用完整有序清单替换当前 TODO，所有字段必须显式给出。 */
    @Tool
    @LLMDescription("Replace the complete ordered TODO list. Pass a JSON array of objects with id, content, status. Status is PENDING, IN_PROGRESS, or COMPLETED. Pass [] to clear the list.")
    fun rewrite_todo_list(
        @LLMDescription("Complete JSON array, for example [{\"id\":\"inspect\",\"content\":\"检查现状\",\"status\":\"IN_PROGRESS\"}].")
        todos_json: String,
    ): String {
        val parsed = Json.parseToJsonElement(todos_json).jsonArray.map { element ->
            val item = element.jsonObject
            require(item.keys == setOf("id", "content", "status")) { "TODO 必须仅包含 id、content、status。" }
            AgentTodoDraft(
                id = item.getValue("id").jsonPrimitive.content,
                content = item.getValue("content").jsonPrimitive.content,
                status = AgentTodoStatus.valueOf(item.getValue("status").jsonPrimitive.content),
            )
        }
        return summary(repository.rewrite(conversationId, parsed))
    }

    /** 更新一个现有 TODO 的状态。 */
    @Tool
    @LLMDescription("Change the status of one existing TODO item by its stable ID. Status is PENDING, IN_PROGRESS, or COMPLETED.")
    fun update_todo_status(
        @LLMDescription("Existing TODO ID.") id: String,
        @LLMDescription("PENDING, IN_PROGRESS, or COMPLETED.") status: String,
    ): String = summary(repository.updateStatus(conversationId, id, AgentTodoStatus.valueOf(status)))

    /** 向模型返回与数据库一致的简短进度。 */
    private fun summary(items: List<com.agent.shared.agent.status.AgentTodoItem>): String {
        val completed = items.count { it.status == AgentTodoStatus.COMPLETED }
        return "TODO 已更新：$completed/${items.size} 完成。"
    }
}
