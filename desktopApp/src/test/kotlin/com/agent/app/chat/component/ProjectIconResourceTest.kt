package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 验证标题栏项目图标会随桌面应用资源一并打包。 */
class ProjectIconResourceTest {
    @Test
    fun `标题栏项目图标以 SVG 资源打包`() {
        val resource = assertNotNull(
            javaClass.classLoader.getResource(
                "composeResources/mulehang_agent.desktopapp.generated.resources/drawable/mulehang_agent.svg",
            ),
        )
        val content = resource.openStream().use { input -> input.readBytes().decodeToString() }

        assertTrue(content.contains("<svg"))
    }
}
