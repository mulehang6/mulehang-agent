@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.agent.app.design.DesktopPalette
import com.agent.app.design.LocalDesktopPalette
import com.agent.shared.tool.model.PermissionPreset
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.MenuColors
import org.jetbrains.jewel.ui.component.styling.MenuItemColors
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import org.jetbrains.jewel.ui.theme.iconButtonStyle
import org.jetbrains.jewel.ui.theme.menuStyle

/**
 * 渲染 Composer 服务商、模型和推理强度选择器，并使用 ActionButton 菜单触发器。
 */
@Composable
internal fun ComposerSelectorMenuButton(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    menuModifier: Modifier = Modifier,
    content: MenuScope.() -> Unit,
) {
    Box {
        ComposerMenuTrigger(
            label = label,
            onClick = { onExpandedChange(!expanded) },
        )
        if (expanded) {
            PopupMenu(
                onDismissRequest = {
                    onDismissRequest()
                    true
                },
                horizontalAlignment = androidx.compose.ui.Alignment.Start,
                modifier = menuModifier,
                popupProperties = PopupProperties(focusable = false),
                content = content,
            )
        }
    }
}

/**
 * 渲染 Composer 权限选择器，并保留各权限项自身的语义色。
 */
@Composable
internal fun ComposerPermissionMenuButton(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    selectedPreset: PermissionPreset,
    onPresetSelected: (PermissionPreset) -> Unit,
) {
    val baseMenuStyle = JewelTheme.menuStyle
    val menuStyle = remember(baseMenuStyle) {
        composerPermissionMenuStyle(baseMenuStyle)
    }

    Box {
        ComposerMenuTrigger(
            label = label,
            onClick = { onExpandedChange(!expanded) },
        )
        if (expanded) {
            PopupMenu(
                onDismissRequest = {
                    onDismissRequest()
                    true
                },
                horizontalAlignment = androidx.compose.ui.Alignment.End,
                style = menuStyle,
                popupProperties = PopupProperties(focusable = false),
            ) {
                PermissionPreset.entries.forEach { preset ->
                    val presentation = permissionPresentation(preset)
                    selectableItem(
                        selected = preset == selectedPreset,
                        onClick = { onPresetSelected(preset) },
                    ) {
                        Tooltip(tooltip = { Text(presentation.description) }) {
                            ComposerPermissionPresetMenuRow(
                                presentation = presentation,
                                selected = preset == selectedPreset,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 渲染带下拉箭头的 Composer ActionButton，并独立追踪真实指针的 hover 状态。 */
@Composable
private fun ComposerMenuTrigger(
    label: String,
    onClick: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val triggerContentColor = palette.text.copy(alpha = 0.82f)
    val hoverInteractionSource = remember { MutableInteractionSource() }
    val pointerHovered by hoverInteractionSource.collectIsHoveredAsState()
    val style = composerSelectorButtonStyle(pointerHovered)

    ActionButton(
        onClick = onClick,
        focusable = false,
        style = style,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = Modifier.hoverable(hoverInteractionSource),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = JewelTheme.defaultTextStyle.copy(color = triggerContentColor),
            )
            Icon(
                key = AllIconsKeys.General.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = triggerContentColor,
            )
        }
    }
}

/**
 * 返回 Composer ActionButton 选择器样式，正常态透明，仅在真实指针 hover 时使用中性完整填充。
 */
@Composable
internal fun composerSelectorButtonStyle(pointerHovered: Boolean): IconButtonStyle {
    val palette = LocalDesktopPalette.current
    val base = JewelTheme.iconButtonStyle
    return remember(base, palette, pointerHovered) {
        IconButtonStyle(
            colors = composerSelectorButtonColors(base.colors, palette, pointerHovered),
            metrics = filledComposerSelectorButtonMetrics(base.metrics),
        )
    }
}

/**
 * 返回 Composer 图标动作的中性 hover 样式，供附件添加和移除操作共用。
 */
@Composable
internal fun composerIconActionButtonStyle(): IconButtonStyle {
    val palette = LocalDesktopPalette.current
    val base = JewelTheme.iconButtonStyle
    return remember(base, palette) {
        IconButtonStyle(
            colors = composerIconActionButtonColors(base.colors, palette),
            metrics = base.metrics,
        )
    }
}

/**
 * 返回 36dp 方形 Composer 主操作样式，停止态使用红色方块图标，发送态保留 Jewel 蓝色。
 */
@Composable
internal fun composerPrimaryActionButtonStyle(danger: Boolean): ButtonStyle {
    val palette = LocalDesktopPalette.current
    val base = JewelTheme.defaultButtonStyle
    return remember(base, palette, danger) {
        ButtonStyle(
            colors = if (danger) composerStopButtonColors(base.colors, palette) else base.colors,
            metrics = squareComposerPrimaryButtonMetrics(base.metrics),
            focusOutlineAlignment = base.focusOutlineAlignment,
        )
    }
}

/** 构造透明正常态与完整中性 hover 填充，背景只由对应触发器的指针状态决定。 */
private fun composerSelectorButtonColors(
    base: IconButtonColors,
    palette: DesktopPalette,
    pointerHovered: Boolean,
): IconButtonColors = IconButtonColors(
    foregroundSelectedActivated = base.foregroundSelectedActivated,
    background = if (pointerHovered) palette.hoverBackground else Color.Transparent,
    backgroundDisabled = Color.Transparent,
    backgroundSelected = Color.Transparent,
    backgroundSelectedActivated = Color.Transparent,
    backgroundFocused = Color.Transparent,
    backgroundPressed = palette.hoverBackground,
    backgroundHovered = palette.hoverBackground,
    border = Color.Transparent,
    borderDisabled = Color.Transparent,
    borderSelected = Color.Transparent,
    borderSelectedActivated = Color.Transparent,
    borderFocused = Color.Transparent,
    borderPressed = Color.Transparent,
    borderHovered = Color.Transparent,
)

/** 为 Composer ActionButton 保留紧凑尺寸，并把圆角调整为与输入框一致的 8dp。 */
private fun filledComposerSelectorButtonMetrics(base: IconButtonMetrics): IconButtonMetrics = IconButtonMetrics(
    cornerSize = CornerSize(8.dp),
    borderWidth = base.borderWidth,
    padding = PaddingValues(0.dp),
    minSize = base.minSize,
)

/** 构造图标动作在 hover 时使用完整中性填充，避免额外描边。 */
private fun composerIconActionButtonColors(
    base: IconButtonColors,
    palette: DesktopPalette,
): IconButtonColors = IconButtonColors(
    foregroundSelectedActivated = base.foregroundSelectedActivated,
    background = base.background,
    backgroundDisabled = base.backgroundDisabled,
    backgroundSelected = base.backgroundSelected,
    backgroundSelectedActivated = base.backgroundSelectedActivated,
    backgroundFocused = base.backgroundFocused,
    backgroundPressed = base.backgroundPressed,
    backgroundHovered = palette.hoverBackground,
    border = base.border,
    borderDisabled = base.borderDisabled,
    borderSelected = base.borderSelected,
    borderSelectedActivated = base.borderSelectedActivated,
    borderFocused = base.borderFocused,
    borderPressed = base.borderPressed,
    borderHovered = base.border,
)

/** 构造停止任务时的透明主按钮，红色只保留给停止方块图标。 */
private fun composerStopButtonColors(base: ButtonColors, palette: DesktopPalette): ButtonColors {
    val transparent = SolidColor(Color.Transparent)
    val hovered = SolidColor(palette.hoverBackground)
    val pressed = SolidColor(palette.hoverBackground.copy(alpha = 0.8f))

    return ButtonColors(
        background = transparent,
        backgroundDisabled = base.backgroundDisabled,
        backgroundFocused = transparent,
        backgroundPressed = pressed,
        backgroundHovered = hovered,
        content = palette.danger,
        contentDisabled = base.contentDisabled,
        contentFocused = palette.danger,
        contentPressed = palette.danger,
        contentHovered = palette.danger,
        border = transparent,
        borderDisabled = base.borderDisabled,
        borderFocused = transparent,
        borderPressed = transparent,
        borderHovered = transparent,
    )
}

/** 将 Jewel 默认主按钮的度量压缩为 36dp 方形和 8dp 圆角。 */
private fun squareComposerPrimaryButtonMetrics(base: ButtonMetrics): ButtonMetrics = ButtonMetrics(
    cornerSize = CornerSize(8.dp),
    padding = PaddingValues(0.dp),
    minSize = DpSize(36.dp, 36.dp),
    borderWidth = base.borderWidth,
    focusOutlineExpand = base.focusOutlineExpand,
)

/** 生成保留 Jewel 容器、阴影、焦点和键盘导航的紧凑权限菜单样式。 */
private fun composerPermissionMenuStyle(base: MenuStyle): MenuStyle = MenuStyle(
    isDark = base.isDark,
    colors = MenuColors(
        background = base.colors.background,
        border = base.colors.border,
        shadow = base.colors.shadow,
        itemColors = transparentComposerPermissionMenuItemBackgrounds(base.colors.itemColors),
    ),
    metrics = base.metrics,
    icons = base.icons,
)

/** 保持 Jewel 文本、图标、焦点和快捷键颜色，仅交由权限行绘制唯一的状态背景。 */
private fun transparentComposerPermissionMenuItemBackgrounds(base: MenuItemColors): MenuItemColors = MenuItemColors(
    background = Color.Transparent,
    backgroundDisabled = Color.Transparent,
    backgroundFocused = Color.Transparent,
    backgroundPressed = Color.Transparent,
    backgroundHovered = Color.Transparent,
    content = base.content,
    contentDisabled = base.contentDisabled,
    contentFocused = base.contentFocused,
    contentPressed = base.contentPressed,
    contentHovered = base.contentHovered,
    iconTint = base.iconTint,
    iconTintDisabled = base.iconTintDisabled,
    iconTintFocused = base.iconTintFocused,
    iconTintPressed = base.iconTintPressed,
    iconTintHovered = base.iconTintHovered,
    keybindingTint = base.keybindingTint,
    keybindingTintDisabled = base.keybindingTintDisabled,
    keybindingTintFocused = base.keybindingTintFocused,
    keybindingTintPressed = base.keybindingTintPressed,
    keybindingTintHovered = base.keybindingTintHovered,
    separator = base.separator,
)

/** 渲染一行紧凑权限项，点击选择仍由外层 Jewel selectableItem 承担。 */
@Composable
private fun ComposerPermissionPresetMenuRow(
    presentation: PermissionPresentation,
    selected: Boolean,
) {
    val palette = LocalDesktopPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(
                color = permissionPresetMenuRowBackground(
                    tone = presentation.tone,
                    selected = selected,
                    hovered = hovered,
                    pressed = pressed,
                ),
            )
            .hoverable(interactionSource)
            .onPointerEvent(PointerEventType.Press) { pressed = true }
            .onPointerEvent(PointerEventType.Release) { pressed = false },
    ) {
        Text(
            text = presentation.label,
            style = JewelTheme.defaultTextStyle.copy(color = permissionPresetMenuTextColor(palette)),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
