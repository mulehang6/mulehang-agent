package com.agent.app.chat.component

import com.agent.shared.settings.model.SettingsDocument
import com.agent.shared.settings.persistence.DesktopSettingsRepository

/** 设置页可独立持久化的区域；枚举顺序也是多区域通知的稳定顺序。 */
internal enum class SettingsSaveArea {
    MCP,
    HOOKS,
    EXTENSIONS,
    PROVIDERS,
}

/**
 * 把一个区域的草稿合并进刚从磁盘读取的最新文档。
 *
 * 字段级合并避免 MCP、Hooks、扩展或 Provider 的未保存草稿覆盖其他区域已经落盘的修改。
 */
internal fun mergeSettingsArea(
    latest: SettingsDocument,
    draft: SettingsDocument,
    area: SettingsSaveArea,
): SettingsDocument = when (area) {
    SettingsSaveArea.MCP -> latest.copy(
        agentResources = latest.agentResources.copy(mcpServers = draft.agentResources.mcpServers),
    )

    SettingsSaveArea.HOOKS -> latest.copy(hooks = draft.hooks)

    SettingsSaveArea.EXTENSIONS -> latest.copy(
        agentResources = draft.agentResources.copy(mcpServers = latest.agentResources.mcpServers),
    )

    SettingsSaveArea.PROVIDERS -> latest.copy(
        providers = draft.providers,
        fasterModel = draft.fasterModel,
        maxIterations = draft.maxIterations,
    )
}

/** 只把成功保存的区域更新到比较基线，保留其他区域各自的旧基线。 */
internal fun mergeSavedBaseline(
    baseline: SettingsDocument,
    saved: SettingsDocument,
    area: SettingsSaveArea,
): SettingsDocument = mergeSettingsArea(baseline, saved, area)

/** 判断当前区域是否真的偏离最近一次成功保存的基线。 */
internal fun hasSettingsAreaChanges(
    baseline: SettingsDocument,
    draft: SettingsDocument,
    area: SettingsSaveArea,
): Boolean = mergeSettingsArea(baseline, draft, area) != baseline

/** 设置页关闭前统一判断所有持久化区域及尚未同步的非法 JSON。 */
internal fun hasUnsavedSettingsChanges(uiState: SettingsPanelUiState): Boolean =
    SettingsSaveArea.entries.any { area ->
        hasSettingsAreaChanges(uiState.lastSavedDocument, uiState.document, area)
    } || uiState.mcpJsonEditorState.error != null

/**
 * 重新读取磁盘文档后只合并一个区域并原子保存；失败和无变化都不会更新基线或产生通知。
 */
internal fun persistSettingsArea(
    uiState: SettingsPanelUiState,
    repository: DesktopSettingsRepository,
    area: SettingsSaveArea,
    onSettingsSaved: () -> Unit,
): Boolean {
    validateSettingsAreaForSave(uiState.document, uiState.settingsValidationErrors, area)?.let { validation ->
        uiState.feedback = validation
        return false
    }
    val latest = runCatching { repository.loadDocument(uiState.layer) }
        .getOrElse { error ->
            uiState.feedback = "读取最新设置失败：${error.message ?: "未知错误"}"
            return false
        }
    val documentToSave = mergeSettingsArea(latest, uiState.document, area)
    if (documentToSave == latest) {
        uiState.feedback = "没有需要保存的修改。"
        return false
    }
    runCatching { repository.saveDocument(uiState.layer, documentToSave) }
        .onFailure { error ->
            uiState.feedback = "保存失败：${error.message ?: "未知错误"}"
            return false
        }

    uiState.document = mergeSettingsArea(uiState.document, documentToSave, area)
    uiState.lastSavedDocument = mergeSavedBaseline(uiState.lastSavedDocument, documentToSave, area)
    uiState.feedback = "已保存；重新加载后用于后续任务。"
    settingsSaveNotification(area)?.let { message ->
        uiState.changeNotifications.record(
            category = if (area == SettingsSaveArea.PROVIDERS) {
                SettingsChangeNotificationCategory.AI_SERVICES
            } else {
                SettingsChangeNotificationCategory.EXTENSIONS
            },
            message = "${settingsChangeScopeLabel(uiState.layer)}：$message",
        )
    }
    if (area != SettingsSaveArea.PROVIDERS) uiState.resourceReloadPending = true
    if (area == SettingsSaveArea.MCP) uiState.mcpReloadPending = true
    if (area == SettingsSaveArea.PROVIDERS) uiState.providerFieldsChangedSinceLastSave = false
    onSettingsSaved()
    return true
}

/** 保存成功后的无敏感信息通知文案。 */
private fun settingsSaveNotification(area: SettingsSaveArea): String? = when (area) {
    SettingsSaveArea.MCP -> "已保存 MCP 服务修改。"
    SettingsSaveArea.HOOKS -> "已保存 Agent Hooks 修改。"
    SettingsSaveArea.EXTENSIONS -> "已保存扩展资源修改。"
    SettingsSaveArea.PROVIDERS -> "已保存 AI 服务修改。"
}
