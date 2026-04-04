package utils

import java.io.File
import java.util.Properties

/**
 * 从 .env 文件中加载环境变量
 */
fun loadEnv(): Properties {
    return Properties().apply {
        val envFile = File(".env")
        if (envFile.exists()) {
            // .use {} 核心语法糖，自动关闭资源
            envFile.inputStream().use { load(it) }
        }
    }
}

