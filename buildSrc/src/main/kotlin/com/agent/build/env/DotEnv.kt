package com.agent.build.env

import java.io.File

/**
 * 读取供 Gradle 构建逻辑使用的 `.env` 配置值。
 */
object DotEnv {
    /**
     * 从 [dotEnvFile] 中读取 [key] 对应的第一个非空值。
     *
     * 支持普通和 `export` 形式的键值对，以及单引号或双引号包裹的值。
     */
    fun readValue(
        dotEnvFile: File,
        key: String,
    ): String? {
        if (!dotEnvFile.isFile) return null

        return dotEnvFile.useLines { lines ->
            lines.firstNotNullOfOrNull { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@firstNotNullOfOrNull null

                val separatorIndex = line.indexOf('=')
                if (separatorIndex < 1) return@firstNotNullOfOrNull null

                val lineKey = line.substring(0, separatorIndex).removePrefix("export ").trim()
                if (lineKey != key) return@firstNotNullOfOrNull null

                line.substring(separatorIndex + 1)
                    .trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                    .takeIf(String::isNotEmpty)
            }
        }
    }
}
