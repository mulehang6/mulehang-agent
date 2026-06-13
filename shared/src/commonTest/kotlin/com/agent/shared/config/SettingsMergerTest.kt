package com.agent.shared.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
     * 上下文窗口和最大输出未显式填写时，应默认按 1M/384K 能力处理。
     */
    @Test
    fun `should default omitted context and output limits to one million and max output`() {
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

        assertEquals(ModelLimit(context = 1_000_000, output = 384_000), merged.single().limit)
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
}
