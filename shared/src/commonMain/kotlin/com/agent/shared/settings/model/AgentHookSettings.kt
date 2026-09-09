package com.agent.shared.settings.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

/** Junie 兼容的可配置 Hook 事件名称。 */
@Serializable
enum class AgentHookEvent {
    @SerialName("SessionStart")
    SESSION_START,

    @SerialName("UserPromptSubmit")
    USER_PROMPT_SUBMIT,

    @SerialName("PreToolUse")
    PRE_TOOL_USE,

    @SerialName("Stop")
    STOP,

    @SerialName("StopFailure")
    STOP_FAILURE,

    @SerialName("PermissionRequest")
    PERMISSION_REQUEST,

    @SerialName("SessionEnd")
    SESSION_END,
}

/** 一个事件下的匹配规则；省略 matcher 表示匹配该事件的全部可匹配值。 */
@Serializable
data class AgentHookMatcher(
    val matcher: String? = null,
    val hooks: List<AgentHookCommand> = emptyList(),
)

/** 通过 Windows Shell 执行的单条 Hook 命令。 */
@Serializable
data class AgentHookCommand(
    val type: String = "command",
    val command: String,
    val timeout: Int? = null,
    val blockOnError: Boolean = false,
    @SerialName("async")
    val runAsync: Boolean = false,
)

/** 全局 hooks 配置；同一事件中的匹配器严格按列表顺序执行。 */
@Serializable
data class AgentHookSettings(
    val hooks: Map<AgentHookEvent, List<AgentHookMatcher>> = emptyMap(),
)

/** settings.json 直接以事件名作为键，同时接受旧版多嵌套一层 hooks 的文档。 */
object AgentHookSettingsSerializer : KSerializer<AgentHookSettings> {
    private val rulesSerializer = MapSerializer(AgentHookEvent.serializer(), ListSerializer(AgentHookMatcher.serializer()))
    override val descriptor = rulesSerializer.descriptor

    /** 写入与示例一致的事件映射。 */
    override fun serialize(encoder: Encoder, value: AgentHookSettings) {
        encoder.encodeSerializableValue(rulesSerializer, value.hooks)
    }

    /** 兼容已由旧设置页保存的嵌套格式，之后保存会自动规整。 */
    override fun deserialize(decoder: Decoder): AgentHookSettings {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        val rules = (element as? JsonObject)?.get("hooks") ?: element
        return AgentHookSettings(jsonDecoder.json.decodeFromJsonElement(rulesSerializer, rules))
    }
}

/**
 * 追加另一来源的规则，并保持调用方先执行、追加来源后执行的稳定顺序。
 *
 * 用户全局设置用作基础，受信任扩展包的 Hook 通过此函数附加，避免任一来源覆盖另一来源。
 */
fun AgentHookSettings.append(after: AgentHookSettings): AgentHookSettings = AgentHookSettings(
    hooks = (hooks.keys + after.hooks.keys).associateWith { event ->
        hooks[event].orEmpty() + after.hooks[event].orEmpty()
    },
)
