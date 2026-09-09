package com.agent.shared.settings.resolver

import com.agent.shared.settings.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 验证持久化 JSON 和内部模型回退的完整配置路径。 */
class RequestSettingsRegressionTest {
    /** 示例的扁平事件格式和旧嵌套格式都必须恢复规则，保存只输出扁平格式。 */
    @Test
    fun `should round trip flat hooks and migrate legacy envelope`() {
        val rules = """{"PreToolUse":[{"hooks":[{"command":"echo test"}]}]}"""
        val json = Json { ignoreUnknownKeys = true }
        val flat = json.decodeFromString(SettingsDocument.serializer(), """{"hooks":$rules}""")
        val legacy = json.decodeFromString(SettingsDocument.serializer(), """{"hooks":{"hooks":$rules}}""")
        assertEquals(flat, legacy)
        assertEquals("echo test", flat.hooks.hooks.getValue(AgentHookEvent.PRE_TOOL_USE).single().hooks.single().command)
        val saved = json.encodeToJsonElement(SettingsDocument.serializer(), legacy).jsonObject.getValue("hooks").jsonObject
        assertFalse("hooks" in saved)
        assertEquals(setOf("PreToolUse"), saved.keys)
    }

    /** Provider 首模型回退需要继承主模型相同的请求覆盖与能力配置。 */
    @Test
    fun `should preserve request overrides in faster fallback`() {
        val provider = ProviderProfile(
            id = "test", providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://example.test", apiKey = "placeholder",
            request = RequestOverrides(headers = mapOf("X-Route" to "provider")),
            models = listOf(ModelProfile(
                id = "first", supportsVision = true,
                reasoningEfforts = listOf("low"), defaultReasoningEffort = "low",
                request = RequestOverrides(
                    headers = mapOf("X-Route" to "model"),
                    body = buildJsonObject { put("route", "fast") },
                    reasoningBodyByEffort = mapOf("low" to buildJsonObject { put("custom", true) }),
                ),
            )),
        )
        val document = SettingsDocument(providers = listOf(provider))
        val primary = SettingsMerger.merge(document, environment = emptyMap()).single()
        val fallback = FasterModelResolver.resolve(provider, document, ConfigLayer.USER)!!
        assertEquals(primary.copy(id = fallback.id), fallback)
    }
}
