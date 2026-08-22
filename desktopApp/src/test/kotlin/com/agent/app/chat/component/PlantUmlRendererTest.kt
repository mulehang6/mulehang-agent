package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证用户指定的 PlantUML 主题拥有最高优先级。 */
class PlantUmlRendererTest {

    /** 自动主题应覆盖活动节点、条件菱形、起止节点、箭头及其标签的对比度。 */
    @Test
    fun appliesReadableActivityDiagramColorsWhenThemeIsNotSpecified() {
        val source = """
            @startuml
            start
            :输入账号密码;
            if (校验通过?) then (是)
              :进入首页;
            else (否)
              :提示错误;
            endif
            stop
            @enduml
        """.trimIndent()

        val dark = applyPlantUmlTheme(source, isDark = true)
        val light = applyPlantUmlTheme(source, isDark = false)

        assertTrue(dark.contains("ActivityBackgroundColor #31343C"))
        assertTrue(dark.contains("ActivityDiamondFontColor #F4F7FC"))
        assertTrue(dark.contains("ActivityStartColor #B6C2DA"))
        assertTrue(dark.contains("ArrowFontColor #F4F7FC"))
        assertTrue(light.contains("ActivityBackgroundColor #FFFFFF"))
        assertTrue(light.contains("ActivityDiamondBorderColor #596273"))
        assertTrue(light.contains("ActivityStopColor #596273"))
        assertTrue(light.contains("ArrowFontColor #1F2329"))
    }

    /** 活动图渲染出的 SVG 应实际带有自动主题的节点、箭头和标签颜色。 */
    @Test
    fun rendersAutomaticActivityThemeIntoSvg() {
        val source = """
            @startuml
            start
            :输入账号密码;
            if (校验通过?) then (是)
              :进入首页;
            else (否)
              :提示错误;
            endif
            stop
            @enduml
        """.trimIndent()

        val svg = renderPlantUmlToSvg(source, isDark = true)
        val renderedColors = Regex("#[0-9a-fA-F]{6}")
            .findAll(svg)
            .map { it.value }
            .toSet()

        assertFalse(svg.contains("Syntax Error"))
        assertTrue(renderedColors.any { it.equals("#31343C", ignoreCase = true) }, "SVG 颜色：$renderedColors")
        assertTrue(renderedColors.any { it.equals("#B6C2DA", ignoreCase = true) }, "SVG 颜色：$renderedColors")
        assertTrue(renderedColors.any { it.equals("#F4F7FC", ignoreCase = true) }, "SVG 颜色：$renderedColors")
    }

    /** 避免自动主题在用户已使用 !theme 时重新改写源码。 */
    @Test
    fun keepsExplicitPlantUmlThemeUnchanged() {
        val source = """
            @startuml
            !theme plain
            shared --> desktopApp : 反向依赖不允许
            @enduml
        """.trimIndent()

        assertEquals(source, applyPlantUmlTheme(source, isDark = true))
    }

    /** 自动主题还应改写时序图参与人、actor、数据库与消息箭头的光暗配色。 */
    @Test
    fun appliesReadableSequenceDiagramColorsWhenThemeIsNotSpecified() {
        val source = SEQUENCE_DIAGRAM_SOURCE

        val dark = applyPlantUmlTheme(source, isDark = true)
        val light = applyPlantUmlTheme(source, isDark = false)

        assertTrue(dark.contains("sequenceParticipantBackgroundColor #31343C"))
        assertTrue(dark.contains("sequenceActorBackgroundColor #31343C"))
        assertTrue(dark.contains("databaseBackgroundColor #31343C"))
        assertTrue(dark.contains("sequenceParticipantBorderColor #6E7A92"))
        assertTrue(dark.contains("sequenceArrowColor #B6C2DA"))
        assertTrue(dark.contains("sequenceArrowFontColor #F4F7FC"))
        assertTrue(light.contains("sequenceParticipantBackgroundColor #FFFFFF"))
        assertTrue(light.contains("databaseBackgroundColor #FFFFFF"))
        assertTrue(light.contains("sequenceArrowColor #4F5B70"))
        assertTrue(light.contains("sequenceArrowFontColor #1F2329"))
    }

    /** 时序图暗色渲染不应再出现默认浅紫参与人底色，确保浅色文字可见。 */
    @Test
    fun rendersSequenceDiagramWithoutDefaultLightParticipantColors() {
        val svg = renderPlantUmlToSvg(SEQUENCE_DIAGRAM_SOURCE, isDark = true)
        val renderedColors = Regex("#[0-9a-fA-F]{6}")
            .findAll(svg)
            .map { it.value }
            .toSet()

        assertFalse(svg.contains("Syntax Error"))
        assertFalse(renderedColors.any { it.equals("#E2E2F0", ignoreCase = true) }, "SVG 颜色：$renderedColors")
        assertTrue(renderedColors.any { it.equals("#31343C", ignoreCase = true) }, "SVG 颜色：$renderedColors")
        assertTrue(renderedColors.any { it.equals("#B6C2DA", ignoreCase = true) }, "SVG 颜色：$renderedColors")
    }

    private companion object {
        val SEQUENCE_DIAGRAM_SOURCE = """
            @startuml
            actor 用户
            participant "Mulehang 桌面应用" as App
            participant "Agent" as Agent
            database "工具" as Tool

            用户 -> App: 发送消息
            App -> Agent: 转发输入
            Agent -> Tool: 调用工具
            Tool --> Agent: 返回结果
            Agent --> App: 流式事件
            App --> 用户: 渲染回复
            @enduml
        """.trimIndent()
    }
}
