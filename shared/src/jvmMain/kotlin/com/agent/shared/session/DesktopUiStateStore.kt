package com.agent.shared.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

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
        val state = readState() ?: return null
        return state.projectSelections[projectPath]
    }

    /**
     * 保存指定项目当前选择的 profile id。
     */
    fun saveSelectedProfile(projectPath: String, profileId: String) {
        val current = readState() ?: UiStateDocument()
        val updated = current.copy(
            projectSelections = current.projectSelections + (projectPath to profileId),
        )
        statePath.parent?.let(Files::createDirectories)
        Files.writeString(statePath, json.encodeToString(UiStateDocument.serializer(), updated))
    }

    /**
     * 读取最近使用的工作区路径。
     */
    fun loadRecentWorkspace(): String? {
        val state = readState() ?: return null
        return state.recentWorkspace
    }

    /**
     * 保存最近使用的工作区路径。
     */
    fun saveRecentWorkspace(workspacePath: String) {
        val current = readState() ?: UiStateDocument()
        val updated = current.copy(recentWorkspace = workspacePath)
        statePath.parent?.let(Files::createDirectories)
        Files.writeString(statePath, json.encodeToString(UiStateDocument.serializer(), updated))
    }

    /** 读取用户选择的界面主题模式；缺省时由桌面端采用深色主题。 */
    fun loadThemeMode(): String? = readState()?.themeMode

    /** 保存用户选择的界面主题模式。 */
    fun saveThemeMode(themeMode: String) {
        val current = readState() ?: UiStateDocument()
        saveState(current.copy(themeMode = themeMode))
    }

    /** 读取是否启用全应用 Liquid Glass；旧状态文件缺省为关闭。 */
    fun loadLiquidGlassEnabled(): Boolean = readState()?.liquidGlassEnabled ?: false

    /** 保存全应用 Liquid Glass 材质开关。 */
    fun saveLiquidGlassEnabled(enabled: Boolean) {
        val current = readState() ?: UiStateDocument()
        saveState(current.copy(liquidGlassEnabled = enabled))
    }

    /**
     * 读取 UI 状态文档，文件不存在时返回 null。
     */
    private fun readState(): UiStateDocument? {
        if (!statePath.exists()) return null
        return json.decodeFromString(UiStateDocument.serializer(), statePath.readText())
    }

    /** 写入完整 UI 状态，同时保证父目录已经存在。 */
    private fun saveState(state: UiStateDocument) {
        statePath.parent?.let(Files::createDirectories)
        Files.writeString(statePath, json.encodeToString(UiStateDocument.serializer(), state))
    }

    /**
     * 用户级 UI 状态文档。
     */
    @Serializable
    private data class UiStateDocument(
        val projectSelections: Map<String, String> = emptyMap(),
        val recentWorkspace: String? = null,
        val themeMode: String? = null,
        val liquidGlassEnabled: Boolean = false,
    )
}
