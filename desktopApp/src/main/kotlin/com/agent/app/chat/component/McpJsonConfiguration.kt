package com.agent.app.chat.component

import com.agent.shared.settings.model.McpServerSettings
import com.agent.shared.settings.model.McpServerTransport
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** MCP JSON 文本的解析结果，失败时保留可直接展示给用户的错误。 */
internal sealed interface McpJsonParseResult {
    /** 成功解析并完成字段校验的直接 MCP 服务。 */
    data class Success(val servers: List<McpServerSettings>) : McpJsonParseResult

    /** 无法安全同步到可视化配置的 JSON 输入。 */
    data class Failure(val message: String) : McpJsonParseResult
}

/** 将常见的 `mcpServers` JSON 配置解析为应用现有的直接 MCP 模型。 */
internal fun parseMcpJsonConfiguration(text: String): McpJsonParseResult {
    val root = runCatching { MCP_CONFIGURATION_JSON.parseToJsonElement(text) }
        .getOrElse { error ->
            return McpJsonParseResult.Failure(error.message ?: "请输入有效的 JSON 对象。")
        }
    return runCatching { parseMcpJsonRoot(root) }
        .fold(
            onSuccess = McpJsonParseResult::Success,
            onFailure = { error ->
                McpJsonParseResult.Failure(error.message ?: "MCP JSON 配置无效。")
            },
        )
}

/** 将直接 MCP 服务格式化为稳定、可再次解析的常见配置格式。 */
internal fun formatMcpJsonConfiguration(servers: List<McpServerSettings>): String {
    val root = buildJsonObject {
        put(
            "mcpServers",
            buildJsonObject {
                servers.forEach { server -> put(server.id, server.toMcpJsonObject()) }
            },
        )
    }
    return MCP_CONFIGURATION_JSON.encodeToString(root)
}

/** 检查模型是否能无损写入受支持的 `mcpServers` JSON 结构。 */
internal fun validateMcpServersForJson(servers: List<McpServerSettings>): String? {
    val ids = servers.map(McpServerSettings::id)
    if (ids.any(String::isBlank)) return "MCP 服务 ID 不能为空。"
    if (ids.distinct().size != ids.size) return "MCP 服务 ID 不能重复。"
    servers.forEach { server ->
        when (server.transport) {
            McpServerTransport.STDIO -> {
                if (server.command.firstOrNull().isNullOrBlank()) {
                    return "stdio MCP '${server.id}' 必须填写命令。"
                }
                if (server.headers.isNotEmpty()) {
                    return "stdio MCP '${server.id}' 不支持 headers 字段。"
                }
            }

            McpServerTransport.SSE, McpServerTransport.STREAMABLE_HTTP -> {
                if (!isSupportedMcpUrl(server.url)) {
                    return "HTTP MCP '${server.id}' 必须填写有效的 http(s) 服务地址。"
                }
                if (server.environment.isNotEmpty()) {
                    return "远程 MCP '${server.id}' 不支持 env 字段。"
                }
                validateMcpHeaders(server.id, server.headers)?.let { return it }
            }
        }
        server.environment.keys.firstOrNull(::isInvalidEnvironmentName)?.let { key ->
            return "MCP '${server.id}' 存在无效环境变量名：${key.ifBlank { "<空>" }}。"
        }
    }
    return null
}

/** 解析顶层对象，并拒绝会在保存时静默丢失的额外字段。 */
private fun parseMcpJsonRoot(element: JsonElement): List<McpServerSettings> {
    val root = element as? JsonObject ?: invalidMcpJson("MCP JSON 顶层必须是对象。")
    rejectUnsupportedFields(root, ROOT_FIELDS, "MCP JSON 顶层")
    val serversObject = root["mcpServers"] as? JsonObject
        ?: invalidMcpJson("MCP JSON 必须包含对象字段 'mcpServers'。")
    return serversObject.map { (id, value) ->
        if (id.isBlank()) invalidMcpJson("MCP 服务 ID 不能为空。")
        val serverObject = value as? JsonObject
            ?: invalidMcpJson("MCP '$id' 的配置必须是对象。")
        parseMcpServer(id, serverObject)
    }.also { servers ->
        validateMcpServersForJson(servers)?.let(::invalidMcpJson)
    }
}

