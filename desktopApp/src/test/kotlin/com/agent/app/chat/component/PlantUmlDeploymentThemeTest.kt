package com.agent.app.chat.component

import kotlin.test.*

/** 普通部署节点不能继承默认浅色填充与应用浅色文字的组合。 */
class PlantUmlDeploymentThemeTest {
    /** 每种部署节点分别渲染，防止某一种节点的正确填充掩盖另一种的遗漏。 */
    @Test
    fun appliesColorsToEachDeploymentElement() {
        for (kind in listOf("rectangle", "node", "database", "cloud", "artifact", "card", "file", "folder", "frame", "hexagon", "queue", "stack", "storage", "usecase", "state", "object")) {
            for (dark in listOf(true, false)) {
                val svg = renderPlantUmlToSvg("@startuml\n$kind \"中文节点\" as N\n@enduml", dark)
                assertTrue(svg.contains(if (dark) "#31343C" else "#FFFFFF", true), "$kind dark=$dark 缺少背景")
                assertTrue(svg.contains(if (dark) "#E7EAF0" else "#1F2329", true), "$kind dark=$dark 缺少文字颜色")
                assertFalse(svg.contains("#F1F1F1", true), "$kind 残留默认填充")
            }
        }
    }

    /** 显式节点颜色必须继续优先于应用注入的默认值。 */
    @Test
    fun retainsExplicitRectangleColor() {
        val svg = renderPlantUmlToSvg("@startuml\nskinparam RectangleBackgroundColor #123456\nskinparam RectangleFontColor #FEDCBA\nrectangle \"自定义\" as N\n@enduml", true)
        assertTrue(svg.contains("#123456", true))
        assertTrue(svg.contains("#FEDCBA", true))
    }
    /** 新日志中的 rectangle 分支图须有暗色背景与可见的轮廓文字。 */
    @Test
    fun rendersBranchRectanglesFromLog() {
        val svg = renderPlantUmlToSvg(BRANCH_RECTANGLE_DIAGRAM, true)
        assertTrue(svg.contains("#31343C", true), "rectangle 缺少暗色填充")
        assertFalse(svg.contains("#F1F1F1", true), "rectangle 残留默认浅色填充")
        assertTrue(svg.contains("#E7EAF0", true))
        assertFalse(svg.contains("<text"))
    }
}

/** 保留本次日志的原始节点类型、换行和箭头标签。 */
internal val BRANCH_RECTANGLE_DIAGRAM = """
@startuml
left to right direction
rectangle "main = main/main\n90c73a6" as M
rectangle "codex/settings-hooks-resource-refactor\n7876e04" as B
M --> B : 5 commits
@enduml
""".trimIndent()
