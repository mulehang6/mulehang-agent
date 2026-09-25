package com.agent.shared.session

import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 桌面端全局外观偏好。 */
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
        const val DEFAULT_UI_SCALE_PERCENT: Int = 100
        const val MIN_UI_SCALE_PERCENT: Int = 50
        const val MAX_UI_SCALE_PERCENT: Int = 200
        const val UI_SCALE_STEP_PERCENT: Int = 10
    }
}

/** 内嵌终端的用户级偏好；只保存稳定 Shell 类型标识。 */
data class DesktopTerminalPreferences(
    val defaultShellId: String = DEFAULT_DESKTOP_TERMINAL_SHELL_ID,
) {
    /** 返回规范化后的终端偏好。 */
    fun normalized(): DesktopTerminalPreferences = copy(
        defaultShellId = defaultShellId.trim().ifBlank { DEFAULT_DESKTOP_TERMINAL_SHELL_ID },
    )
}

/** 旧版 Windows PowerShell 的稳定持久化标识，也是终端的默认回退项。 */
const val DEFAULT_DESKTOP_TERMINAL_SHELL_ID: String = "windows-powershell"

/** 将任意缩放百分比归一到支持范围内最近的 10% 档位。 */
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
 * 在统一 SQLDelight 数据库中保存桌面 UI 状态。
 *
 * 该状态不包含任何会话输入草稿；草稿只存在于进程内存中。
 */
