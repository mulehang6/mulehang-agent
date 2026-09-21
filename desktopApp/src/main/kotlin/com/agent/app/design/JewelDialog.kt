package com.agent.app.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import mulehang_agent.desktopapp.generated.resources.Res
import mulehang_agent.desktopapp.generated.resources.mulehang_agent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** 将浮层固定在当前应用窗口中央，并在小窗口中约束到可见边界。 */
internal object CenteredPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
        y = ((windowSize.height - popupContentSize.height) / 2).coerceAtLeast(0),
    )
}

/**
 * 在当前 Compose 层级内显示 Islands 风格模态浮层，避免创建带系统标题栏的第二个窗口。
 */
@Composable
internal fun JewelDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = "取消",
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    width: Dp = 440.dp,
    height: Dp = 240.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val dialogShape = RoundedCornerShape(12.dp)
    Popup(
        popupPositionProvider = CenteredPopupPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            JewelSurface(
                role = JewelSurfaceRole.FLOATING,
                radius = 12.dp,
                solidColor = palette.panelBackground,
                borderColor = palette.popupBorder,
                modifier = modifier.width(width).height(height),
            ) {
                Column(modifier = Modifier.fillMaxSize().clip(dialogShape)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(palette.frameBackground)
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.mulehang_agent),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            color = palette.text,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconActionButton(
                            key = AllIconsKeys.General.Close,
                            contentDescription = "关闭",
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.line))
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(contentPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content,
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.line))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissLabel?.let { label ->
                            OutlinedButton(onClick = onDismiss) { Text(label) }
                        }
                        DefaultButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
                    }
                }
            }
        }
    }
}
