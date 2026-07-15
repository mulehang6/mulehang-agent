package com.agent.shared.session

import com.agent.shared.settings.persistence.DesktopEnvironmentOverrides
import com.agent.shared.settings.persistence.DesktopPathResolver
import com.agent.shared.settings.persistence.DesktopSettingsRepository
import com.agent.shared.settings.model.ConfigProfile
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 基于桌面文件系统的应用会话仓库。
 */
class DesktopAppSessionRepository(
    projectRoot: Path,
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
    private val uiStateStore = DesktopUiStateStore(userHome.resolve(".mulehang/ui-state.json"))

    /**
     * 加载双层 settings 合并后的 profile 列表。
     */
    override suspend fun loadProfiles(): List<ConfigProfile> = settingsRepository.loadResolvedProfiles()

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