/** 解析一条服务记录，并根据传输类型约束可用字段。 */
private fun parseMcpServer(id: String, value: JsonObject): McpServerSettings {
    rejectUnsupportedFields(value, SERVER_FIELDS, "MCP '$id'")
    if ("enabled" in value && "disabled" in value) {
        invalidMcpJson("MCP '$id' 不能同时设置 enabled 和 disabled。")
    }
    val enabled = value.optionalBoolean("enabled", id)
        ?: value.optionalBoolean("disabled", id)?.not()
        ?: true
    val type = value.optionalString("type", id)?.toMcpTransport(id, "type")
    val transportAlias = value.optionalString("transport", id)?.toMcpTransport(id, "transport")
    if (type != null && transportAlias != null && type != transportAlias) {
        invalidMcpJson("MCP '$id' 的 type 和 transport 相互冲突。")
    }
    val transport = type ?: transportAlias ?: McpServerTransport.STDIO
    return when (transport) {
        McpServerTransport.STDIO -> parseStdioServer(id, value, enabled)
        McpServerTransport.SSE, McpServerTransport.STREAMABLE_HTTP -> parseRemoteServer(id, value, enabled, transport)
    }
}

/** 解析 stdio 服务，并拒绝远程连接专用字段。 */
private fun parseStdioServer(id: String, value: JsonObject, enabled: Boolean): McpServerSettings {
    if ("url" in value) invalidMcpJson("stdio MCP '$id' 不支持 url 字段。")
    if ("headers" in value) invalidMcpJson("stdio MCP '$id' 不支持 headers 字段。")
    val command = value.requiredString("command", id)
    val arguments = value.optionalStringList("args", id)
    val environment = value.optionalStringMap("env", id)
    return McpServerSettings(
        id = id,
        transport = McpServerTransport.STDIO,
        command = listOf(command) + arguments,
        environment = environment,
        enabled = enabled,
    )
}

/** 解析 SSE 或 streamable HTTP 服务，并拒绝 stdio 专用字段。 */
private fun parseRemoteServer(
    id: String,
    value: JsonObject,
    enabled: Boolean,
    transport: McpServerTransport,
): McpServerSettings {
    listOf("command", "args", "env").firstOrNull(value::containsKey)?.let { field ->
        invalidMcpJson("远程 MCP '$id' 不支持 $field 字段。")
    }
    return McpServerSettings(
        id = id,
        transport = transport,
        url = value.requiredString("url", id),
        headers = value.optionalHeaders(id),
        enabled = enabled,
    )
}

/** 把应用模型写成兼容常见 MCP 客户端的单服务对象。 */
private fun McpServerSettings.toMcpJsonObject(): JsonObject = buildJsonObject {
    when (transport) {
        McpServerTransport.STDIO -> {
            put("command", command.firstOrNull().orEmpty())
            put("args", buildJsonArray { command.drop(1).forEach { argument -> add(JsonPrimitive(argument)) } })
            if (environment.isNotEmpty()) {
                put("env", buildJsonObject { environment.forEach { (key, value) -> put(key, value) } })
            }
        }

        McpServerTransport.SSE -> {
            put("type", "sse")
            put("url", url.orEmpty())
            putHeadersIfPresent(headers)
        }

        McpServerTransport.STREAMABLE_HTTP -> {
            put("type", "streamable-http")
            put("url", url.orEmpty())
            putHeadersIfPresent(headers)
        }
    }
    if (!enabled) put("disabled", true)
}

/** 仅在远程服务实际配置请求头时写出对象，避免规范文本出现空字段。 */
private fun kotlinx.serialization.json.JsonObjectBuilder.putHeadersIfPresent(headers: Map<String, String>) {
    if (headers.isNotEmpty()) {
        put("headers", buildJsonObject { headers.forEach { (key, value) -> put(key, value) } })
    }
}

/** 读取必填字符串字段，并给出包含服务 ID 的错误。 */
private fun JsonObject.requiredString(field: String, id: String): String =
    optionalString(field, id)?.takeIf(String::isNotBlank)
        ?: invalidMcpJson("MCP '$id' 必须填写字符串字段 '$field'。")

/** 读取可选字符串字段，拒绝数字、布尔值和 null。 */
private fun JsonObject.optionalString(field: String, id: String): String? {
    val element = this[field] ?: return null
    val primitive = element as? JsonPrimitive
    if (primitive == null || !primitive.isString) invalidMcpJson("MCP '$id' 的 $field 必须是字符串。")
    return primitive.content
}

/** 读取可选布尔字段，拒绝字符串形式的真假值。 */
private fun JsonObject.optionalBoolean(field: String, id: String): Boolean? {
    val element = this[field] ?: return null
    val primitive = element as? JsonPrimitive
    if (primitive == null || primitive.isString) invalidMcpJson("MCP '$id' 的 $field 必须是布尔值。")
    val value = primitive.booleanOrNull
    return value ?: invalidMcpJson("MCP '$id' 的 $field 必须是布尔值。")
}

