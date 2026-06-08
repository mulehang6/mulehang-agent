package com.agent.shared.config

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * 负责读取双层 settings 并输出最终 profile 列表。
 */
class DesktopSettingsRepository(
    private val pathResolver: DesktopPathResolver,
    private val environmentOverrides: DesktopEnvironmentOverrides,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) {
    /**
     * 加载最终 profile 列表。
     */
    fun loadResolvedProfiles(): List<ConfigProfile> {
        val user = readDocument(pathResolver.userSettingsPath())
        val project = readDocument(pathResolver.projectSettingsPath())
        return SettingsMerger.merge(
            user = user,
            project = project,
            environment = environmentOverrides.asMap(),
        )
    }

    /**
     * 写入项目级示例 settings。
     */
    fun writeExampleSettings(exampleContent: String) {
        val target = pathResolver.projectRoot.resolve("mulehang/settings.json.example")
        target.parent.createDirectories()
        Files.writeString(target, exampleContent)
    }

    /**
     * 读取单个 settings 文档，文件不存在时返回 null。
     */
    private fun readDocument(path: Path): SettingsDocument? {
        if (!path.exists()) return null
        return json.decodeFromString(SettingsDocument.serializer(), path.readText())
    }
}
