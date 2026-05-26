package com.agent.shared.config

import kotlinx.serialization.json.Json

/**
 * 负责读取双层 settings 并输出最终 profile 列表。
 */
class DesktopSettingsRepository(
    private val pathResolver: DesktopPathResolver,
    private val environmentOverrides: DesktopEnvironmentOverrides,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) {
    /**
     * 加载最终 profile 列表。
     */
    fun loadResolvedProfiles(): List<ResolvedAgentProfile> {
        TODO("Implement layered settings loading from user/project JSON and environment overrides.")
    }

    /**
     * 写入项目级示例 settings。
     */
    fun writeExampleSettings(exampleContent: String) {
        TODO("Implement example settings file bootstrap.")
    }
}
