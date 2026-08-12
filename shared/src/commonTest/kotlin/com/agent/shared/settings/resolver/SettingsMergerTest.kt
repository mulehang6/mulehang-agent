package com.agent.shared.settings.resolver

import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.IllegalConfigExceptions
import com.agent.shared.settings.model.ModelLimit
import com.agent.shared.settings.model.ModelProfile
import com.agent.shared.settings.model.ProviderProfile
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.settings.model.SettingsDocument

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 验证 settings 文档的 provider/model 结构、默认值与层级覆盖规则。
 */
class SettingsMergerTest {

    /**
     * 缺少 enabled 字段时，provider 和 model 默认启用。
     */
    @Test
    fun `should treat missing enabled as true`() {
        val provider = ProviderProfile(
            id = "openai",
            providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com/v1",
            apiKey = "json-key",
            models = listOf(ModelProfile(id = "gpt-4.1")),
        )

        assertTrue(provider.isEnabled())
        assertTrue(provider.models.single().isEnabled())
    }

    /**
     * 项目级配置应按 profile id 覆盖用户级配置。
     */
    @Test
    fun `should let project layer override user layer`() {
        val userSettings = SettingsDocument(
            providers = listOf(
                ProviderProfile(
                    id = "openai",
                    providerType = ProviderType.OPENAI_RESPONSES,
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = "user-key",
                    models = listOf(ModelProfile(id = "gpt-4.1")),
                ),
            ),
        )
        val projectSettings = SettingsDocument(
            providers = listOf(
                ProviderProfile(
                    id = "openai",
                    providerType = ProviderType.OPENAI_RESPONSES,
                    baseUrl = "https://custom.example/v1",
                    apiKey = "project-key",
                    models = listOf(ModelProfile(id = "gpt-4.1-mini")),
                ),
            ),
        )

        val merged = SettingsMerger.merge(
            user = userSettings,
            project = projectSettings,
            environment = emptyMap(),
        )

        assertEquals("https://custom.example/v1", merged.single().baseUrl)
        assertEquals("project-key", merged.single().apiKey)
        assertEquals("gpt-4.1-mini", merged.single().model)
        assertEquals("openai:gpt-4.1-mini", merged.single().id)
        assertEquals("openai", merged.single().providerId)
        assertEquals(ConfigLayer.PROJECT, merged.single().layer)
    }

    /**
     * 同一个 provider 下的多个模型应展平成同一 providerId 下的运行时 profile。
     */
    @Test
    fun `should flatten provider models into runtime profiles`() {
        val projectSettings = SettingsDocument(
            providers = listOf(
                ProviderProfile(
                    id = "deepseek",
                    label = "DeepSeek",
                    providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://api.deepseek.com/v1",
                    apiKey = "project-key",
                    models = listOf(
                        ModelProfile(id = "deepseek-v4-flash", label = "V4 Flash"),
                        ModelProfile(id = "deepseek-v4-pro", label = "V4 Pro"),
                    ),
                ),
            ),
        )

        val merged = SettingsMerger.merge(
            user = null,
            project = projectSettings,
            environment = emptyMap(),
        )

        assertEquals(listOf("deepseek:deepseek-v4-flash", "deepseek:deepseek-v4-pro"), merged.map { it.id })
        assertEquals(listOf("deepseek", "deepseek"), merged.map { it.providerId })
        assertEquals(listOf("DeepSeek", "DeepSeek"), merged.map { it.providerLabel })
        assertEquals(listOf("V4 Flash", "V4 Pro"), merged.map { it.modelLabel })
    }

    /**
     * 未声明 defaultModel 时，models 数组的第一个模型应成为运行时列表首项。
     */
    @Test
    fun `should use first model when default model is omitted`() {
        val merged = SettingsMerger.merge(
            user = null,
            project = SettingsDocument(
                providers = listOf(
                    ProviderProfile(
                        id = "custom",
                        providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                        baseUrl = "https://gateway.example/v1",
                        apiKey = "key",
                        models = listOf(
                            ModelProfile(id = "first-model"),
                            ModelProfile(id = "second-model"),
                        ),
                    ),
                ),
            ),
            environment = emptyMap(),
        )

        assertEquals(listOf("first-model", "second-model"), merged.map { it.model })
    }

    /**
     * 环境变量优先级最高，应覆盖 JSON 中的字段。
     */
    @Test
    fun `should let environment override project layer`() {
        val projectSettings = SettingsDocument(
            providers = listOf(
                ProviderProfile(
                    id = "openai",
                    providerType = ProviderType.OPENAI_RESPONSES,
                    baseUrl = "https://project.example/v1",
                    apiKey = "project-key",
                    models = listOf(ModelProfile(id = "gpt-4.1-mini")),
                ),
            ),
        )

        val merged = SettingsMerger.merge(
            user = null,
            project = projectSettings,
            environment = mapOf(
                "MULEHANG_BASE_URL" to "https://env.example/v1",
                "MULEHANG_API_KEY" to "env-key",
                "MULEHANG_MODEL" to "gpt-5.4-mini",
            ),
        )

        assertEquals("https://env.example/v1", merged.single().baseUrl)
        assertEquals("env-key", merged.single().apiKey)
        assertEquals("gpt-5.4-mini", merged.single().model)
        assertEquals("openai:gpt-5.4-mini", merged.single().id)
        assertEquals(ConfigLayer.ENVIRONMENT, merged.single().layer)
    }

