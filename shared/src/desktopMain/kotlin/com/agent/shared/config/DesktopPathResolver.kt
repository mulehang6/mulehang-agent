package com.agent.shared.config

import java.nio.file.Path

/**
 * 解析用户级与项目级配置文件路径。
 */
data class DesktopPathResolver(
    val userHome: Path,
    val projectRoot: Path,
) {
    /**
     * 用户级 settings 路径。
     */
    fun userSettingsPath(): Path = userHome.resolve(".mulehang/settings.json")

    /**
     * 项目级 settings 路径。
     */
    fun projectSettingsPath(): Path = projectRoot.resolve("mulehang/settings.json")
}
