package com.agent.utils

import java.nio.file.Files
import java.nio.file.Path

/**
 * 读取简单 KEY=VALUE 格式的 .env 文件；不会输出或记录任何密钥值。
 */
fun readDotEnv(dotEnvPath: Path = Path.of(".env")): Map<String, String> {
    if (!Files.exists(dotEnvPath)) {
        return emptyMap()
    }

    return Files.readAllLines(dotEnvPath)
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
 * 去掉 .env 值两侧常见引号，保留中间内容不做额外处理。
 */
private fun String.trimMatchingQuotes(): String {
    return when {
        length >= 2 && first() == '"' && last() == '"' -> substring(1, lastIndex)
        length >= 2 && first() == '\'' && last() == '\'' -> substring(1, lastIndex)
        else -> this
    }
}
