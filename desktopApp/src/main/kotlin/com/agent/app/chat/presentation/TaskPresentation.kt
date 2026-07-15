package com.agent.app.chat.presentation

/**
 * 决定新任务应落在哪个工作区；可按需强制走目录选择器。
 */
internal fun resolveWorkspaceForTaskCreation(
    activeWorkspacePath: String?,
    forceDirectoryPicker: Boolean,
    pickWorkspaceDirectory: () -> String?,
): String? = if (forceDirectoryPicker || activeWorkspacePath.isNullOrBlank()) {
    pickWorkspaceDirectory()
} else {
    activeWorkspacePath
}
