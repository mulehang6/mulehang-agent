package com.agent.app.chat.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Composer 底栏在不同窗口宽度下的布局方式。 */
internal enum class ComposerControlLayoutMode {
    SPLIT,
    HORIZONTAL_SCROLL,
}

/** 根据窗口模式选择 Composer 底栏的布局策略。 */
internal fun composerControlLayout(compact: Boolean): ComposerControlLayoutMode =
    if (compact) ComposerControlLayoutMode.HORIZONTAL_SCROLL else ComposerControlLayoutMode.SPLIT

/** 在宽窗口中分组对齐，在窄窗口中将所有控件放入同一条横向滚动栏。 */
@Composable
internal fun ComposerControlBar(
    compact: Boolean,
    startControls: @Composable RowScope.() -> Unit,
    endControls: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    when (composerControlLayout(compact)) {
        ComposerControlLayoutMode.HORIZONTAL_SCROLL -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                startControls()
                endControls()
            }
        }

        ComposerControlLayoutMode.SPLIT -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = startControls,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = endControls,
                )
            }
        }
    }
}
