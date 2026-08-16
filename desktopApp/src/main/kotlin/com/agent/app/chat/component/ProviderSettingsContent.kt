@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppAccent
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.ProviderCardBackground
import com.agent.app.design.ProviderCardHoverBackground
import com.agent.app.design.selectMenuItemBackground
import com.agent.shared.settings.model.ModelProfile
import com.agent.shared.settings.model.ProviderProfile
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.settings.model.SettingsDocument
import java.net.URI
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text

internal const val PROVIDER_EDITOR_EXPAND_DURATION_MILLIS = 180
internal const val PROVIDER_EDITOR_COLLAPSE_DURATION_MILLIS = 140
private val ProviderEditorMotionEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** AI Provider 摘要列表和按需展开的编辑器。 */
@Composable
internal fun ProviderSettingsContent(
    document: SettingsDocument,
    search: String,
    expandedProviderId: String?,
    onExpandedProviderChange: (String?) -> Unit,
    onDocumentChange: (SettingsDocument) -> Unit,
) {
    Text("AI 服务", style = JewelTheme.defaultTextStyle.copy(color = AppText))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsActionButton("新增服务") {
            val id = "provider-${document.providers.size + 1}"
            onDocumentChange(document.copy(providers = document.providers + newProvider(id)))
            onExpandedProviderChange(id)
        }
    }
    document.providers.filter { provider ->
        search.isBlank() || provider.id.contains(search, true) || provider.label.orEmpty().contains(search, true)
    }.forEach { provider ->
        val expanded = provider.id == expandedProviderId
        var hovered by remember(provider.id) { mutableStateOf(false) }
        SettingsGroup(background = providerCardBackground()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(providerSummaryBackground(expanded = expanded, hovered = hovered))
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .clickable { onExpandedProviderChange(if (expanded) null else provider.id) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProviderGlyph(provider.label ?: provider.id)
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.label ?: provider.id, style = JewelTheme.defaultTextStyle.copy(color = AppText))
                    Text(
                        "${provider.providerType.name.lowercase().replace('_', '-')}  ·  " +
                                "${providerEndpointHost(provider.baseUrl)}  ·  ${provider.models.size} 个模型",
                        style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                    )
                }
                Checkbox(
                    checked = provider.isEnabled(),
                    onCheckedChange = { enabled ->
                        onDocumentChange(
                            document.copy(
                                providers = document.providers.map {
                                    if (it.id == provider.id) provider.copy(enabled = enabled) else it
                                },
                            ),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp).semantics {
                        contentDescription = "${provider.label ?: provider.id}启用状态"
                        stateDescription = if (provider.isEnabled()) "已启用" else "已停用"
                    },
                )
                ProviderDisclosureArrow(expanded = expanded, providerLabel = provider.label ?: provider.id)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(PROVIDER_EDITOR_EXPAND_DURATION_MILLIS, easing = ProviderEditorMotionEasing)) +
                        fadeIn(tween(PROVIDER_EDITOR_EXPAND_DURATION_MILLIS, easing = ProviderEditorMotionEasing)),
                exit = shrinkVertically(tween(PROVIDER_EDITOR_COLLAPSE_DURATION_MILLIS, easing = ProviderEditorMotionEasing)) +
                        fadeOut(tween(PROVIDER_EDITOR_COLLAPSE_DURATION_MILLIS, easing = ProviderEditorMotionEasing)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderEditor(
                        provider = provider,
                        onChange = { updated ->
                            onDocumentChange(
                                document.copy(providers = document.providers.map { if (it.id == provider.id) updated else it }),
                            )
                        },
                        onDelete = {
                            onDocumentChange(document.copy(providers = document.providers - provider))
                            onExpandedProviderChange(null)
                        },
                    )
                }
            }
        }
    }
    if (document.providers.isEmpty()) {
        Text("尚未配置服务。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
    }
}

/** 返回 Provider 摘要箭头的无障碍说明。 */
internal fun providerDisclosureDescription(providerLabel: String, expanded: Boolean): String =
    if (expanded) "收起服务 $providerLabel" else "展开服务 $providerLabel"

/** 返回 Provider 摘要箭头的朝向：展开朝下，收起朝右。 */
internal fun providerDisclosureRotationDegrees(expanded: Boolean): Float = if (expanded) 0f else -90f

