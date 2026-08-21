package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.LocalDesktopPalette
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabState
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.TabColors
import org.jetbrains.jewel.ui.component.styling.TabMetrics
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.editorTabStyle

/** Islands 风格页签的不可变描述，保留 Jewel 页签的选择和键盘行为。 */
internal data class IslandsTab(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val iconKey: IconKey? = null,
    val closable: Boolean = false,
    val onClose: () -> Unit = {},
    val modifier: Modifier = Modifier,
)

/**
 * 用 Jewel [TabStrip] 承载接近 IDEA Islands 的 28dp 圆角页签。
 *
 * 外层仍由 Jewel 提供焦点、方向键与自动滚入选中项；关闭按钮放在视觉胶囊内，
 * 因而描边能完整包住图标、标题和关闭动作。
 */
@Composable
internal fun IslandsTabStrip(
    tabs: List<IslandsTab>,
    modifier: Modifier = Modifier,
) {
    TabStrip(
        tabs = tabs.map(IslandsTab::toJewelTab),
        style = rememberIslandsTabStyle(),
        modifier = modifier,
    )
}

/** 将 Islands 说明转换为 Jewel 编辑器页签，同时把关闭图标留在自定义内容中。 */
private fun IslandsTab.toJewelTab(): TabData.Editor = TabData.Editor(
    selected = selected,
    closable = false,
    onClick = onClick,
    // 关闭按钮由 Islands 胶囊内部的可访问 action 处理；禁用 Jewel 的第三键关闭，
    // 以免终端的右键菜单和设置标题产生意外关闭。
    onClose = {},
    content = { state ->
        IslandsTabContent(
            tab = this@toJewelTab,
            state = state,
        )
    },
)

/** 绘制单个 Islands 页签的填充、描边、图标、标题与可选关闭动作。 */
@Composable
private fun IslandsTabContent(
    tab: IslandsTab,
    state: TabState,
) {
    val palette = LocalDesktopPalette.current
    val selected = state.isSelected
    val shape = RoundedCornerShape(ISLANDS_TAB_CORNER_RADIUS)
    val fill = when {
        selected -> islandsTabSelectedFill(isDark = palette.isDark)
        state.isHovered -> palette.hoverBackground.copy(alpha = ISLANDS_TAB_HOVER_ALPHA)
        else -> Color.Transparent
    }
    val border = if (selected) islandsTabSelectedBorder(isDark = palette.isDark) else Color.Transparent
    val iconTint = if (selected) AppText else AppMuted

    Row(
        modifier = tab.modifier
            .height(ISLANDS_TAB_HEIGHT)
            .clip(shape)
            .background(fill)
            .border(ISLANDS_TAB_BORDER_WIDTH, border, shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        tab.iconKey?.let { key ->
            Icon(
                key = key,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconTint,
            )
        }
        Text(
            text = tab.label,
            modifier = Modifier.padding(start = if (tab.iconKey == null) 0.dp else 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = AppText,
        )
        if (tab.closable) {
            Icon(
                key = JewelTheme.editorTabStyle.icons.close,
                contentDescription = "关闭 ${tab.label}",
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp)
                    .clickable(
                        role = Role.Button,
                        onClick = tab.onClose,
                    ),
                tint = iconTint,
            )
        }
    }
}

/** 创建透明轨道，避免 Jewel 默认的下划线与 Islands 胶囊叠加。 */
@Composable
private fun rememberIslandsTabStyle(): TabStyle {
    val editorStyle = JewelTheme.editorTabStyle
    return remember(editorStyle) {
        val transparent = Color.Transparent
        TabStyle(
            colors = TabColors(
                background = transparent,
                backgroundDisabled = transparent,
                backgroundPressed = transparent,
                backgroundHovered = transparent,
                backgroundSelected = transparent,
                content = editorStyle.colors.content,
                contentDisabled = editorStyle.colors.contentDisabled,
                contentPressed = editorStyle.colors.contentPressed,
                contentHovered = editorStyle.colors.contentHovered,
                contentSelected = editorStyle.colors.contentSelected,
                underline = transparent,
                underlineDisabled = transparent,
                underlinePressed = transparent,
                underlineHovered = transparent,
                underlineSelected = transparent,
            ),
            metrics = TabMetrics(
                underlineThickness = 0.dp,
                tabPadding = PaddingValues(horizontal = 4.dp),
                tabHeight = 36.dp,
                tabContentSpacing = editorStyle.metrics.tabContentSpacing,
                closeContentGap = 0.dp,
            ),
            icons = editorStyle.icons,
            contentAlpha = editorStyle.contentAlpha,
            scrollbarStyle = editorStyle.scrollbarStyle,
        )
    }
}

/** 返回当前主题下 Islands 选中页签的填充颜色。 */
internal fun islandsTabSelectedFill(isDark: Boolean): Color =
    if (isDark) Color(0xFF233558) else Color(0xFFE3EBFE)

/** 返回当前主题下 Islands 选中页签的描边颜色。 */
internal fun islandsTabSelectedBorder(isDark: Boolean): Color =
    if (isDark) Color(0xFF2E4D89) else Color(0xFFA7C5FF)

private val ISLANDS_TAB_HEIGHT = 28.dp
private val ISLANDS_TAB_CORNER_RADIUS = 7.dp
private val ISLANDS_TAB_BORDER_WIDTH = 1.dp
private const val ISLANDS_TAB_HOVER_ALPHA = 0.74f
