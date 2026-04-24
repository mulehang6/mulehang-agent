package com.agent.runtime.utils

import java.nio.file.Files
import java.nio.file.Path

/**
 * 读取简单 KEY=VALUE 格式的 .env 文件；不会输出或记录任何密钥值。
 */
fun readDotEnv(dotEnvPath: Path = Path.of(".env")): Map<String, String> {
    val resolvedPath = resolveDotEnvPath(dotEnvPath) ?: return emptyMap()
    if (!Files.exists(resolvedPath)) {
        return emptyMap()
    }

    return Files.readAllLines(resolvedPath)
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.removePrefix("export ").split("=", limit = 2) }
        .filter { it.size == 2 && it[0].isNotBlank() }
        .associate { (key, value) ->
            key.trim() to value.trim().trimMatchingQuotes()
        }
}

/**
 * 解析应当读取的 .env 路径。
 *
 * 对默认的相对路径 `.env`，会从当前工作目录向上逐级查找，兼容 IDE 从子模块目录运行测试的场景；
 * 对显式传入的绝对路径或带父目录的路径，按调用方给定的位置读取。
 */
internal fun resolveDotEnvPath(
    dotEnvPath: Path,
    workingDirectory: Path = Path.of("").toAbsolutePath(),
): Path? {
    if (dotEnvPath.isAbsolute || dotEnvPath.parent != null) {
        return dotEnvPath
    }

    var current: Path? = workingDirectory
    while (current != null) {
        val candidate = current.resolve(dotEnvPath)
        if (Files.exists(candidate)) {
            return candidate
        }
        current = current.parent
    }
    return dotEnvPath
}

/**
 * 去掉 .env 值两侧常见引号，保留中间内容不做额外处理。
 */
private fun String.trimMatchingQuotes(): String {
    return when {
        length >= 2 && first() == '"' && last() == '"' -> substring(1, lastIndex)
        length >= 2 && first() == '\'' && last() == '\'' -> substring(1, lastIndex)
        else -> this
    }
}
