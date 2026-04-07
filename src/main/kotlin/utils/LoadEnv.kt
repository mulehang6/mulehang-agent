package utils

import java.nio.file.Path
import java.util.Properties

/**
 * 从指定目录下的 .env 文件中加载键值对。
 */
fun loadEnv(root: Path = Path.of("").toAbsolutePath().normalize()): Map<String, String> {
    val envFile = root.resolve(".env").toFile()
    if (!envFile.exists()) {
        return emptyMap()
    }

    return Properties().apply {
        envFile.inputStream().use { load(it) }
    }.entries.associate { (key, value) ->
        key.toString() to value.toString()
    }
}
