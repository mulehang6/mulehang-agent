@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)
@file:Suppress("UnstableApiUsage")

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.DesktopAppearance
import com.agent.app.design.ResolvedDesktopFont
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.shared.session.DesktopAppearancePreferences
import com.agent.shared.session.normalizeDesktopUiScalePercent
import kotlin.math.roundToInt
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.search.SpeedSearchableComboBox

/**
 * 全局外观设置，选择即时保存；缩放在拖动中即时应用，并在结束拖动后保存。
 */
@Composable
internal fun AppearanceSettingsContent(
    appearance: DesktopAppearance,
    compact: Boolean,
    onPreferencesChanged: (DesktopAppearancePreferences) -> Unit,
    onPreferencesChangeFinished: (DesktopAppearancePreferences) -> Unit,
) {
    GroupHeader("外观")
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppearanceScaleSetting(
                preferences = appearance.preferences,
                compact = compact,
                onPreferencesChanged = onPreferencesChanged,
                onPreferencesChangeFinished = onPreferencesChangeFinished,
            )
            AppearanceFontSetting(
                label = "界面字体",
                description = "用于界面中的常规文字与控件。",
                defaultLabel = "系统默认",
                selectedFont = appearance.uiFont,
                fontFamilies = appearance.fontCatalog.uiFontFamilies,
                compact = compact,
                onFontFamilyChange = { familyName ->
                    val updatedPreferences = appearance.preferences.copy(uiFontFamily = familyName)
                    onPreferencesChanged(updatedPreferences)
                    onPreferencesChangeFinished(updatedPreferences)
                },
            )
            AppearanceFontSetting(
                label = "代码与终端字体",
                description = "仅列出经字宽检测的等宽系统字体，并同步应用到已打开的终端。",
                defaultLabel = "系统等宽",
                selectedFont = appearance.codeFont,
                fontFamilies = appearance.fontCatalog.codeFontFamilies,
                compact = compact,
                onFontFamilyChange = { familyName ->
                    val updatedPreferences = appearance.preferences.copy(codeFontFamily = familyName)
                    onPreferencesChanged(updatedPreferences)
                    onPreferencesChangeFinished(updatedPreferences)
                },
            )
        }
    }
}

/**
 * 渲染 50% 到 200% 的离散全局缩放选择器。
 */
@Composable
private fun AppearanceScaleSetting(
    preferences: DesktopAppearancePreferences,
    compact: Boolean,
    onPreferencesChanged: (DesktopAppearancePreferences) -> Unit,
    onPreferencesChangeFinished: (DesktopAppearancePreferences) -> Unit,
) {
    var previewScalePercent by remember { mutableStateOf(preferences.scalePercent) }
    LaunchedEffect(preferences.scalePercent) {
        previewScalePercent = preferences.scalePercent
    }
    val onScaleChange = { value: Float ->
        previewScalePercent = normalizeDesktopUiScalePercent(value.roundToInt())
        onPreferencesChanged(
            preferences.copy(scalePercent = previewScalePercent),
        )
    }
    val onScaleChangeFinished = {
        onPreferencesChangeFinished(preferences.copy(scalePercent = previewScalePercent))
    }
    val resetScale = {
        previewScalePercent = DesktopAppearancePreferences.DEFAULT_UI_SCALE_PERCENT
        val updatedPreferences = preferences.copy(scalePercent = previewScalePercent)
        onPreferencesChanged(updatedPreferences)
        onPreferencesChangeFinished(updatedPreferences)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("全局缩放")
        Text("缩放会同时影响界面布局和文字；可使用 Ctrl+= 与 Ctrl+- 调整。", color = AppMuted)
        if (compact) {
            Text("当前 ${previewScalePercent}%")
            Slider(
                value = previewScalePercent.toFloat(),
                onValueChange = onScaleChange,
                valueRange = DesktopAppearancePreferences.MIN_UI_SCALE_PERCENT.toFloat()..
                    DesktopAppearancePreferences.MAX_UI_SCALE_PERCENT.toFloat(),
                steps = scaleSliderSteps(),
                onValueChangeFinished = onScaleChangeFinished,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = resetScale) {
                Text("恢复 100%")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${previewScalePercent}%", modifier = Modifier.width(48.dp))
                Slider(
                    value = previewScalePercent.toFloat(),
                    onValueChange = onScaleChange,
                    valueRange = DesktopAppearancePreferences.MIN_UI_SCALE_PERCENT.toFloat()..
                        DesktopAppearancePreferences.MAX_UI_SCALE_PERCENT.toFloat(),
                    steps = scaleSliderSteps(),
                    onValueChangeFinished = onScaleChangeFinished,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = resetScale) {
                    Text("恢复 100%")
                }
            }
        }
    }
}

