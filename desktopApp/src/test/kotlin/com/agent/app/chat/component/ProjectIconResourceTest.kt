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

    /** Jewel 使用的 IntelliJ expui 图标必须随 standalone 运行时资源一同解析。 */
    @Test
    fun `Jewel IntelliJ icons should resolve from the runtime classpath`() {
        val classLoader = javaClass.classLoader
        val iconResources = listOf(
            "expui/general/add.svg",
            "expui/general/chevronDown.svg",
            "expui/general/close.svg",
            "expui/general/menu.svg",
            "expui/general/search.svg",
            "expui/general/settings.svg",
            "expui/run/run.svg",
            "expui/general/vcs.svg",
            "debugger/console.svg",
        )

        iconResources.forEach { path -> assertNotNull(classLoader.getResource(path), path) }
    }
}
