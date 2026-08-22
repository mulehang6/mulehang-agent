package com.agent.app.chat.component

import java.io.ByteArrayOutputStream
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader

/**
 * 在当前 JVM 内生成 PlantUML SVG。渲染器不访问网络，也不修改 PlantUML 输出的 SVG。
 */
internal fun renderPlantUmlToSvg(
    source: String,
    isDark: Boolean,
): String {
    val output = ByteArrayOutputStream()
    SourceStringReader(applyPlantUmlTheme(source, isDark)).outputImage(
        output,
        FileFormatOption(FileFormat.SVG),
    )
    return output.toString(Charsets.UTF_8)
}

/**
 * 在用户没有指定 `!theme` 时写入应用主题的最小 PlantUML 外观配置。
 */
internal fun applyPlantUmlTheme(
    source: String,
    isDark: Boolean,
): String {
    if (USER_THEME_DIRECTIVE.containsMatchIn(source)) return source
    val configuration = if (isDark) PLANT_UML_DARK_THEME else PLANT_UML_LIGHT_THEME
    val startMatch = PLANT_UML_START.find(source) ?: return source
    return source.replaceRange(
        startMatch.range.last + 1,
        startMatch.range.last + 1,
        "\n$configuration",
    )
}

private val PLANT_UML_START = Regex("(?im)^\\s*@start[A-Za-z0-9_]*[^\\r\\n]*$")
private val USER_THEME_DIRECTIVE = Regex("(?im)^\\s*!theme\\b")

private const val PLANT_UML_DARK_THEME = """
skinparam backgroundColor transparent
skinparam defaultFontColor #E7EAF0
skinparam defaultBorderColor #6E7A92
skinparam ArrowColor #B6C2DA
skinparam ArrowFontColor #F4F7FC
skinparam sequenceParticipantBackgroundColor #31343C
skinparam sequenceParticipantBorderColor #6E7A92
skinparam sequenceActorBackgroundColor #31343C
skinparam sequenceActorBorderColor #6E7A92
skinparam databaseBackgroundColor #31343C
skinparam databaseBorderColor #6E7A92
skinparam sequenceArrowColor #B6C2DA
skinparam sequenceArrowFontColor #F4F7FC
skinparam ActivityBackgroundColor #31343C
skinparam ActivityBorderColor #B6C2DA
skinparam ActivityFontColor #F4F7FC
skinparam ActivityDiamondBackgroundColor #393E49
skinparam ActivityDiamondBorderColor #B6C2DA
skinparam ActivityDiamondFontColor #F4F7FC
skinparam ActivityStartColor #B6C2DA
skinparam ActivityEndColor #B6C2DA
skinparam ActivityStopColor #B6C2DA
skinparam ActivityBarColor #B6C2DA
skinparam NoteBackgroundColor #31343C
skinparam NoteBorderColor #6E7A92
skinparam NoteFontColor #E7EAF0
<style>
document {
  BackgroundColor transparent
}
activityDiagram {
  BackgroundColor #31343C
  LineColor #B6C2DA
  FontColor #F4F7FC
  diamond {
    BackgroundColor #393E49
    LineColor #B6C2DA
    FontColor #F4F7FC
  }
  arrow {
    LineColor #B6C2DA
    FontColor #F4F7FC
  }
  circle {
    start {
      BackgroundColor #B6C2DA
      LineColor #B6C2DA
    }
    end {
      BackgroundColor #B6C2DA
      LineColor #B6C2DA
    }
    stop {
      BackgroundColor #B6C2DA
      LineColor #B6C2DA
    }
  }
}
</style>
"""

private const val PLANT_UML_LIGHT_THEME = """
skinparam backgroundColor transparent
skinparam defaultFontColor #1F2329
skinparam defaultBorderColor #6B7180
skinparam ArrowColor #4F5B70
skinparam ArrowFontColor #1F2329
skinparam sequenceParticipantBackgroundColor #FFFFFF
skinparam sequenceParticipantBorderColor #596273
skinparam sequenceActorBackgroundColor #FFFFFF
skinparam sequenceActorBorderColor #596273
skinparam databaseBackgroundColor #FFFFFF
skinparam databaseBorderColor #596273
skinparam sequenceArrowColor #4F5B70
skinparam sequenceArrowFontColor #1F2329
skinparam ActivityBackgroundColor #FFFFFF
skinparam ActivityBorderColor #596273
skinparam ActivityFontColor #1F2329
skinparam ActivityDiamondBackgroundColor #F4F5F7
skinparam ActivityDiamondBorderColor #596273
skinparam ActivityDiamondFontColor #1F2329
skinparam ActivityStartColor #596273
skinparam ActivityEndColor #596273
skinparam ActivityStopColor #596273
skinparam ActivityBarColor #596273
skinparam NoteBackgroundColor #FFFFFF
skinparam NoteBorderColor #6B7180
skinparam NoteFontColor #1F2329
<style>
document {
  BackgroundColor transparent
}
activityDiagram {
  BackgroundColor #FFFFFF
  LineColor #596273
  FontColor #1F2329
  diamond {
    BackgroundColor #F4F5F7
    LineColor #596273
    FontColor #1F2329
  }
  arrow {
    LineColor #4F5B70
    FontColor #1F2329
  }
  circle {
    start {
      BackgroundColor #596273
      LineColor #596273
    }
    end {
      BackgroundColor #596273
      LineColor #596273
    }
    stop {
      BackgroundColor #596273
      LineColor #596273
    }
  }
}
</style>
"""
