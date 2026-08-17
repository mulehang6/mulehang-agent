@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.chat.state.buildWorkspaceLabel
import com.agent.app.design.DesktopAccentBlue
import com.agent.app.design.AppMuted
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.ideaFrameAmbientBackground
import com.agent.app.platform.pickWorkspaceDirectory
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mulehang_agent.desktopapp.generated.resources.Res
import mulehang_agent.desktopapp.generated.resources.mulehang_agent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.utils.clientRegion

internal const val HEADER_PROJECT_ICON_SIZE_DP = 20
internal const val HEADER_PROJECT_ICON_MENU_WIDTH_DP = 180
internal const val TITLE_BAR_ACTION_HEIGHT_DP = 40
internal const val TITLE_BAR_SIDEBAR_CLIENT_REGION_KEY = "sidebar-toggle"
internal const val TITLE_BAR_PROJECT_CLIENT_REGION_KEY = "project-selector"
internal const val TITLE_BAR_BRANCH_CLIENT_REGION_KEY = "branch-menu"
internal const val TITLE_BAR_PROJECT_SELECT_ACTION_LABEL = "选择工作区…"
internal const val TITLE_BAR_BRANCH_REFRESH_ACTION_LABEL = "刷新分支"
internal const val TITLE_BAR_BRANCH_COPY_ACTION_LABEL = "复制分支名"

