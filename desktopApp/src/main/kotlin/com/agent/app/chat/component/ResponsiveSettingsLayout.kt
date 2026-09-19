package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 移动同一个内容子树，宽窄切换不会销毁 Hook 编辑器与 Git 安装草稿。 */
@Composable
internal fun ResponsiveSettingsLayout(
    compact: Boolean,
    section: SettingsSection,
    sections: List<SettingsSection>,
    anchors: SettingsAnchorState,
    onSectionChange: (SettingsSection, Boolean) -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    val latestContent by rememberUpdatedState(content)
    val movableContent = remember { movableContentOf<Boolean> { latestContent(it) } }
    if (compact) {
        Column(Modifier.fillMaxSize().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsNavigation(section, sections, onSectionChange, compact = true, anchors = anchors)
            Box(Modifier.weight(1f).fillMaxWidth()) { movableContent(true) }
        }
    } else {
        Row(Modifier.fillMaxSize().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SettingsNavigation(section, sections, onSectionChange, anchors = anchors)
            Box(Modifier.weight(1f).fillMaxSize().widthIn(max = 760.dp)) { movableContent(false) }
        }
    }
}
