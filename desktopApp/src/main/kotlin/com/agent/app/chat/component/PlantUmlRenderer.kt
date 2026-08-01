package com.agent.app.chat.component

import java.io.ByteArrayOutputStream
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader

/**
 * 在当前 JVM 内将 PlantUML 文本渲染为 SVG，不启动浏览器、不访问网络。
 */
internal fun renderPlantUmlToSvg(source: String): String {
    val output = ByteArrayOutputStream()
    SourceStringReader(source).outputImage(
        output,
        FileFormatOption(FileFormat.SVG),
    )
    return output.toString(Charsets.UTF_8)
}
