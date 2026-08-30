package com.agent.shared.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.math.roundToInt

/**
 * 桌面端全局外观偏好。
 *
 * 字体名称保持原样保存，以便系统字体重新可用时自动恢复；是否实际可用由桌面端解析。
 */
data class DesktopAppearancePreferences(
    val scalePercent: Int = DEFAULT_UI_SCALE_PERCENT,
    val uiFontFamily: String? = null,
    val codeFontFamily: String? = null,
) {
    /** 返回可安全应用和持久化的规范化外观偏好。 */
    fun normalized(): DesktopAppearancePreferences = copy(
        scalePercent = normalizeDesktopUiScalePercent(scalePercent),
        uiFontFamily = uiFontFamily?.takeIf(String::isNotBlank),
        codeFontFamily = codeFontFamily?.takeIf(String::isNotBlank),
    )

    companion object {
        /** 默认的全局界面缩放百分比。 */
        const val DEFAULT_UI_SCALE_PERCENT: Int = 100

        /** 允许的最小全局界面缩放百分比。 */
        const val MIN_UI_SCALE_PERCENT: Int = 50

        /** 允许的最大全局界面缩放百分比。 */
        const val MAX_UI_SCALE_PERCENT: Int = 200

        /** 全局界面缩放的离散步长。 */
        const val UI_SCALE_STEP_PERCENT: Int = 10
    }
}

/**
 * 将任意缩放百分比归一到支持范围内最近的 10% 档位。
 */
fun normalizeDesktopUiScalePercent(scalePercent: Int?): Int {
    if (scalePercent == null) return DesktopAppearancePreferences.DEFAULT_UI_SCALE_PERCENT
    val rounded = (scalePercent / DesktopAppearancePreferences.UI_SCALE_STEP_PERCENT.toDouble())
        .roundToInt() * DesktopAppearancePreferences.UI_SCALE_STEP_PERCENT
    return rounded.coerceIn(
        DesktopAppearancePreferences.MIN_UI_SCALE_PERCENT,
        DesktopAppearancePreferences.MAX_UI_SCALE_PERCENT,
    )
}

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

    /**
     * 读取用户级全局外观偏好；旧状态和缺失缩放均按默认值兼容。
     */
    fun loadAppearancePreferences(): DesktopAppearancePreferences {
        val state = readState() ?: return DesktopAppearancePreferences()
        return DesktopAppearancePreferences(
            scalePercent = normalizeDesktopUiScalePercent(state.uiScalePercent),
            uiFontFamily = state.uiFontFamily,
            codeFontFamily = state.codeFontFamily,
        ).normalized()
    }

    /** 保存用户级全局外观偏好，并先规范化缩放和值为空的字体名称。 */
    fun saveAppearancePreferences(preferences: DesktopAppearancePreferences) {
        val current = readState() ?: UiStateDocument()
        val normalized = preferences.normalized()
        saveState(
            current.copy(
                uiScalePercent = normalized.scalePercent,
                uiFontFamily = normalized.uiFontFamily,
                codeFontFamily = normalized.codeFontFamily,
            ),
        )
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
        val uiScalePercent: Int? = null,
        val uiFontFamily: String? = null,
        val codeFontFamily: String? = null,
    )
}
