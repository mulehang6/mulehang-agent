package com.agent.shared.state

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

    /**
     * 读取 UI 状态文档，文件不存在时返回 null。
     */
    private fun readState(): UiStateDocument? {
        if (!statePath.exists()) return null
        return json.decodeFromString(UiStateDocument.serializer(), statePath.readText())
    }

    /**
     * 用户级 UI 状态文档。
     */
    @Serializable
    private data class UiStateDocument(
        val projectSelections: Map<String, String> = emptyMap(),
        val recentWorkspace: String? = null,
    )
}
