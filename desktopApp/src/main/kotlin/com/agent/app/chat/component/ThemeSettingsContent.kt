package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.AppLine
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppText
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.DesktopMaterialMode
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.liquidglass.AdaptiveLiquidGlassSurface
import com.agent.app.design.liquidglass.LiquidGlassSurfaceRole

internal const val LIQUID_GLASS_MENU_WIDTH_DP = 210
internal const val LIQUID_GLASS_MENU_ROW_HEIGHT_DP = 24
internal const val LIQUID_GLASS_MENU_GAP_DP = 6

/** 保存主题下拉菜单的锚点、方向、键盘索引和同层浮层状态。 */
@Stable
internal class LiquidGlassSelectState {
    var expanded by mutableStateOf(false)
        private set
    var focusedIndex by mutableStateOf(0)
        private set
    var anchorBoundsInRoot by mutableStateOf(Rect.Zero)
        private set
    var menuBoundsInRoot by mutableStateOf(Rect.Zero)
        private set
    var panelOriginInRoot by mutableStateOf(Offset.Zero)
        private set
    var panelSize by mutableStateOf(IntSize.Zero)
        private set

    /** 更新设置面板根坐标，供菜单同层定位和外部点击判断。 */
    fun updatePanelGeometry(origin: Offset, size: IntSize) {
        panelOriginInRoot = origin
        panelSize = size
    }

    /** 更新主题触发器在根坐标中的边界。 */
    fun updateAnchor(bounds: Rect) {
        anchorBoundsInRoot = bounds
    }

    /** 更新已展开菜单的根坐标边界。 */
    fun updateMenu(bounds: Rect) {
        menuBoundsInRoot = bounds
    }

    /** 切换菜单并让键盘索引与当前值同步。 */
    fun toggle(selectedIndex: Int) {
        focusedIndex = selectedIndex
        expanded = !expanded
    }

    /** 打开菜单并聚焦指定选项。 */
    fun open(index: Int) {
        focusedIndex = index.coerceIn(0, DesktopThemeMode.entries.lastIndex)
        expanded = true
    }

    /** 关闭菜单，触发器焦点由组合层恢复。 */
    fun close() {
        expanded = false
    }

    /** 将键盘焦点移动到相邻选项并循环。 */
    fun move(delta: Int) {
        val count = DesktopThemeMode.entries.size
        focusedIndex = (focusedIndex + delta + count) % count
    }

    /** 将键盘焦点移动到首项或末项。 */
    fun moveToEdge(last: Boolean) {
        focusedIndex = if (last) DesktopThemeMode.entries.lastIndex else 0
    }
}

/** 主题页只保留一个紧凑 Island 和右侧 Liquid Glass 选择器。 */
@Composable
internal fun ThemeSettingsContent(
    themeMode: DesktopThemeMode,
    liquidGlassEnabled: Boolean,
    selectState: LiquidGlassSelectState,
    onThemeChanged: (DesktopThemeMode) -> Unit,
    onLiquidGlassEnabledChanged: (Boolean) -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val islandBackground = if (palette.materialMode == DesktopMaterialMode.LIQUID_GLASS) {
        AppPanelBackground.copy(alpha = 0.58f)
    } else {
        AppPanelBackground
    }
    Text("主题", style = MaterialTheme.typography.headlineSmall.copy(color = AppText))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(islandBackground)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("界面主题", style = MaterialTheme.typography.titleSmall.copy(color = AppText))
                Text("跟随系统，或固定使用深色与浅色外观。", style = MaterialTheme.typography.bodySmall.copy(color = AppMuted))
            }
            LiquidGlassThemeTrigger(
                themeMode = themeMode,
                state = selectState,
                onThemeChanged = onThemeChanged,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppLine.copy(alpha = 0.72f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("启用液态玻璃", style = MaterialTheme.typography.titleSmall.copy(color = AppText))
                Text("将 Liquid Glass 材质应用到整个应用。", style = MaterialTheme.typography.bodySmall.copy(color = AppMuted))
            }
            Switch(
                checked = liquidGlassEnabled,
                onCheckedChange = onLiquidGlassEnabledChanged,
                modifier = Modifier.semantics {
                    stateDescription = if (liquidGlassEnabled) "液态玻璃已启用" else "液态玻璃已停用"
                },
            )
        }
    }
}

/** 绘制共享背景采样的主题触发器，并处理完整键盘交互。 */
@Composable
private fun LiquidGlassThemeTrigger(
    themeMode: DesktopThemeMode,
    state: LiquidGlassSelectState,
    onThemeChanged: (DesktopThemeMode) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val selectedIndex = DesktopThemeMode.entries.indexOf(themeMode)
    LaunchedEffect(state.expanded) {
        if (!state.expanded) focusRequester.requestFocus()
    }
    AdaptiveLiquidGlassSurface(
        role = LiquidGlassSurfaceRole.INPUT,
        radius = 9.dp,
        solidColor = AppPanelBackground,
        borderColor = AppLine,
        modifier = Modifier
            .width(170.dp)
            .height(36.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onGloballyPositioned { state.updateAnchor(it.boundsInRoot()) }
            .onPreviewKeyEvent { event ->
                handleThemeSelectKeyEvent(
                    eventKey = event.key,
                    isKeyDown = event.type == KeyEventType.KeyDown,
                    state = state,
                    selectedIndex = selectedIndex,
                    onThemeChanged = onThemeChanged,
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = "主题选择器，当前${themeMode.label}"
                stateDescription = if (state.expanded) "已展开" else "已收起"
            }
            .clickable { state.toggle(selectedIndex) },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(themeMode.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(color = AppText))
            Text(if (state.expanded) "⌃" else "⌄", style = MaterialTheme.typography.labelLarge.copy(color = AppMuted))
        }
    }
}

/** 处理触发器持有焦点时的主题选择键盘协议。 */
private fun handleThemeSelectKeyEvent(
    eventKey: Key,
    isKeyDown: Boolean,
    state: LiquidGlassSelectState,
    selectedIndex: Int,
    onThemeChanged: (DesktopThemeMode) -> Unit,
): Boolean {
    return isKeyDown && when (eventKey) {
        Key.Enter, Key.Spacebar -> {
            if (state.expanded) {
                onThemeChanged(DesktopThemeMode.entries[state.focusedIndex])
                state.close()
            } else {
                state.open(selectedIndex)
            }
            true
        }
        Key.DirectionDown -> {
            if (state.expanded) state.move(1) else state.open(selectedIndex)
            true
        }
        Key.DirectionUp -> {
            if (state.expanded) state.move(-1) else state.open(selectedIndex)
            true
        }
        Key.MoveHome -> {
            if (!state.expanded) state.open(selectedIndex)
            state.moveToEdge(last = false)
            true
        }
        Key.MoveEnd -> {
            if (!state.expanded) state.open(selectedIndex)
            state.moveToEdge(last = true)
            true
        }
        Key.Escape -> {
            state.close()
            true
        }
        else -> false
    }
}
