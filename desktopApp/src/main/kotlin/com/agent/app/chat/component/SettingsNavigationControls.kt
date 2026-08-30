@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.LocalDesktopPalette
import com.agent.shared.settings.model.ConfigLayer
import java.awt.Toolkit
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

/** 宽屏设置分类栏的固定列宽，给表单内容保留足够可用空间。 */
internal const val SETTINGS_NAVIGATION_WIDE_WIDTH_DP = 96

/** 使用自然宽度的 Islands 页签切换用户级与项目级配置。 */
@Composable
internal fun SettingsScopeBar(
    layer: ConfigLayer,
    projectEnabled: Boolean,
    onLayerChange: (ConfigLayer) -> Unit,
) {
    val scopeTabs = buildList {
        add(
            IslandsTab(
                label = "全局",
                selected = layer == ConfigLayer.USER,
                onClick = { onLayerChange(ConfigLayer.USER) },
            ),
        )
        if (projectEnabled) {
            add(
                IslandsTab(
                    label = "当前项目",
                    selected = layer == ConfigLayer.PROJECT,
                    onClick = { onLayerChange(ConfigLayer.PROJECT) },
                ),
            )
        }
    }
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IslandsTabStrip(tabs = scopeTabs)
            if (!projectEnabled) {
                Text("请选择工作区", color = AppMuted)
            }
        }
        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

/** 按设置 Island 的可用宽度呈现垂直或横向的 Islands 分类导航。 */
@Composable
internal fun SettingsNavigation(
    section: SettingsSection,
    sections: List<SettingsSection> = SettingsSection.entries,
    onSectionChange: (SettingsSection, Boolean) -> Unit,
    compact: Boolean = false,
) {
    if (compact) {
        IslandsTabStrip(
            tabs = sections.map { entry ->
                IslandsTab(
                    label = entry.label,
                    selected = entry == section,
                    onClick = { onSectionChange(entry, false) },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    Column(
        modifier = Modifier.width(SETTINGS_NAVIGATION_WIDE_WIDTH_DP.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { entry ->
            SettingsNavigationItem(
                label = entry.label,
                selected = entry == section,
                onClick = { onSectionChange(entry, false) },
            )
        }
    }
}

/** 绘制宽屏设置分类的 28dp Islands 选中态，避免使用偏小的默认列表行。 */
@Composable
private fun SettingsNavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val shape = RoundedCornerShape(7.dp)
    val fill = if (selected) islandsTabSelectedFill(palette.isDark) else Color.Transparent
    val border = if (selected) islandsTabSelectedBorder(palette.isDark) else Color.Transparent
    Tooltip(tooltip = { Text(label) }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(shape)
                .background(fill)
                .border(1.dp, border, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) AppText else AppMuted,
            )
        }
    }
}

/** 读取 Windows 动画偏好，并允许测试通过系统属性显式覆盖。 */
internal fun prefersReducedMotion(): Boolean {
    System.getProperty("mulehang.reducedMotion")?.toBooleanStrictOrNull()?.let { return it }
    return Toolkit.getDefaultToolkit().getDesktopProperty("win.animation") == false
}
