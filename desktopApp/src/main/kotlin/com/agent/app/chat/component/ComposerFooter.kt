package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.platform.pickWorkspaceDirectory
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/**
 * 原型下方 plan + composer 区域。
 */
@Composable
internal fun FooterComposerSection(
    state: ChatWindowState,
    compact: Boolean,
    onSendDraft: () -> Unit,
    composerInputMaxHeight: Dp = 320.dp,
) {
    val activeConversation = state.ui.activeConversationOrNull
    val planCard = activeConversation?.let { extractPlanCard(it.items) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (compact) 12.dp else 32.dp,
                top = 0.dp,
                end = if (compact) 12.dp else 32.dp,
                bottom = if (compact) 12.dp else 20.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (planCard != null) {
                PlanCard(
                    title = planCard.title,
                    entries = planCard.entries,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            activeConversation?.let { conversation ->
                PendingInteractionCards(
                    conversation = conversation,
                    state = state,
                )
                state.workspaceIssue(conversation)
                    .takeIf(::shouldShowWorkspaceRepairCard)
                    ?.let { message ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            WorkspaceRepairCard(
                                message = message,
                                onRelink = {
                                    pickWorkspaceDirectory()?.let { path ->
                                        if (conversation.workspacePath.isBlank()) {
                                            state.relinkConversationWorkspace(conversation.id, path)
                                        } else {
                                            state.relinkWorkspace(conversation.workspacePath, path)
                                        }
                                    }
                                },
                                onDisconnect = { state.disconnectWorkspace(conversation.workspacePath) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp),
                            )
                        }
                    }
            }
            ComposerPanel(
                state = state,
                onSendDraft = onSendDraft,
                composerInputMaxHeight = composerInputMaxHeight,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 工作目录错误与权限、提问共用 Composer 上方的交互卡片区域。 */
internal fun shouldShowWorkspaceRepairCard(workspaceIssue: String?): Boolean = workspaceIssue != null

/** 在 Composer 上方提供可恢复的工作目录操作。 */
@Composable
private fun WorkspaceRepairCard(
    message: String,
    onRelink: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 12.dp,
        solidColor = AppDanger.copy(alpha = 0.12f),
        borderColor = AppDanger.copy(alpha = 0.42f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "工作目录不可用",
                style = JewelTheme.defaultTextStyle.copy(color = AppText),
            )
            Text(
                text = message,
                style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(onClick = onRelink) { Text("更新工作目录") }
                OutlinedButton(onClick = onDisconnect) { Text("移除工作目录") }
            }
        }
    }
}
