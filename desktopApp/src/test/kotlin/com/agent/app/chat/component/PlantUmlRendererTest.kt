package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证用户指定的 PlantUML 主题拥有最高优先级。 */
class PlantUmlRendererTest {
    /** 类图和时序图在明暗主题下都使用成对的背景与文字颜色。 */
    @Test
    fun rendersCommonDiagramFamiliesInBothThemes() {
        for (body in listOf("class 用户 {\n名字: String\n}\n用户 --> 服务", "actor 用户\nparticipant 服务\n用户 -> 服务: 中文请求\nactivate 服务\n服务 --> 用户: 完成")) {
            for (dark in listOf(true, false)) {
                val svg = renderPlantUmlToSvg("@startuml\n$body\n@enduml", dark)
                assertTrue(svg.contains(if (dark) "#31343C" else "#FFFFFF", ignoreCase = true))
                assertTrue(svg.contains(if (dark) "#E7EAF0" else "#1F2329", ignoreCase = true))
                assertFalse(svg.contains("<text"))
            }
        }
    }

    /** 用户显式 skinparam 写在注入的默认值之后，轮廓化也必须保留自定义颜色。 */
    @Test
    fun preservesExplicitComponentColorsThroughOutlining() {
        val svg = renderPlantUmlToSvg("@startuml\nskinparam ComponentBackgroundColor #123456\nskinparam ComponentFontColor #FEDCBA\n[中文]\n@enduml", true)
        assertTrue(svg.contains("#123456", ignoreCase = true))
        assertTrue(svg.contains("#FEDCBA", ignoreCase = true))
        assertFalse(svg.contains("<text"))
    }

    /** 日志中的组件图必须在暗色主题下同时设置节点背景与文字颜色。 */
    @Test
    fun rendersReadableComponentDiagramFromRunLog() {
        val source = LOG_COMPONENT_DIAGRAM
        val svg = renderPlantUmlToSvg(source, isDark = true)
        assertTrue(svg.contains("#31343C", ignoreCase = true), "组件必须使用暗色背景")
        assertFalse(svg.contains("#F1F1F1", ignoreCase = true), "不能保留默认浅色组件背景")
        assertTrue(svg.contains("#E7EAF0", ignoreCase = true))
        assertFalse(svg.contains("<text"), "中文标签应转换为路径")
    }

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
}

/** 脱敏后的原始日志组件图，渲染与截图测试共用。 */
internal val LOG_COMPONENT_DIAGRAM = """
            @startuml
            skinparam componentStyle rectangle
            package "shared (KMP 契约 + JVM 实现)" {
              [agent\nKoog 网关/流式事件/provider 适配]
              [chat\n消息模型/发送用例/SQLite 持久化]
              [settings\n分层配置解析与合并]
              [tool\n本地工具运行时/审批/审计]
            }
            package "desktopApp (Compose Desktop UI)" {
              [bootstrap 入口/窗口]
              [chat UI\n时间线/composer/设置面板]
              [tool UI 审批卡片/diff 预览]
              [design Jewel 主题/Islands]
              [platform Windows 标题栏/剪贴板/终端]
            }
            package "agent-ui-prototype1 (React+Vite, 原型)" {
              [交互原型验证]
            }
            [desktopApp] --> [shared]
            [agent-ui-prototype1] ..> [desktopApp] : 仅参考交互
            @enduml
        """.trimIndent()
