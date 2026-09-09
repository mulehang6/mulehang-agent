package com.agent.app.chat.component

import com.agent.shared.settings.model.SettingsDocument

/** 保存的是整个文档，因此任一分类的未完成输入都必须阻止写入。 */
internal fun validateSettingsForSave(document: SettingsDocument, fieldErrors: Map<String, String>): String? =
    fieldErrors.values.firstOrNull()
        ?: validateSettingsDocument(document)
        ?: validateMcpServerSettings(document)
        ?: validateAgentHookSettings(document)

/** 模型改名时迁移已有错误，避免旧键永久阻止保存或丢失尚未修正的输入。 */
internal fun renameSettingsValidationErrors(
    errors: MutableMap<String, String>,
    oldPrefix: String,
    newPrefix: String,
) {
    if (oldPrefix == newPrefix) return
    val moved = errors.filterKeys { it == oldPrefix || it.startsWith("$oldPrefix:") }
    moved.forEach { (key, _) -> errors.remove(key) }
    moved.forEach { (key, value) -> errors[newPrefix + key.removePrefix(oldPrefix)] = value }
}
