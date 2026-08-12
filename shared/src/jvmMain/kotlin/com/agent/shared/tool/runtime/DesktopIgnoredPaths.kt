package com.agent.shared.tool.runtime

import java.nio.file.Path

/** 为 glob 与 grep 统一排除依赖、构建产物和版本控制元数据。 */
object DesktopIgnoredPaths {
    /** 返回路径是否位于默认不应交给 agent 搜索的目录。 */
    fun containsIgnoredSegment(path: Path): Boolean = path.any { segment -> segment.toString() in IGNORED_DIRECTORY_NAMES }

    private val IGNORED_DIRECTORY_NAMES = setOf(
        ".git", ".gradle", ".idea", ".kotlin", "build", "out", "node_modules", ".next", ".cache",
    )
}
