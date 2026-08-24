@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.agent.app.design.LocalDesktopPalette
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** 绘制图表的源码切换与渲染缩放控件，并以细线融入同一张图表 Island。 */
@Composable
internal fun DiagramPreviewToolbar(
    displayMode: DiagramPreviewDisplayMode,
    enabled: Boolean,
    zoomPercent: Int,
    zoomInput: TextFieldValue,
    onZoomInputChange: (TextFieldValue) -> Unit,
    onZoomChange: (Int) -> Unit,
    onDisplayModeChange: (DiagramPreviewDisplayMode) -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val commitZoomInput = {
        onZoomChange(normalizeDiagramZoomInput(zoomInput.text, zoomPercent))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DIAGRAM_TOOLBAR_HEIGHT_DP.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (displayMode) {
                    DiagramPreviewDisplayMode.RENDERED -> if (enabled) "缩放" else "加载中"
                    DiagramPreviewDisplayMode.SOURCE -> "源码"
                },
                style = JewelTheme.defaultTextStyle.copy(color = palette.muted),
            )
            if (displayMode == DiagramPreviewDisplayMode.RENDERED) {
                IconButton(
                    onClick = { onZoomChange(zoomPercent - DIAGRAM_ZOOM_STEP_PERCENT) },
                    enabled = enabled && zoomPercent > DIAGRAM_MIN_ZOOM_PERCENT,
                ) {
                    Icon(AllIconsKeys.General.ZoomOut, contentDescription = "缩小图表")
                }
                Slider(
                    value = zoomPercent.toFloat(),
                    onValueChange = { onZoomChange(it.roundToInt()) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    enabled = enabled,
                    valueRange = DIAGRAM_MIN_ZOOM_PERCENT.toFloat()..DIAGRAM_MAX_ZOOM_PERCENT.toFloat(),
                    steps = DIAGRAM_ZOOM_SLIDER_STEPS,
                )
                TextField(
                    value = zoomInput,
                    onValueChange = { nextValue ->
                        if (isDiagramZoomInputCandidate(nextValue.text)) {
                            onZoomInputChange(nextValue)
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier
                        .width(56.dp)
                        .height(30.dp)
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) commitZoomInput()
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                commitZoomInput()
                                true
                            } else {
                                false
                            }
                        },
                )
                Text(
                    text = "%",
                    style = JewelTheme.defaultTextStyle.copy(color = palette.muted),
                )
                IconButton(
                    onClick = { onZoomChange(zoomPercent + DIAGRAM_ZOOM_STEP_PERCENT) },
                    enabled = enabled && zoomPercent < DIAGRAM_MAX_ZOOM_PERCENT,
                ) {
                    Icon(AllIconsKeys.General.ZoomIn, contentDescription = "放大图表")
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = { onDisplayModeChange(displayMode.toggled()) },
            ) {
                Text(
                    text = if (displayMode == DiagramPreviewDisplayMode.RENDERED) "查看源码" else "查看渲染",
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.line.copy(alpha = 0.72f)),
        )
    }
}
