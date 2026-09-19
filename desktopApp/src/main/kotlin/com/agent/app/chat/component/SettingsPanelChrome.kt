@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.iconKey
import com.agent.app.design.rememberExternalTextFieldValue
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** 设置页左上角使用与终端一致的 IDEA Islands 页签。 */
@Composable
internal fun SettingsTitleTab(onClose: () -> Unit) {
    IslandsTabStrip(
        tabs = listOf(
            IslandsTab(
                label = "设置",
                selected = true,
                iconKey = RightRailGlyph.SETTINGS.iconKey,
                closable = true,
                onClick = {},
                onClose = onClose,
            ),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 仅在设置内容真实溢出时绘制右侧滚动条。 */
internal fun shouldShowSettingsContentScrollbar(maxScrollValue: Int): Boolean = maxScrollValue > 0

/** 带焦点边框的紧凑设置搜索框。 */
@Composable
internal fun SettingsSearchField(value: String, onValueChange: (String) -> Unit) {
    val editorValue = rememberExternalTextFieldValue(value)
    TextField(
        value = editorValue.value,
        onValueChange = { nextValue ->
            editorValue.value = nextValue
            onValueChange(nextValue.text)
        },
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(38.dp),
        placeholder = { Text("搜索") },
        leadingIcon = { Icon(AllIconsKeys.Actions.Find, "搜索") },
    )
}