    /**
     * 模型上下文窗口配置应从 JSON profile 合并到运行时 profile。
     */
    @Test
    fun `should merge model limit from profile settings`() {
        val projectSettings = SettingsDocument(
            providers = listOf(
                ProviderProfile(
                    id = "deepseek",
                    providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://api.deepseek.com/v1",
                    apiKey = "project-key",
                    models = listOf(
                        ModelProfile(
                            id = "deepseek-v4-pro",
                            limit = ModelLimit(context = 128_000, output = 16_000),
                        ),
                    ),
                ),
            ),
        )

        val merged = SettingsMerger.merge(
            user = null,
            project = projectSettings,
            environment = emptyMap(),
        )

        assertEquals(ModelLimit(context = 128_000, output = 16_000), merged.single().limit)
    }

    /**
     * 上下文窗口和最大输出未显式填写时，应默认按 256K/384K 能力处理。
     */
    @Test
    fun `should default omitted context and output limits to 256k and max output`() {
        val projectSettings = SettingsDocument(
            providers = listOf(
                ProviderProfile(
                    id = "openrouter",
                    providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://openrouter.ai/api/v1",
                    apiKey = "project-key",
                    models = listOf(ModelProfile(id = "openai/gpt-5-codex")),
                ),
            ),
        )

        val merged = SettingsMerger.merge(
            user = null,
            project = projectSettings,
            environment = emptyMap(),
        )

        assertEquals(ModelLimit(context = 256_000, output = 384_000), merged.single().limit)
    }

    /**
     * 同一个 provider 下不同模型应保留各自的上下文和最大输出限制。
     */
    @Test
    fun `should keep different limits per model under same provider`() {
        val projectSettings = SettingsDocument(
            providers = listOf(
                ProviderProfile(
                    id = "openrouter",
                    providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://openrouter.ai/api/v1",
                    apiKey = "project-key",
                    models = listOf(
                        ModelProfile(
                            id = "openai/gpt-5-codex",
                            limit = ModelLimit(context = 272_000, output = 128_000),
                        ),
                        ModelProfile(
                            id = "anthropic/claude-sonnet-4",
                            limit = ModelLimit(context = 200_000, output = 64_000),
                        ),
                    ),
                ),
            ),
        )

        val merged = SettingsMerger.merge(
            user = null,
            project = projectSettings,
            environment = emptyMap(),
        )

        assertEquals(ModelLimit(context = 272_000, output = 128_000), merged[0].limit)
        assertEquals(ModelLimit(context = 200_000, output = 64_000), merged[1].limit)
    }

    /**
     * 环境变量应允许临时覆盖上下文窗口，便于验证不同 profile 能力。
     */
    @Test
    fun `should let environment override context limit`() {
        val projectSettings = SettingsDocument(
            providers = listOf(
                ProviderProfile(
                    id = "deepseek",
                    providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://api.deepseek.com/v1",
                    apiKey = "project-key",
                    models = listOf(
                        ModelProfile(
                            id = "deepseek-v4-pro",
                            limit = ModelLimit(context = 128_000, output = 8_000),
                        ),
                    ),
                ),
            ),
        )

        val merged = SettingsMerger.merge(
            user = null,
            project = projectSettings,
            environment = mapOf("MULEHANG_CONTEXT_WINDOW" to "1000000"),
        )

        assertEquals(ModelLimit(context = 1_000_000, output = 8_000), merged.single().limit)
        assertEquals(ConfigLayer.ENVIRONMENT, merged.single().layer)
    }

    /**
     * 模型名应清理终端样式残留，避免 ANSI 片段被发送到 provider。
     */
    @Test
    fun `should sanitize styled model names from settings and environment`() {
        val projectSettings = SettingsDocument(
            providers = listOf(
                ProviderProfile(
                    id = "deepseek",
                    providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://api.deepseek.com/v1",
                    apiKey = "project-key",
                    models = listOf(ModelProfile(id = "deepseek-v4-pro\u001B[1m")),
                ),
            ),
        )

        val fromProject = SettingsMerger.merge(
            user = null,
            project = projectSettings,
            environment = emptyMap(),
        )
        val fromEnvironment = SettingsMerger.merge(
            user = null,
            project = projectSettings,
            environment = mapOf("MULEHANG_MODEL" to "deepseek-v4-flash[1m"),
        )

        assertEquals("deepseek-v4-pro", fromProject.single().model)
        assertEquals("deepseek-v4-flash", fromEnvironment.single().model)
    }

