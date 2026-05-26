package com.agent.shared.state

import kotlinx.serialization.json.Json
import java.nio.file.Path

/**
 * 按项目保存 UI 级最近选择状态。
 */
class DesktopUiStateStore(
    private val statePath: Path,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) {
    /**
     * 读取指定项目上次选择的 profile id。
     */
    fun loadSelectedProfile(projectPath: String): String? {
        TODO("Implement project-scoped UI state loading.")
    }

    /**
     * 保存指定项目当前选择的 profile id。
     */
    fun saveSelectedProfile(projectPath: String, profileId: String) {
        TODO("Implement project-scoped UI state persistence.")
    }
}
