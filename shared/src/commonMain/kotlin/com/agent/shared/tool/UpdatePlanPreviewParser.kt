package com.agent.shared.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * update_plan 预览里的单个步骤。
 */
data class ParsedUpdatePlanStep(
    val step: String,
    val status: String,
)

/**
 * update_plan 工具参数预览的解析结果。
 */
data class ParsedUpdatePlanPreview(
    val explanation: String? = null,
    val plan: List<ParsedUpdatePlanStep> = emptyList(),
)

/**
 * 解析 update_plan 工具参数预览，避免 UI 层依赖字段顺序或转义细节。
 */
fun parseUpdatePlanPreview(preview: String): ParsedUpdatePlanPreview? = runCatching {
    val payloadObject = Json.parseToJsonElement(preview).jsonObject
    val steps = payloadObject["plan"]
        ?.jsonArray
        ?.mapNotNull { stepElement ->
            val stepObject = stepElement.jsonObject
            val step = stepObject["step"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val status = stepObject["status"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            ParsedUpdatePlanStep(
                step = step,
                status = status,
            )
        }
        .orEmpty()
    if (steps.isEmpty()) {
        null
    } else {
        ParsedUpdatePlanPreview(
            explanation = payloadObject["explanation"]?.jsonPrimitive?.contentOrNull,
            plan = steps,
        )
    }
}.getOrNull()
