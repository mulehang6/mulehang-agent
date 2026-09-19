@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.SimpleListItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.LocalDesktopPalette
import com.agent.shared.settings.model.ConfigLayer
import java.awt.Toolkit
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** 宽屏设置分类栏的固定列宽，给表单内容保留足够可用空间。 */
internal const val SETTINGS_NAVIGATION_WIDE_WIDTH_DP = 120
internal const val SETTINGS_SUBMENU_EXPAND_DURATION_MILLIS = 180
internal const val SETTINGS_SUBMENU_COLLAPSE_DURATION_MILLIS = 140

private const val SETTINGS_SUBMENU_REDUCED_MOTION_DURATION_MILLIS = 120
private val SettingsSubmenuMotionEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

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
    onSectionChange: (SettingsSection) -> Unit,
    onParentClick: (SettingsSection) -> Unit,
    compact: Boolean = false,
    expandedSections: Set<SettingsSection> = emptySet(),
    appearanceSubsections: List<AppearanceSubsection> = AppearanceSubsection.entries,
    appearanceSubsection: AppearanceSubsection = AppearanceSubsection.OVERVIEW,
    onAppearanceSubsectionChange: (AppearanceSubsection) -> Unit = {},
    extensionSubsections: List<ExtensionSubsection> = ExtensionSubsection.entries,
    extensionSubsection: ExtensionSubsection = ExtensionSubsection.OVERVIEW,
    onExtensionSubsectionChange: (ExtensionSubsection) -> Unit = {},
) {
    fun hasSubsections(entry: SettingsSection): Boolean = when (entry) {
        SettingsSection.APPEARANCE -> appearanceSubsections.isNotEmpty()
        SettingsSection.EXTENSIONS -> extensionSubsections.isNotEmpty()
        SettingsSection.TOOLS,
        SettingsSection.PROVIDERS,
            -> false
    }

    fun selectSection(entry: SettingsSection) {
        if (hasSubsections(entry)) onParentClick(entry) else onSectionChange(entry)
    }

    if (compact) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ListComboBox(
                items = sections, selectedIndex = sections.indexOf(section),
                onSelectedItemChange = { sections.getOrNull(it)?.let(::selectSection) },
                itemKeys = { _, entry -> entry.name }, modifier = Modifier.weight(1f),
                style = rememberProviderProtocolComboBoxStyle(),
            ) { entry, selected, active -> SimpleListItem(entry.label, selected, active) }
            if (section == SettingsSection.APPEARANCE && appearanceSubsections.isNotEmpty()) {
                SettingsSubsectionComboBox(
                    labels = appearanceSubsections.map(AppearanceSubsection::label),
                    keys = appearanceSubsections.map(AppearanceSubsection::name),
                    selectedIndex = appearanceSubsections.indexOf(appearanceSubsection).coerceAtLeast(0),
                    onSelectedItemChange = {
                        appearanceSubsections.getOrNull(it)?.let(onAppearanceSubsectionChange)
                    },
                    modifier = Modifier.weight(1f),
                )
            } else if (section == SettingsSection.EXTENSIONS && extensionSubsections.isNotEmpty()) {
                SettingsSubsectionComboBox(
                    labels = extensionSubsections.map(ExtensionSubsection::label),
                    keys = extensionSubsections.map(ExtensionSubsection::name),
                    selectedIndex = extensionSubsections.indexOf(extensionSubsection).coerceAtLeast(0),
                    onSelectedItemChange = {
                        extensionSubsections.getOrNull(it)?.let(onExtensionSubsectionChange)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }
    val reducedMotion = prefersReducedMotion()
    Column(
        modifier = Modifier.width(SETTINGS_NAVIGATION_WIDE_WIDTH_DP.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { entry ->
            val hasChildren = hasSubsections(entry)
            val expanded = entry in expandedSections
            SettingsNavigationItem(
                label = entry.label,
                selected = entry == section,
                hasChildren = hasChildren,
                expanded = expanded,
                reducedMotion = reducedMotion,
                onClick = { selectSection(entry) },
            )
            if (entry == SettingsSection.APPEARANCE && appearanceSubsections.isNotEmpty()) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = settingsSubmenuEnterTransition(reducedMotion),
                    exit = settingsSubmenuExitTransition(reducedMotion),
                ) {
                    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        appearanceSubsections.forEach { subsection ->
                            SettingsNavigationItem(
                                subsection.label,
                                selected = section == entry && appearanceSubsection == subsection,
                                onClick = { onAppearanceSubsectionChange(subsection) },
                            )
                        }
                    }
                }
            }
            if (entry == SettingsSection.EXTENSIONS && extensionSubsections.isNotEmpty()) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = settingsSubmenuEnterTransition(reducedMotion),
                    exit = settingsSubmenuExitTransition(reducedMotion),
                ) {
                    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        extensionSubsections.forEach { subsection ->
                            SettingsNavigationItem(
                                subsection.label,
                                selected = section == entry && extensionSubsection == subsection,
                                onClick = { onExtensionSubsectionChange(subsection) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 窄屏二级下拉共用 Jewel 样式和选项渲染，保持外观与扩展导航一致。 */
@Composable
private fun SettingsSubsectionComboBox(
    labels: List<String>,
    keys: List<String>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    modifier: Modifier,
) {
    ListComboBox(
        items = labels,
        selectedIndex = selectedIndex,
        onSelectedItemChange = onSelectedItemChange,
        itemKeys = { index, _ -> keys[index] },
        modifier = modifier,
        style = rememberProviderProtocolComboBoxStyle(),
    ) { entry, selected, active ->
        SimpleListItem(entry, selected, active)
    }
}

/** 绘制宽屏设置分类的 28dp Islands 选中态，避免使用偏小的默认列表行。 */
@Composable
private fun SettingsNavigationItem(
    label: String,
    selected: Boolean,
    hasChildren: Boolean = false,
    expanded: Boolean = false,
    reducedMotion: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val shape = RoundedCornerShape(7.dp)
    val hoverInteractionSource = remember { MutableInteractionSource() }
    val hovered by hoverInteractionSource.collectIsHoveredAsState()
    val fill = when {
        selected -> islandsTabSelectedFill(palette.isDark)
        hasChildren && hovered -> palette.hoverBackground
        else -> Color.Transparent
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(
                durationMillis = SETTINGS_SUBMENU_EXPAND_DURATION_MILLIS,
                easing = SettingsSubmenuMotionEasing,
            )
        },
        label = "settings-parent-arrow-rotation",
    )
    val arrowAlpha by animateFloatAsState(
        targetValue = if (!hasChildren || expanded || hovered) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 120),
        label = "settings-parent-arrow-alpha",
    )
    val border = if (selected) islandsTabSelectedBorder(palette.isDark) else Color.Transparent
    Tooltip(tooltip = { Text(label) }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(shape)
                .background(fill)
                .border(1.dp, border, shape)
                .hoverable(hoverInteractionSource)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    if (hasChildren) stateDescription = if (expanded) "已展开" else "已收起"
                }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) AppText else AppMuted,
                )
                if (hasChildren) {
                    Icon(
                        key = AllIconsKeys.General.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier
                            .alpha(arrowAlpha)
                            .rotate(arrowRotation)
                            .padding(start = 4.dp)
                            .width(14.dp)
                            .height(14.dp),
                        tint = if (selected) AppText else AppMuted,
                    )
                }
            }
        }
    }
}