    /**
     * 显式 enabled=false 应关闭 profile。
     */
    @Test
    fun `should allow profile to be disabled explicitly`() {
        val disabled = ProviderProfile(
            id = "anthropic-work",
            providerType = ProviderType.ANTHROPIC,
            baseUrl = "https://api.anthropic.com",
            apiKey = "hidden",
            models = listOf(ModelProfile(id = "claude-sonnet-4")),
            enabled = false,
        )

        assertFalse(disabled.isEnabled())
    }

    /**
     * 自定义模型的 reasoning 档位应从 JSON 配置转换为类型安全的运行时 profile。
     */
    @Test
    fun `should merge configured reasoning efforts into runtime profile`() {
        val merged = SettingsMerger.merge(
            user = null,
            project = customModelSettings(
                reasoningEfforts = listOf("low", "medium", "high"),
                defaultReasoningEffort = "medium",
            ),
            environment = emptyMap(),
        )

        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            merged.single().reasoningEfforts,
        )
        assertEquals(ReasoningEffort.MEDIUM, merged.single().defaultReasoningEffort)
    }

    /**
     * 配置中的 xhigh 档位应转换为运行时 profile 可消费的类型安全枚举。
     */
    @Test
    fun `should merge xhigh configured reasoning effort into runtime profile`() {
        val merged = SettingsMerger.merge(
            user = null,
            project = customModelSettings(
                reasoningEfforts = listOf("low", "medium", "high", "xhigh", "max"),
                defaultReasoningEffort = "medium",
            ),
            environment = emptyMap(),
        )

        assertEquals(
            listOf(
                ReasoningEffort.LOW,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.HIGH,
                ReasoningEffort.XHIGH,
                ReasoningEffort.MAX,
            ),
            merged.single().reasoningEfforts,
        )
    }

    /**
     * `none` 是部分 Responses 服务用来关闭思考模式的显式 wire value，应保留为可配置档位。
     */
    @Test
    fun `should merge none configured reasoning effort into runtime profile`() {
        val merged = SettingsMerger.merge(
            user = null,
            project = customModelSettings(
                reasoningEfforts = listOf("none", "low", "high", "max"),
            ),
            environment = emptyMap(),
        )

        assertEquals(
            listOf(
                ReasoningEffort.NONE,
                ReasoningEffort.LOW,
                ReasoningEffort.HIGH,
                ReasoningEffort.MAX,
            ),
            merged.single().reasoningEfforts,
        )
    }

    /**
     * 显式空列表表示模型不支持 reasoning，不能与未配置混淆。
     */
    @Test
    fun `should preserve an explicitly empty reasoning effort list`() {
        val merged = SettingsMerger.merge(
            user = null,
            project = customModelSettings(reasoningEfforts = emptyList()),
            environment = emptyMap(),
        )

        assertEquals(emptyList(), merged.single().reasoningEfforts)
        assertEquals(null, merged.single().defaultReasoningEffort)
    }

    /**
     * 非法 reasoning 档位应在配置合并时指出对应模型。
     */
    @Test
    fun `should reject invalid configured reasoning effort`() {
        val exception = assertFailsWith<IllegalConfigExceptions> {
            SettingsMerger.merge(
                user = null,
                project = customModelSettings(reasoningEfforts = listOf("deep")),
                environment = emptyMap(),
            )
        }

        assertContains(exception.message.orEmpty(), "custom:custom-reasoning-model")
        assertContains(exception.message.orEmpty(), "deep")
    }

    /**
     * 默认 reasoning 档位必须是该模型已声明的可选项之一。
     */
    @Test
    fun `should reject configured default reasoning effort outside supported efforts`() {
        val exception = assertFailsWith<IllegalConfigExceptions> {
            SettingsMerger.merge(
                user = null,
                project = customModelSettings(
                    reasoningEfforts = listOf("low"),
                    defaultReasoningEffort = "high",
                ),
                environment = emptyMap(),
            )
        }

        assertContains(exception.message.orEmpty(), "custom:custom-reasoning-model")
        assertContains(exception.message.orEmpty(), "defaultReasoningEffort")
    }

    /**
     * 为自定义 OpenAI-compatible 模型构造测试配置。
     */
    private fun customModelSettings(
        reasoningEfforts: List<String>,
        defaultReasoningEffort: String? = null,
    ): SettingsDocument = SettingsDocument(
        providers = listOf(
            ProviderProfile(
                id = "custom",
                providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://gateway.example/v1",
                apiKey = "test-key",
                models = listOf(
                    ModelProfile(
                        id = "custom-reasoning-model",
                        reasoningEfforts = reasoningEfforts,
                        defaultReasoningEffort = defaultReasoningEffort,
                    ),
                ),
            ),
        ),
    )
}