/** 绘制 Provider 摘要行末端的紧凑 disclosure 箭头。 */
@Composable
private fun ProviderDisclosureArrow(expanded: Boolean, providerLabel: String) {
    val rotationDegrees by animateFloatAsState(
        targetValue = providerDisclosureRotationDegrees(expanded),
        animationSpec = tween(160, easing = ProviderEditorMotionEasing),
        label = "provider-disclosure-arrow",
    )
    Canvas(
        modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotationDegrees }.semantics {
            contentDescription = providerDisclosureDescription(providerLabel, expanded)
            stateDescription = if (expanded) "已展开" else "已收起"
        },
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val halfWidth = size.minDimension * 0.2f
        val halfHeight = size.minDimension * 0.12f
        val strokeWidth = 1.65.dp.toPx()
        drawLine(
            AppMuted,
            androidx.compose.ui.geometry.Offset(centerX - halfWidth, centerY - halfHeight),
            androidx.compose.ui.geometry.Offset(centerX, centerY + halfHeight),
            strokeWidth,
            StrokeCap.Round,
        )
        drawLine(
            AppMuted,
            androidx.compose.ui.geometry.Offset(centerX, centerY + halfHeight),
            androidx.compose.ui.geometry.Offset(centerX + halfWidth, centerY - halfHeight),
            strokeWidth,
            StrokeCap.Round,
        )
    }
}

/** 绘制不依赖品牌资产的中性 Provider 标识。 */
@Composable
private fun ProviderGlyph(label: String) {
    Box(
        modifier = Modifier.padding(end = 10.dp).size(32.dp).clip(RoundedCornerShape(9.dp))
            .background(AppChipBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.trim().take(1).uppercase(), style = JewelTheme.defaultTextStyle.copy(color = AppText))
    }
}

/** 中性设置组表面，作为 Provider 外层 Island。 */
@Composable
private fun SettingsGroup(
    background: Color = AppChipBackground,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(background).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** 绘制小型操作按钮，仅保存操作使用固定强调色。 */
@Composable
internal fun SettingsActionButton(
    text: String,
    emphasized: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    var hovered by remember(text) { mutableStateOf(false) }
    val color = when {
        emphasized -> AppAccent
        destructive -> AppDanger.copy(alpha = if (hovered) 0.9f else 0.62f)
        else -> settingsItemBackground(selected = false, hovered = hovered)
    }
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(color)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        style = JewelTheme.defaultTextStyle.copy(color = AppText),
    )
}

/** 返回设置项和菜单共用的 hover/选中背景。 */
internal fun settingsItemBackground(selected: Boolean, hovered: Boolean, enabled: Boolean = true): Color =
    selectMenuItemBackground(selected = selected, hovered = hovered, enabled = enabled)

/** 返回 Provider 外层卡片的稳定主题表面。 */
internal fun providerCardBackground(): Color = ProviderCardBackground

/** 返回 Provider 摘要 Island 的展开与悬浮表面。 */
internal fun providerSummaryBackground(expanded: Boolean, hovered: Boolean): Color = when {
    expanded -> settingsItemBackground(selected = true, hovered = hovered)
    hovered -> ProviderCardHoverBackground
    else -> Color.Transparent
}

/** 从 Provider 地址提取适合摘要行展示的主机名，并为不完整地址提供可读回退。 */
@Suppress("HttpUrlsUsage")
internal fun providerEndpointHost(baseUrl: String): String {
    val normalized = baseUrl.trim()
    if (normalized.isBlank()) return "未配置地址"
    return runCatching { URI(normalized).host }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: normalized
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .ifBlank { "未配置地址" }
}

/** 未配置辅助模型时，以首个模型作为不可写入的视觉回退。 */
internal fun auxiliaryModelPlaceholder(provider: ProviderProfile): String? =
    provider.models.firstOrNull()?.id?.takeIf(String::isNotBlank)

/** 创建可立即编辑的默认 Provider。 */
private fun newProvider(id: String): ProviderProfile = ProviderProfile(
    id = id,
    providerType = ProviderType.OPENAI_RESPONSES,
    baseUrl = "https://api.openai.com/v1",
    apiKey = "",
    models = listOf(ModelProfile(id = "gpt-4.1")),
)

/** 返回表单可直接修复的首个设置错误。 */
internal fun validateSettingsDocument(document: SettingsDocument): String? {
    val ids = document.providers.map(ProviderProfile::id)
    if (ids.any(String::isBlank)) return "服务 ID 不能为空。"
    if (ids.distinct().size != ids.size) return "服务 ID 不能重复。"
    document.providers.forEach { provider ->
        if (provider.baseUrl.isBlank()) return "${provider.id} 的 Base URL 不能为空。"
        if (provider.apiKey.isBlank()) return "${provider.id} 的 API Key 不能为空。"
        if (provider.models.isEmpty()) return "${provider.id} 至少需要一个模型。"
        if (provider.models.any { it.id.isBlank() }) return "${provider.id} 存在空的模型 ID。"
    }
    return null
}
