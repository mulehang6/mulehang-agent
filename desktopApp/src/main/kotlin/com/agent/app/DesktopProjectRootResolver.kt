package com.agent.app

import java.nio.file.Files
import java.nio.file.Path

/**
 * 解析桌面运行时应该使用的项目根目录。
 */
object DesktopProjectRootResolver {

    /**
     * 使用用户实际选择的路径作为项目根目录；如果传入文件路径，则使用它的父目录。
     */
    fun resolve(selectedRoot: Path): Path {
        val normalizedRoot = selectedRoot.toAbsolutePath().normalize()
        return if (Files.isRegularFile(normalizedRoot)) {
            normalizedRoot.parent ?: normalizedRoot
        } else {
            normalizedRoot
        }
    }
}
