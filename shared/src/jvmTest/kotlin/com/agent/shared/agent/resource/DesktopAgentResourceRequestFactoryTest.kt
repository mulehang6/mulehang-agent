package com.agent.shared.agent.resource

import com.agent.shared.settings.model.AgentExtensionPackageSettings
import com.agent.shared.settings.model.AgentResourceSettings
import com.agent.shared.settings.model.SettingsDocument
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证桌面设置层将全局资源放在项目资源之前，供 Kilo 优先级合并。 */
class DesktopAgentResourceRequestFactoryTest {
    /** 用户级扩展包先出现，项目级扩展包可在后续加载阶段覆盖或合并同名资源。 */
    @Test
    fun `should place user extension packages before project extension packages`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val workspace = Files.createTempDirectory("mulehang-resource-workspace")
        val userDocument = SettingsDocument(
            agentResources = AgentResourceSettings(
                extensionPackages = listOf(
                    AgentExtensionPackageSettings(id = "user", source = home.resolve("user-extension").toString()),
                ),
            ),
        )
        val projectDocument = SettingsDocument(
            agentResources = AgentResourceSettings(
                extensionPackages = listOf(
                    AgentExtensionPackageSettings(id = "project", source = workspace.resolve("project-extension").toString()),
                ),
            ),
        )

        val request = DesktopAgentResourceRequestFactory(home).create(
            workspacePath = workspace,
            userDocument = userDocument,
            projectDocument = projectDocument,
        )

        assertEquals(
            listOf(AgentResourceOrigin.USER_CONFIGURATION, AgentResourceOrigin.PROJECT_CONFIGURATION),
            request.packages.map(InstalledAgentExtensionPackage::origin),
        )
    }
}