class DesktopUiStateStore(
    private val persistence: DesktopPersistenceDatabase,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) {
    /** 为测试和独立使用保留的数据库路径构造函数。 */
    constructor(databasePath: Path) : this(DesktopPersistenceDatabase.open(databasePath))

    /** 合并旧版 JSON 或 SQLite UI 状态；源文件保留作回退。 */
    fun migrateLegacyState(legacyPath: Path) {
        val alreadyMigrated = persistence.read { queries ->
            queries.selectUiState(LEGACY_STATE_MIGRATION_KEY).executeAsOneOrNull() != null
        }
        if (alreadyMigrated) return
        if (!Files.isRegularFile(legacyPath)) return
        val legacyState = readLegacyState(legacyPath) ?: return
        persistence.write { queries ->
            if (queries.selectUiState(LEGACY_STATE_MIGRATION_KEY).executeAsOneOrNull() != null) return@write
            val currentRow = queries.selectUiState(DESKTOP_STATE_KEY).executeAsOneOrNull()
            val currentState = currentRow?.let { row ->
                require(row.payload_version <= UI_STATE_PAYLOAD_VERSION) {
                    "不支持的 UI 状态版本：${row.payload_version}"
                }
                json.decodeFromString(UiStateDocument.serializer(), row.payload_json)
            }
            val mergedState = mergeUiState(legacyState, currentState)
            val now = System.currentTimeMillis()
            queries.upsertUiState(
                state_key = DESKTOP_STATE_KEY,
                payload_version = UI_STATE_PAYLOAD_VERSION,
                payload_json = json.encodeToString(UiStateDocument.serializer(), mergedState),
                updated_at = now,
            )
            queries.upsertUiState(
                state_key = LEGACY_STATE_MIGRATION_KEY,
                payload_version = UI_STATE_PAYLOAD_VERSION,
                payload_json = "{}",
                updated_at = now,
            )
        }
    }

    /** 保留统一库中较新的字段，并补齐它尚未保存的旧版状态。 */
    private fun mergeUiState(legacy: UiStateDocument, current: UiStateDocument?): UiStateDocument {
        if (current == null) return legacy
        return UiStateDocument(
            projectSelections = legacy.projectSelections + current.projectSelections,
            recentWorkspace = current.recentWorkspace ?: legacy.recentWorkspace,
            themeMode = current.themeMode ?: legacy.themeMode,
            uiScalePercent = current.uiScalePercent ?: legacy.uiScalePercent,
            uiFontFamily = current.uiFontFamily ?: legacy.uiFontFamily,
            codeFontFamily = current.codeFontFamily ?: legacy.codeFontFamily,
            defaultTerminalShellId = current.defaultTerminalShellId ?: legacy.defaultTerminalShellId,
        )
    }

    /** 读取原版 JSON，或旧版缺陷曾生成的同路径 SQLite 状态库。 */
    private fun readLegacyState(legacyPath: Path): UiStateDocument? {
        runCatching {
            json.decodeFromString(UiStateDocument.serializer(), Files.readString(legacyPath))
        }.getOrNull()?.let { return it }
        if (!legacyPath.hasSqliteHeader()) return null
        return runCatching {
            DriverManager.getConnection("jdbc:sqlite:${legacyPath.toAbsolutePath().normalize()}").use { connection ->
                connection.createStatement().use { it.execute("PRAGMA query_only = ON") }
                val hasStateTable = connection.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'app_ui_state'",
                ).use { it.executeQuery().use { rows -> rows.next() } }
                if (!hasStateTable) return@use null
                connection.prepareStatement(
                    "SELECT payload_version, payload_json FROM app_ui_state WHERE state_key = ?",
                ).use { statement ->
                    statement.setString(1, DESKTOP_STATE_KEY)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) {
                            require(rows.getLong("payload_version") <= UI_STATE_PAYLOAD_VERSION) {
                                "不支持的旧版 UI 状态版本。"
                            }
                            json.decodeFromString(UiStateDocument.serializer(), rows.getString("payload_json"))
                        } else {
                            null
                        }
                    }
                }
            }
        }.getOrNull()
    }

    /** 仅尝试把 SQLite 文件作为数据库读取，避免打开任意损坏的 JSON。 */
    private fun Path.hasSqliteHeader(): Boolean = runCatching {
        Files.newInputStream(this).use { stream -> stream.readNBytes(SQLITE_HEADER.size).contentEquals(SQLITE_HEADER) }
    }.getOrDefault(false)

    /** 读取指定项目上次选择的 profile id。 */
    fun loadSelectedProfile(projectPath: String): String? = readState()?.projectSelections?.get(projectPath)

    /** 保存指定项目当前选择的 profile id。 */
    fun saveSelectedProfile(projectPath: String, profileId: String) {
        val current = readState() ?: UiStateDocument()
        saveState(current.copy(projectSelections = current.projectSelections + (projectPath to profileId)))
    }

    /** 读取最近使用的工作区路径。 */
    fun loadRecentWorkspace(): String? = readState()?.recentWorkspace

    /** 保存最近使用的工作区路径。 */
    fun saveRecentWorkspace(workspacePath: String) {
        val current = readState() ?: UiStateDocument()
        saveState(current.copy(recentWorkspace = workspacePath))
    }

    /** 读取用户选择的界面主题模式。 */
    fun loadThemeMode(): String? = readState()?.themeMode

    /** 保存用户选择的界面主题模式。 */
    fun saveThemeMode(themeMode: String) {
        val current = readState() ?: UiStateDocument()
        saveState(current.copy(themeMode = themeMode))
    }

    /** 读取用户级全局外观偏好。 */
    fun loadAppearancePreferences(): DesktopAppearancePreferences {
        val state = readState() ?: return DesktopAppearancePreferences()
        return DesktopAppearancePreferences(
            scalePercent = normalizeDesktopUiScalePercent(state.uiScalePercent),
            uiFontFamily = state.uiFontFamily,
            codeFontFamily = state.codeFontFamily,
        ).normalized()
    }

    /** 保存用户级全局外观偏好。 */
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

    /** 读取用户级终端偏好。 */
    fun loadTerminalPreferences(): DesktopTerminalPreferences = DesktopTerminalPreferences(
        defaultShellId = readState()?.defaultTerminalShellId ?: DEFAULT_DESKTOP_TERMINAL_SHELL_ID,
    ).normalized()

    /** 保存用户级终端偏好。 */
    fun saveTerminalPreferences(preferences: DesktopTerminalPreferences) {
        val current = readState() ?: UiStateDocument()
        saveState(current.copy(defaultTerminalShellId = preferences.normalized().defaultShellId))
    }

    /** 读取统一数据库中的版本化 UI 状态文档。 */
    private fun readState(): UiStateDocument? = persistence.read { queries ->
        queries.selectUiState(DESKTOP_STATE_KEY).executeAsOneOrNull()?.let { row ->
            require(row.payload_version <= UI_STATE_PAYLOAD_VERSION) {
                "不支持的 UI 状态版本：${row.payload_version}"
            }
            json.decodeFromString(UiStateDocument.serializer(), row.payload_json)
        }
    }

    /** 原子写入完整 UI 状态文档。 */
    private fun saveState(state: UiStateDocument) {
        val payload = json.encodeToString(UiStateDocument.serializer(), state)
        persistence.write { queries ->
            queries.upsertUiState(
                state_key = DESKTOP_STATE_KEY,
                payload_version = UI_STATE_PAYLOAD_VERSION,
                payload_json = payload,
                updated_at = System.currentTimeMillis(),
            )
        }
    }

    /** 用户级 UI 状态文档。 */
    @Serializable
    private data class UiStateDocument(
        val projectSelections: Map<String, String> = emptyMap(),
        val recentWorkspace: String? = null,
        val themeMode: String? = null,
        val uiScalePercent: Int? = null,
        val uiFontFamily: String? = null,
        val codeFontFamily: String? = null,
        val defaultTerminalShellId: String? = null,
    )

    private companion object {
        const val DESKTOP_STATE_KEY: String = "desktop"
        const val LEGACY_STATE_MIGRATION_KEY: String = "migration:legacy-ui-state-v1"
        const val UI_STATE_PAYLOAD_VERSION: Long = 1L
        val SQLITE_HEADER: ByteArray = "SQLite format 3\u0000".encodeToByteArray()
    }
}
