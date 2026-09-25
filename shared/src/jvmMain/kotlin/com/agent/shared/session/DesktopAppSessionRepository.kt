package com.agent.shared.session

import com.agent.shared.settings.persistence.DesktopEnvironmentOverrides
import com.agent.shared.settings.persistence.DesktopPathResolver
import com.agent.shared.settings.persistence.DesktopSettingsRepository
import com.agent.shared.settings.model.AgentHookSettings
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 基于桌面文件系统的应用会话仓库。
 */
class DesktopAppSessionRepository(
    projectRoot: Path,
    private val uiStateStore: DesktopUiStateStore,
    userHome: Path = Paths.get(System.getProperty("user.home")),
) : AppSessionRepository {
    private val pathResolver = DesktopPathResolver(
        userHome = userHome,
        projectRoot = projectRoot,
    )
    private val settingsRepository = DesktopSettingsRepository(
        pathResolver = pathResolver,
        environmentOverrides = DesktopEnvironmentOverrides(),
    )
    /**
     * 加载双层 settings 合并后的 profile 列表。
     */
    override suspend fun loadProfiles(): List<ConfigProfile> = settingsRepository.loadResolvedProfiles()

    /** 从同一份 settings 文档加载低延迟内部任务共用的快速模型。 */
    override suspend fun loadFasterProfiles(): Map<String, ConfigProfile> = settingsRepository.loadResolvedFasterProfiles()

    /** Hook 仅从用户层加载，项目 settings 永远不会参与命令执行。 */
    override suspend fun loadHookSettings(): AgentHookSettings =
        settingsRepository.loadDocument(ConfigLayer.USER).hooks

    /** 环境覆盖用户级设置；非法值退回有效用户设置或默认值。 */
    override suspend fun loadContextCompactionThresholdPercent(): Int {
        val userValue = settingsRepository.loadDocument(ConfigLayer.USER).contextCompactionThresholdPercent
        val environmentValue = System.getenv("MULEHANG_CONTEXT_COMPACTION_THRESHOLD_PERCENT")?.toIntOrNull()
        return listOfNotNull(environmentValue, userValue, 80).first { it in 50..95 && it % 5 == 0 }
    }

    /**
     * 读取当前项目上次选择的 profile id。
     */
    override suspend fun loadRememberedProfileId(): String? =
        uiStateStore.loadSelectedProfile(pathResolver.projectRoot.toString())

    /**
     * 保存当前项目最终选择的 profile id。
     */
    override suspend fun saveRememberedProfileId(profileId: String) {
        uiStateStore.saveSelectedProfile(pathResolver.projectRoot.toString(), profileId)
    }
}
