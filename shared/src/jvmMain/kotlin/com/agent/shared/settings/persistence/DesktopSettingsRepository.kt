package com.agent.shared.settings.persistence

import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.SettingsDocument
import com.agent.shared.settings.resolver.SettingsMerger
import io.github.oshai.kotlinlogging.KotlinLogging
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

    /** 加载当前设置层解析后的 AUTO 审批模型。 */
    fun loadResolvedFasterProfiles(): Map<String, ConfigProfile> {
        val user = readDocument(pathResolver.userSettingsPath())
        val project = readDocument(pathResolver.projectSettingsPath())
        val resolutions = SettingsMerger.mergeFasterProfileResolutions(user, project)
        resolutions.forEach { (providerId, resolution) ->
            val profile = resolution.profile
            log.info {
                "event=auto_reviewer_config provider=$providerId status=${if (profile == null) "manual" else "ready"} " +
                    "source=${resolution.source?.wireValue ?: "manual"} reason=${resolution.reason} " +
                    "model=${profile?.model ?: "-"}"
            }
        }
        return resolutions.mapNotNull { (providerId, resolution) ->
            resolution.profile?.let { providerId to it }
        }.toMap()
    }

    /**
     * 写入项目级示例 settings。
     */
    fun writeExampleSettings(exampleContent: String) {
        val target = pathResolver.projectRoot.resolve(".mulehang/settings.json.example")
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

    private companion object {
        val log = KotlinLogging.logger { }
    }
}
