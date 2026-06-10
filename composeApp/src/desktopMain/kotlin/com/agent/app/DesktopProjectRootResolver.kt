package com.agent.app

import java.nio.file.Files
import java.nio.file.Path

/**
 * 解析桌面运行时应该使用的项目根目录。
 */
object DesktopProjectRootResolver {

    /**
     * 从启动工作目录向上查找 Gradle 仓库根，找不到时返回原始目录。
     */
    fun resolve(start: Path): Path {
        val normalizedStart = start.toAbsolutePath().normalize()
        var current: Path? = normalizedStart
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent
        }
        return normalizedStart
    }
}
