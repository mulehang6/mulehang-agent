package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertTrue

/** 验证本地图表渲染在用户主题存在时仍保留应用的中文字体覆盖。 */
class PlantUmlRendererTest {

    /** `!theme plain` 之后必须继续应用桌面字体，避免关系中文标签退化为缺字方块。 */
    @Test
    fun `should apply app font overrides after a user plantuml theme`() {
        val source = """
            @startuml
            !theme plain
            shared --> desktopApp : 反向依赖不允许
            @enduml
        """.trimIndent()

        val themed = applyPlantUmlDarkTheme(source)

        assertTrue(themed.indexOf("!theme plain") < themed.lastIndexOf("defaultFontName Microsoft YaHei UI"))
        assertTrue(themed.lastIndexOf("defaultFontName Microsoft YaHei UI") < themed.indexOf("@enduml"))
    }
}
