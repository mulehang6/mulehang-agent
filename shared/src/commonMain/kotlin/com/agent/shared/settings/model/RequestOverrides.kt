package com.agent.shared.settings.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 不依赖厂商名称的 HTTP 请求扩展配置。
 *
 * [body] 用于所有请求的静态 JSON 覆盖；[reasoningBodyByEffort] 在用户选择对应思考档位时最后合并，
 * 因而可表达不同协议的启用、关闭与档位字段。
 */
@Serializable
data class RequestOverrides(
    val headers: Map<String, String> = emptyMap(),
    val body: JsonObject = JsonObject(emptyMap()),
    val reasoningBodyByEffort: Map<String, JsonObject> = emptyMap(),
)

/**
 * 深度合并两个 JSON 对象；对象递归合并，数组和标量由后者完整覆盖。
 */
fun JsonObject.deepMerge(override: JsonObject): JsonObject = JsonObject(
    (keys + override.keys).associateWith { key ->
        val baseValue = get(key)
        val overrideValue = override[key]
        when {
            baseValue is JsonObject && overrideValue is JsonObject -> baseValue.deepMerge(overrideValue)
            overrideValue != null -> overrideValue
            baseValue != null -> baseValue
            else -> error("JSON object key must resolve to a value: $key")
        }
    },
)

/**
 * 合并同一思考档位的 JSON 映射，模型级声明优先于 Provider 级声明。
 */
fun Map<String, JsonObject>.deepMerge(override: Map<String, JsonObject>): Map<String, JsonObject> =
    (keys + override.keys).associateWith { key ->
        val baseValue = get(key)
        val overrideValue = override[key]
        when {
            baseValue != null && overrideValue != null -> baseValue.deepMerge(overrideValue)
            overrideValue != null -> overrideValue
            baseValue != null -> baseValue
            else -> error("Reasoning request body key must resolve to a value: $key")
        }
    }