/** 宽屏子菜单从父项下方展开，减弱动态效果时只保留短淡入。 */
private fun settingsSubmenuEnterTransition(reducedMotion: Boolean): EnterTransition = if (reducedMotion) {
    fadeIn(tween(SETTINGS_SUBMENU_REDUCED_MOTION_DURATION_MILLIS))
} else {
    expandVertically(
        animationSpec = tween(
            SETTINGS_SUBMENU_EXPAND_DURATION_MILLIS,
            easing = SettingsSubmenuMotionEasing,
        ),
        expandFrom = Alignment.Top,
    ) + fadeIn(
        tween(
            SETTINGS_SUBMENU_EXPAND_DURATION_MILLIS,
            easing = SettingsSubmenuMotionEasing,
        ),
    )
}

/** 宽屏子菜单向父项收起，退出速度略快于展开以保持导航响应。 */
private fun settingsSubmenuExitTransition(reducedMotion: Boolean): ExitTransition = if (reducedMotion) {
    fadeOut(tween(SETTINGS_SUBMENU_REDUCED_MOTION_DURATION_MILLIS))
} else {
    shrinkVertically(
        animationSpec = tween(
            SETTINGS_SUBMENU_COLLAPSE_DURATION_MILLIS,
            easing = SettingsSubmenuMotionEasing,
        ),
        shrinkTowards = Alignment.Top,
    ) + fadeOut(
        tween(
            SETTINGS_SUBMENU_COLLAPSE_DURATION_MILLIS,
            easing = SettingsSubmenuMotionEasing,
        ),
    )
}

/** 读取 Windows 动画偏好，并允许测试通过系统属性显式覆盖。 */
internal fun prefersReducedMotion(): Boolean {
    System.getProperty("mulehang.reducedMotion")?.toBooleanStrictOrNull()?.let { return it }
    return Toolkit.getDefaultToolkit().getDesktopProperty("win.animation") == false
}
