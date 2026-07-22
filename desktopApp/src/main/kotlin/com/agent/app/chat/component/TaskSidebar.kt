package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.chat.presentation.resolveWorkspaceForTaskCreation
import com.agent.app.chat.state.ChatTaskGroup
import com.agent.app.chat.state.ChatTaskListItemUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppAccent
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.AppSuccess
import com.agent.app.design.AppText
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.RingHeaderActionButton
import com.agent.app.design.RingInputField
import com.agent.app.design.RingPrimaryButton
import com.agent.app.platform.pickWorkspaceDirectory

internal const val TASK_SECTION_TITLE_FONT_SIZE_SP = 13
internal const val TASK_PATH_FONT_SIZE_SP = 13

/**
 * 原型左侧 task 侧栏。
 */
@Composable
internal fun TaskSidebar(
    state: ChatWindowState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    val startTaskInCurrentWorkspace: () -> Unit = {
        val workspacePath = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = state.ui.activeConversationOrNull?.workspacePath,
            forceDirectoryPicker = false,
            pickWorkspaceDirectory = ::pickWorkspaceDirectory,
        )
        if (workspacePath != null) {
            state.createConversationForWorkspace(workspacePath)
        }
    }
    val startTaskInSelectedWorkspace: () -> Unit = {
        val workspacePath = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = state.ui.activeConversationOrNull?.workspacePath,
            forceDirectoryPicker = true,
            pickWorkspaceDirectory = ::pickWorkspaceDirectory,
        )
        if (workspacePath != null) {
            state.createConversationForWorkspace(workspacePath)
        }
    }
    val filteredSections = remember(state.ui.taskSections, searchQuery) {
        state.ui.taskSections.map { section ->
            section.copy(
                tasks = section.tasks.filter { task ->
                    searchQuery.isBlank() ||
                            task.title.contains(searchQuery, ignoreCase = true) ||
                            task.subtitle.contains(searchQuery, ignoreCase = true)
                },
            )
        }
    }
    Column(
        modifier = modifier
            .padding(if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RingInputField(
                modifier = Modifier.weight(1f),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = "搜索任务",
                iconGlyph = HeaderGlyph.SEARCH,
                borderless = true,
            )
            RingHeaderActionButton(
                glyph = HeaderGlyph.ADD,
                onClick = startTaskInSelectedWorkspace,
                inline = true,
                tooltip = "在其他工作区新建任务",
            )
        }
        RingPrimaryButton(
            text = "新建任务",
            onClick = startTaskInCurrentWorkspace,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            filteredSections.forEach { section ->
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AppMuted,
                        fontSize = TASK_SECTION_TITLE_FONT_SIZE_SP.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                    ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    section.tasks.forEach { task ->
                        TaskListItem(
                            task = task,
                            selected = task.id == state.ui.activeTaskId,
                            onClick = { state.selectConversation(task.id) },
                            compact = compact,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 左侧 task 条目。
 */
@Composable
private fun TaskListItem(
    task: ChatTaskListItemUiState,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
) {
    val dotColor = if (task.group == ChatTaskGroup.DONE) AppSuccess else AppAccent
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AppSelectedBackground else Color.Transparent,
        border = null,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(dotColor, CircleShape),
                )
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppText,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = task.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppMuted.copy(alpha = 0.92f),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = TASK_PATH_FONT_SIZE_SP.sp,
                    lineHeight = 18.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                Text(
                    text = task.stats,
                    style = MaterialTheme.typography.labelSmall.copy(color = AppMuted),
                )
            }
        }
    }
}
