package com.agent.app.chat.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.agent.app.design.AppAccent
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.shared.agent.resource.AgentResourceOrigin
import com.agent.shared.agent.resource.AgentSkillResource
import java.nio.file.Path
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** 显示当前资源快照中由用户级 `.agents/skills` 自动发现的 Skills。 */
@Composable
internal fun AutoLoadedSkillsCard(
    userHome: Path,
    loadedSkills: List<AgentSkillResource>,
) {
    val agentsSkillsRoot = userHome.resolve(".agents/skills")
    val autoLoadedSkills = autoLoadedUserAgentsSkills(loadedSkills, userHome)
    ExtensionSettingsCard {
        Text("自动加载的 Skills", style = JewelTheme.defaultTextStyle.copy(color = AppText))
        if (autoLoadedSkills.isEmpty()) {
            Text("当前未从 $agentsSkillsRoot 加载 Skill。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
        } else {
            Text(
                "已加载 ${autoLoadedSkills.size} 个 Skill",
                style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
            )
            autoLoadedSkills.forEach { skill ->
                Column {
                    Text(skill.name, style = JewelTheme.defaultTextStyle.copy(color = AppText))
                    LoadedSkillDescription(skill.fullDescription)
                }
            }
        }
    }
}

/** 在一行内显示 Skill 描述，让提示符紧贴截断位置。 */
@Composable
private fun LoadedSkillDescription(description: String) {
    var expanded by remember(description) { mutableStateOf(false) }
    val textStyle = JewelTheme.defaultTextStyle.copy(color = AppMuted)
    if (expanded) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = description,
                modifier = Modifier.fillMaxWidth(),
                style = textStyle,
            )
            DescriptionToggle(
                label = "收起",
                contentDescription = "收起完整 Skill 描述",
                onClick = { expanded = false },
            )
        }
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val textMeasurer = rememberTextMeasurer()
        val availableWidth = with(LocalDensity.current) { maxWidth.roundToPx() }
        val visibleDescription = remember(description, availableWidth, textStyle) {
            truncateSkillDescription(description, availableWidth, textStyle, textMeasurer)
        }
        val isTruncated = visibleDescription.length < description.length
        if (isTruncated) {
            Row(modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = visibleDescription,
                    maxLines = 1,
                    style = textStyle,
                )
                DescriptionToggle(
                    label = "...",
                    contentDescription = "展开完整 Skill 描述",
                    onClick = { expanded = true },
                )
            }
        } else {
            BasicText(
                text = description,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                style = textStyle,
            )
        }
    }
}

/** 渲染仅文字自身可命中的描述展开与收起操作。 */
@Composable
private fun DescriptionToggle(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    BasicText(
        text = label,
        modifier = Modifier
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        style = JewelTheme.defaultTextStyle.copy(
            color = AppAccent,
            textDecoration = if (hovered) TextDecoration.Underline else TextDecoration.None,
        ),
    )
}

/** 截取可与末尾提示符一起放入给定宽度的一段文本。 */
private fun truncateSkillDescription(
    description: String,
    availableWidth: Int,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
): String {
    if (availableWidth <= 0 || textMeasurer.measure(description, style = textStyle, softWrap = false).size.width <= availableWidth) {
        return description
    }
    val ellipsisWidth = textMeasurer.measure("...", style = textStyle, softWrap = false).size.width
    val textWidthLimit = (availableWidth - ellipsisWidth).coerceAtLeast(0)
    var lowerBound = 0
    var upperBound = description.length
    while (lowerBound < upperBound) {
        val candidateLength = (lowerBound + upperBound + 1) / 2
        val candidateWidth = textMeasurer.measure(
            description.take(candidateLength),
            style = textStyle,
            softWrap = false,
        ).size.width
        if (candidateWidth <= textWidthLimit) {
            lowerBound = candidateLength
        } else {
            upperBound = candidateLength - 1
        }
    }
    return description.take(lowerBound)
}

/** 仅保留本轮由默认用户级 `.agents/skills` 根加载的 Skill。 */
internal fun autoLoadedUserAgentsSkills(
    loadedSkills: List<AgentSkillResource>,
    userHome: Path,
): List<AgentSkillResource> {
    val agentsSkillsRoot = userHome.resolve(".agents/skills").toAbsolutePath().normalize()
    return loadedSkills.filter { skill ->
        skill.origin == AgentResourceOrigin.USER_AUTO_DISCOVERY &&
                skill.location.toAbsolutePath().normalize().startsWith(agentsSkillsRoot)
    }
}