/** Jewel 装饰窗口中的 IDEA 风格标题栏内容。 */
@Composable
internal fun DecoratedWindowScope.ChatTitleBar(
    state: ChatWindowState,
    projectRoot: Path?,
    sidebarVisible: Boolean,
    onToggleSidebar: () -> Unit,
    onGlobalFeedback: (AppFeedbackState) -> Unit,
    frameGradientAnchorPx: Float?,
    onFrameGradientAnchorChanged: (Float) -> Unit,
) {
    val activeConversation = state.ui.activeConversationOrNull
    val workspacePath = activeConversation?.workspacePath
        ?.takeIf { state.workspaceIssueForPath(it) == null }
        ?: projectRoot?.toString()
    val projectLabel = titleBarProjectLabel(activeConversation?.workspacePath, activeConversation?.workspaceName, projectRoot)
    var branchName by remember(workspacePath) { mutableStateOf("") }
    var branchRefreshToken by remember(workspacePath) { mutableStateOf(0) }
    val projectIconPainter = painterResource(Res.drawable.mulehang_agent)
    val palette = LocalDesktopPalette.current

    LaunchedEffect(workspacePath, branchRefreshToken) {
        branchName = if (workspacePath == null) "" else readWorkspaceBranch(workspacePath)
    }

    TitleBar(
        modifier = if (palette.isDark) {
            Modifier.ideaFrameAmbientBackground(
                frameColor = palette.frameBackground,
                projectColor = palette.titleBarGradientStart,
                anchorXPx = frameGradientAnchorPx,
            )
        } else {
            Modifier
        },
        gradientStartColor = Color.Unspecified,
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Start)
                .fillMaxHeight()
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                painter = projectIconPainter,
                contentDescription = "mulehang-agent 项目图标",
                modifier = Modifier.size(HEADER_PROJECT_ICON_SIZE_DP.dp),
            )
            IconActionButton(
                key = AllIconsKeys.General.Menu,
                contentDescription = if (sidebarVisible) "隐藏任务侧栏" else "显示任务侧栏",
                onClick = onToggleSidebar,
                modifier = Modifier
                    .size(TITLE_BAR_ACTION_HEIGHT_DP.dp)
                    .clientRegion(TITLE_BAR_SIDEBAR_CLIENT_REGION_KEY),
                iconModifier = Modifier.size(20.dp),
            )
            Dropdown(
                modifier = Modifier
                    .height(TITLE_BAR_ACTION_HEIGHT_DP.dp)
                    .clientRegion(TITLE_BAR_PROJECT_CLIENT_REGION_KEY),
                menuModifier = Modifier.widthIn(min = HEADER_PROJECT_ICON_MENU_WIDTH_DP.dp),
                menuContent = {
                    passiveItem {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(text = projectLabel, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = workspacePath ?: "尚未选择工作区",
                                color = AppMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    selectableItem(
                        selected = false,
                        onClick = {
                            pickWorkspaceDirectory()?.let { selectedPath ->
                                state.createConversationForWorkspace(selectedPath)
                                onGlobalFeedback(AppFeedbackState(message = "已切换工作区", anchor = null))
                            }
                        },
                    ) {
                        Text(TITLE_BAR_PROJECT_SELECT_ACTION_LABEL)
                    }
                },
            ) {
                ProjectTitleBarChip(
                    projectLabel = projectLabel,
                    onProjectIconPositioned = onFrameGradientAnchorChanged,
                )
            }
            if (shouldShowHeaderBranchChip(branchName)) {
                Dropdown(
                    modifier = Modifier
                        .height(TITLE_BAR_ACTION_HEIGHT_DP.dp)
                        .clientRegion(TITLE_BAR_BRANCH_CLIENT_REGION_KEY),
                    menuContent = {
                        passiveItem {
                            Text(
                                text = branchName,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        selectableItem(
                            selected = false,
                            onClick = { branchRefreshToken++ },
                        ) {
                            Text(TITLE_BAR_BRANCH_REFRESH_ACTION_LABEL)
                        }
                        selectableItem(
                            selected = false,
                            onClick = {
                                copyHeaderBranchToClipboard(branchName)
                                onGlobalFeedback(AppFeedbackState(message = headerBranchCopiedFeedbackMessage(), anchor = null))
                            },
                        ) {
                            Text(TITLE_BAR_BRANCH_COPY_ACTION_LABEL)
                        }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            key = AllIconsKeys.Vcs.Branch,
                            contentDescription = "当前分支",
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = branchName,
                            modifier = Modifier.padding(start = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 渲染标题栏中由 Jewel 下拉组件包裹的项目徽章和名称。 */
@Composable
private fun ProjectTitleBarChip(
    projectLabel: String,
    onProjectIconPositioned: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.height(TITLE_BAR_ACTION_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(DesktopAccentBlue.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                .onGloballyPositioned { coordinates ->
                    onProjectIconPositioned(coordinates.positionInWindow().x + coordinates.size.width / 2f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = titleBarProjectMonogram(projectLabel),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = projectLabel,
            color = Color.White,
            modifier = Modifier.padding(start = 6.dp).widthIn(max = 180.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 返回包含当前工作区的最近 IDEA 项目根目录；没有 `.idea` 时保留原始目录。 */
internal fun titleBarProjectRoot(
    projectRoot: Path?,
    isIdeaProjectRoot: (Path) -> Boolean = { root -> Files.isDirectory(root.resolve(".idea")) },
): Path? = projectRoot?.toAbsolutePath()?.normalize()?.let { root ->
    generateSequence(root) { it.parent }.firstOrNull(isIdeaProjectRoot) ?: root
}

/** 从 IDE 项目根目录生成标题栏项目名称，并在无根目录时回退活动工作区。 */
internal fun titleBarProjectLabel(workspacePath: String?, workspaceName: String?, projectRoot: Path?): String {
    val activeProjectRoot = titleBarProjectRoot(projectRoot)
        ?: workspacePath?.let { workspace -> runCatching { titleBarProjectRoot(Path.of(workspace)) }.getOrNull() }
    return activeProjectRoot?.fileName?.toString().orEmpty()
        .ifBlank { workspacePath?.let { buildWorkspaceLabel(it, workspaceName) }.orEmpty() }
        .ifBlank { "mulehang-agent" }
}

/** 返回项目徽章使用的至多两个首字符。 */
internal fun titleBarProjectMonogram(projectLabel: String): String = projectLabel.trim().take(2).uppercase().ifBlank { "MH" }

/** 将分支名复制到系统剪贴板。 */
private fun copyHeaderBranchToClipboard(branch: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(branch), null)
}

/** 返回复制分支后显示在全局 toast 中的反馈文案。 */
internal fun headerBranchCopiedFeedbackMessage(): String = "已复制"

/** 仅将成功读取到的非空分支名渲染为标题栏操作。 */
internal fun shouldShowHeaderBranchChip(branchName: String): Boolean = branchName.isNotBlank()

/** Git 调用失败时丢弃标准输出，避免错误文本污染标题栏。 */
internal fun resolveHeaderBranchOutput(exitCode: Int, output: String): String =
    output.trim().takeIf { exitCode == 0 }.orEmpty()

/** 在后台读取工作区当前 Git 分支。 */
private suspend fun readWorkspaceBranch(workspacePath: String): String = withContext(Dispatchers.IO) {
    runCatching {
        val process = ProcessBuilder("git", "-C", workspacePath, "branch", "--show-current").start()
        val branch = process.inputStream.bufferedReader().use { it.readText() }
        process.errorStream.bufferedReader().use { it.readText() }
        resolveHeaderBranchOutput(process.waitFor(), branch)
    }.getOrDefault("")
}

/** 组装标题栏任务上下文文本。 */
internal fun buildHeaderConversationLabel(workspace: String, branch: String, taskTitle: String): String =
    "${buildHeaderConversationPrefix(workspace, branch)} $taskTitle"

/** 组装标题栏工作区与分支前缀。 */
internal fun buildHeaderConversationPrefix(workspace: String, branch: String): String = "$workspace : $branch /"
