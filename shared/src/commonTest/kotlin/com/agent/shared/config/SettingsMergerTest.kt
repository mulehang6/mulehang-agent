package com.agent.shared.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证 settings 文档的默认启用与层级覆盖规则。
 */
class SettingsMergerTest {

    /**
     * 缺少 enabled 字段时，profile 默认启用。
     */
    @Test
    fun `should treat missing enabled as true`() {
        val profile = AgentProfile(
            id = "openai-main",
            providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com/v1",
            apiKey = "json-key",
            model = "gpt-4.1",
        )

        assertTrue(profile.isEnabled())
    }

    /**
     * 项目级配置应按 profile id 覆盖用户级配置。
     */
    @Test
    fun `should let project layer override user layer`() {
        val userSettings = SettingsDocument(
            profiles = listOf(
                AgentProfile(
                    id = "main",
                    providerType = ProviderType.OPENAI_RESPONSES,
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = "user-key",
                    model = "gpt-4.1",
                ),
            ),
        )
        val projectSettings = SettingsDocument(
            profiles = listOf(
                AgentProfile(
                    id = "main",
                    providerType = ProviderType.OPENAI_RESPONSES,
                    baseUrl = "https://custom.example/v1",
                    apiKey = "project-key",
                    model = "gpt-4.1-mini",
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
        assertEquals(ConfigLayer.PROJECT, merged.single().layer)
    }

    /**
     * 环境变量优先级最高，应覆盖 JSON 中的字段。
     */
    @Test
    fun `should let environment override project layer`() {
        val projectSettings = SettingsDocument(
            profiles = listOf(
                AgentProfile(
                    id = "main",
                    providerType = ProviderType.OPENAI_RESPONSES,
                    baseUrl = "https://project.example/v1",
                    apiKey = "project-key",
                    model = "gpt-4.1-mini",
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
        assertEquals(ConfigLayer.ENVIRONMENT, merged.single().layer)
    }

    /**
     * 模型上下文窗口配置应从 JSON profile 合并到运行时 profile。
     */
    @Test
    fun `should merge model limit from profile settings`() {
        val projectSettings = SettingsDocument(
            profiles = listOf(
                AgentProfile(
                    id = "deepseek",
                    providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://api.deepseek.com/v1",
                    apiKey = "project-key",
                    model = "deepseek-v4-pro",
                    limit = ModelLimit(context = 1_000_000, output = 384_000),
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
            profiles = listOf(
                AgentProfile(
                    id = "deepseek",
                    providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://api.deepseek.com/v1",
                    apiKey = "project-key",
                    model = "deepseek-v4-pro",
                    limit = ModelLimit(context = 128_000, output = 8_000),
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
            profiles = listOf(
                AgentProfile(
                    id = "deepseek",
                    providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://api.deepseek.com/v1",
                    apiKey = "project-key",
                    model = "deepseek-v4-pro\u001B[1m",
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
        val disabled = AgentProfile(
            id = "anthropic-work",
            providerType = ProviderType.ANTHROPIC,
            baseUrl = "https://api.anthropic.com",
            apiKey = "hidden",
            model = "claude-sonnet-4",
            enabled = false,
        )

        assertFalse(disabled.isEnabled())
    }
}