/**
 * 渲染带 Jewel 速度搜索的字体选择框和缺失字体回退说明。
 */
@Composable
private fun AppearanceFontSetting(
    label: String,
    description: String,
    defaultLabel: String,
    selectedFont: ResolvedDesktopFont,
    fontFamilies: List<String>,
    compact: Boolean,
    onFontFamilyChange: (String?) -> Unit,
) {
    val options = remember(fontFamilies, defaultLabel) {
        appearanceFontOptions(defaultLabel = defaultLabel, fontFamilies = fontFamilies)
    }
    val selectedIndex = appearanceFontOptionIndex(options, selectedFont)
    val selector = @Composable {
        SpeedSearchArea(modifier = Modifier.fillMaxWidth()) {
            SpeedSearchableComboBox(
                items = options.map(AppearanceFontOption::label),
                selectedIndex = selectedIndex,
                onSelectedItemChange = { index -> onFontFamilyChange(options[index].familyName) },
                itemKeys = { index, _ -> index },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (compact) {
            Text(label)
            Text(description, color = AppMuted)
            selector()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label)
                    Text(description, color = AppMuted)
                }
                Column(modifier = Modifier.width(240.dp)) {
                    selector()
                }
            }
        }
        missingFontMessage(selectedFont, defaultLabel)?.let { message ->
            Text(message, color = AppMuted)
        }
    }
}

/**
 * 字体下拉框中显示的一个可选项。
 */
private data class AppearanceFontOption(
    val familyName: String?,
    val label: String,
)

/**
 * 创建默认字体和系统字体组成的下拉选项。
 */
private fun appearanceFontOptions(
    defaultLabel: String,
    fontFamilies: List<String>,
): List<AppearanceFontOption> = buildList {
    add(AppearanceFontOption(familyName = null, label = defaultLabel))
    addAll(fontFamilies.map { familyName -> AppearanceFontOption(familyName = familyName, label = familyName) })
}

/**
 * 找出当前解析字体在下拉框中的位置；缺失字体显示为默认项但不覆盖已保存配置。
 */
private fun appearanceFontOptionIndex(
    options: List<AppearanceFontOption>,
    selectedFont: ResolvedDesktopFont,
): Int {
    val familyName = selectedFont.effectiveFamilyName ?: return 0
    return options.indexOfFirst { it.familyName.equals(familyName, ignoreCase = true) }.coerceAtLeast(0)
}

/**
 * 返回缺失字体时应展示的回退说明。
 */
private fun missingFontMessage(selectedFont: ResolvedDesktopFont, defaultLabel: String): String? =
    selectedFont.configuredFamilyName
        ?.takeIf { selectedFont.isFallback }
        ?.let { "“$it”未在当前系统中找到，正在使用$defaultLabel。" }

/**
 * 计算 Jewel 滑杆在 50% 至 200% 之间需要的内部离散档位数量。
 */
private fun scaleSliderSteps(): Int =
    (DesktopAppearancePreferences.MAX_UI_SCALE_PERCENT - DesktopAppearancePreferences.MIN_UI_SCALE_PERCENT) /
        DesktopAppearancePreferences.UI_SCALE_STEP_PERCENT - 1
