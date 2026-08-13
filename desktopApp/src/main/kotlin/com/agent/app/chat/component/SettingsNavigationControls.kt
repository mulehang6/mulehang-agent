@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.AppText
import com.agent.shared.settings.model.ConfigLayer

private const val SETTINGS_CONTENT_ENTER_MILLIS = 180
private const val SETTINGS_CONTENT_EXIT_MILLIS = 140
private const val SETTINGS_CONTENT_FADE_MILLIS = 120

/** 使用共享选中胶囊切换用户级与项目级配置。 */
@Composable
internal fun SettingsScopeBar(
    layer: ConfigLayer,
    projectEnabled: Boolean,
    onLayerChange: (ConfigLayer) -> Unit,
) {
    val indicatorOffset by animateDpAsState(
        targetValue = if (layer == ConfigLayer.PROJECT) 92.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 650f),
        label = "settings-scope-indicator",
    )
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.width(180.dp).height(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(AppChipBackground.copy(alpha = 0.58f)).padding(2.dp),
            ) {
                Box(
                    modifier = Modifier.offset(x = indicatorOffset).width(84.dp).height(28.dp)
                        .clip(RoundedCornerShape(6.dp)).background(AppSelectedBackground),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsScopeOption("全局", selected = layer == ConfigLayer.USER, enabled = true) {
                        onLayerChange(ConfigLayer.USER)
                    }
                    SettingsScopeOption("当前项目", selected = layer == ConfigLayer.PROJECT, enabled = projectEnabled) {
                        onLayerChange(ConfigLayer.PROJECT)
                    }
                }
            }
            if (!projectEnabled) Text("请选择工作区", style = MaterialTheme.typography.bodySmall.copy(color = AppMuted))
        }
        Spacer(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(AppLine))
    }
}

/** 绘制配置层级选择器中的等宽文本按钮。 */
@Composable
private fun SettingsScopeOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.width(84.dp).height(28.dp).clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(color = if (enabled) AppText else AppMuted))
    }
}

/** 左侧设置导航使用共享选中背景，并区分鼠标与键盘触发的内容动效。 */
@Composable
internal fun SettingsNavigation(
    section: SettingsSection,
    onSectionChange: (SettingsSection, Boolean) -> Unit,
) {
    val itemHeight = 38.dp
    val itemGap = 5.dp
    val indicatorOffset by animateDpAsState(
        targetValue = (itemHeight + itemGap) * section.ordinal,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 680f),
        label = "settings-section-indicator",
    )
    Box(modifier = Modifier.width(166.dp).height(itemHeight * SettingsSection.entries.size + itemGap)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(itemHeight).offset(y = indicatorOffset)
                .clip(RoundedCornerShape(7.dp)).background(AppSelectedBackground),
        )
        Column(verticalArrangement = Arrangement.spacedBy(itemGap)) {
            SettingsSection.entries.forEach { entry ->
                var hovered by remember(entry) { mutableStateOf(false) }
                Box(
                    modifier = Modifier.fillMaxWidth().height(itemHeight).clip(RoundedCornerShape(7.dp))
                        .background(
                            if (hovered && entry != section) {
                                settingsItemBackground(selected = false, hovered = true)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .onPointerEvent(PointerEventType.Enter) { hovered = true }
                        .onPointerEvent(PointerEventType.Exit) { hovered = false }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val target = when (event.key) {
                                Key.DirectionDown -> SettingsSection.entries.getOrNull((entry.ordinal + 1).coerceAtMost(SettingsSection.entries.lastIndex))
                                Key.DirectionUp -> SettingsSection.entries.getOrNull((entry.ordinal - 1).coerceAtLeast(0))
                                else -> null
                            }
                            target?.let { onSectionChange(it, false) } != null
                        }
                        .focusable()
                        .clickable { onSectionChange(entry, true) }
                        .semantics {
                            role = Role.Tab
                            selected = entry == section
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(entry.label, style = MaterialTheme.typography.bodyMedium.copy(color = AppText))
                }
            }
        }
    }
}

/** 设置内容切换所需的状态快照。 */
@Immutable
internal data class SettingsContentTarget(
    val layer: ConfigLayer,
    val section: SettingsSection,
    val spatialMotion: Boolean,
)

/** 对配置层级使用交叉淡入，对鼠标分区切换使用八像素方向过渡。 */
@Composable
internal fun SettingsAnimatedContent(
    target: SettingsContentTarget,
    modifier: Modifier = Modifier,
    content: @Composable (SettingsSection) -> Unit,
) {
    val motionDistancePx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val reducedMotion = prefersReducedMotion()
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            settingsContentTransition(
                initial = initialState,
                target = targetState,
                reducedMotion = reducedMotion,
                motionDistancePx = motionDistancePx,
            )
        },
        label = "settings-content-transition",
    ) { current ->
        Box(modifier = Modifier.fillMaxSize()) { content(current.section) }
    }
}

/** 返回可被单元测试验证的设置内容转场。 */
private fun settingsContentTransition(
    initial: SettingsContentTarget,
    target: SettingsContentTarget,
    reducedMotion: Boolean,
    motionDistancePx: Int,
): ContentTransform {
    if (reducedMotion || !target.spatialMotion || initial.layer != target.layer) {
        return fadeIn(tween(if (reducedMotion) 90 else SETTINGS_CONTENT_FADE_MILLIS)) togetherWith
                fadeOut(tween(if (reducedMotion) 90 else SETTINGS_CONTENT_FADE_MILLIS))
    }
    val direction = if (target.section.ordinal >= initial.section.ordinal) 1 else -1
    return (slideInHorizontally(tween(SETTINGS_CONTENT_ENTER_MILLIS)) { direction * motionDistancePx } +
            fadeIn(tween(SETTINGS_CONTENT_ENTER_MILLIS))) togetherWith
            (slideOutHorizontally(tween(SETTINGS_CONTENT_EXIT_MILLIS)) { -direction * motionDistancePx } +
                    fadeOut(tween(SETTINGS_CONTENT_EXIT_MILLIS)))
}