/** 读取字符串数组，空数组代表没有命令参数。 */
private fun JsonObject.optionalStringList(field: String, id: String): List<String> {
    val element = this[field] ?: return emptyList()
    val array = element as? JsonArray ?: invalidMcpJson("MCP '$id' 的 $field 必须是字符串数组。")
    return array.mapIndexed { index, item ->
        val primitive = item as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            invalidMcpJson("MCP '$id' 的 $field 第 ${index + 1} 项必须是字符串。")
        }
        primitive.content
    }
}

/** 读取字符串键值对象，并校验环境变量名称。 */
private fun JsonObject.optionalStringMap(field: String, id: String): Map<String, String> {
    val element = this[field] ?: return emptyMap()
    val value = element as? JsonObject ?: invalidMcpJson("MCP '$id' 的 $field 必须是字符串对象。")
    return value.mapValues { (key, item) ->
        if (isInvalidEnvironmentName(key)) {
            invalidMcpJson("MCP '$id' 存在无效环境变量名：${key.ifBlank { "<空>" }}。")
        }
        val primitive = item as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            invalidMcpJson("MCP '$id' 的环境变量 '$key' 必须是字符串。")
        }
        primitive.content
    }
}

/** 读取并校验远程 MCP Headers；Header 名称按 HTTP 的大小写不敏感规则判重。 */
private fun JsonObject.optionalHeaders(id: String): Map<String, String> {
    val headers = optionalRawStringMap("headers", id)
    validateMcpHeaders(id, headers)?.let(::invalidMcpJson)
    return headers
}

/** 读取通用字符串对象，不对键名施加环境变量专用规则。 */
private fun JsonObject.optionalRawStringMap(field: String, id: String): Map<String, String> {
    val element = this[field] ?: return emptyMap()
    val value = element as? JsonObject ?: invalidMcpJson("MCP '$id' 的 $field 必须是字符串对象。")
    return value.mapValues { (key, item) ->
        val primitive = item as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            invalidMcpJson("MCP '$id' 的 $field 项 '$key' 必须是字符串。")
        }
        primitive.content
    }
}

/** 拒绝空名称、换行注入和大小写不同但语义重复的 HTTP Header。 */
private fun validateMcpHeaders(id: String, headers: Map<String, String>): String? {
    headers.entries.firstOrNull { (name, value) ->
        name.isBlank() || name.any { it == '\r' || it == '\n' } || value.any { it == '\r' || it == '\n' }
    }?.let { (name, _) ->
        return "MCP '$id' 存在无效 Header：${name.ifBlank { "<空>" }}。"
    }
    val normalizedNames = headers.keys.map { it.lowercase() }
    if (normalizedNames.distinct().size != normalizedNames.size) {
        return "MCP '$id' 的 Header 名称不能仅以大小写区分。"
    }
    return null
}

/** 将 JSON 中的传输名称转换为现有枚举。 */
private fun String.toMcpTransport(id: String, field: String): McpServerTransport =
    when (lowercase().replace('_', '-')) {
        "stdio" -> McpServerTransport.STDIO
        "sse" -> McpServerTransport.SSE
        "streamable-http" -> McpServerTransport.STREAMABLE_HTTP
        else -> invalidMcpJson("MCP '$id' 的 $field 不支持传输类型 '$this'。")
    }

/** 拒绝转换层无法保存回模型的字段，避免用户配置被静默删减。 */
private fun rejectUnsupportedFields(value: JsonObject, supported: Set<String>, label: String) {
    value.keys.firstOrNull { field -> field !in supported }?.let { field ->
        invalidMcpJson("$label 不支持字段 '$field'。")
    }
}

/** 判断环境变量名称是否会导致进程环境注入歧义。 */
private fun isInvalidEnvironmentName(name: String): Boolean =
    name.isBlank() || name.any { character -> character == '=' || character.isWhitespace() }

/** 仅接受运行时 HTTP MCP 客户端可连接的绝对地址。 */
private fun isSupportedMcpUrl(value: String?): Boolean = runCatching {
    val uri = URI(value?.trim().orEmpty())
    uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

/** 抛出仅在解析边界内捕获的用户输入错误。 */
private fun invalidMcpJson(message: String): Nothing = throw McpJsonValidationException(message)

/** 标记可直接展示而无需附带堆栈信息的 MCP JSON 校验失败。 */
private class McpJsonValidationException(message: String) : IllegalArgumentException(message)

private val ROOT_FIELDS = setOf("mcpServers")
private val SERVER_FIELDS = setOf("command", "args", "env", "headers", "type", "transport", "url", "enabled", "disabled")
private val MCP_CONFIGURATION_JSON = Json { prettyPrint = true }
