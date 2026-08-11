@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.agent.app.bootstrap.APP_TITLE_BAR_HEIGHT_DP
import com.agent.app.bootstrap.WindowChromeMode
import com.agent.app.bootstrap.toggleWindowPlacement
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.chat.state.buildWorkspaceLabel
import com.agent.app.chat.state.taskStatusFor
import com.agent.app.design.AppDanger
import com.agent.app.design.AppHeaderBackground
import com.agent.app.design.AppHoverBackground
import com.agent.app.design.AppText
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.MenuGrowthOrigin
import com.agent.app.design.PopupMenuBackground
import com.agent.app.design.PopupMenuBorder
import com.agent.app.design.PopupMenuShape
import com.agent.app.design.RingHeaderActionButton
import com.agent.app.design.PopupMenuShadowInset
import com.agent.app.design.popupMenuSurface
import com.agent.app.design.RingTooltip
import com.agent.app.design.menuGrowthTransformOrigin
import com.agent.app.design.rememberMenuGrowthMotion
import mulehang_agent.desktopapp.generated.resources.Res
import mulehang_agent.desktopapp.generated.resources.mulehang_agent
import org.jetbrains.compose.resources.painterResource
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val NATIVE_TITLE_BAR_MENU_BACKGROUND = java.awt.Color(0x1E, 0x1F, 0x22)
private const val NATIVE_TITLE_BAR_TASK_HIT_WIDTH_DP = 560
private const val NATIVE_TITLE_BAR_MENU_SEAM_OVERLAP_PX = 1
internal const val HEADER_TASK_TITLE_FONT_SIZE_SP = 16
internal const val HEADER_TASK_CHIP_HEIGHT_DP = 36
internal const val HEADER_TASK_CHIP_HORIZONTAL_PADDING_DP = 8
internal const val HEADER_BRANCH_CHIP_HEIGHT_DP = HEADER_TASK_CHIP_HEIGHT_DP
internal const val HEADER_BRANCH_CHIP_HORIZONTAL_PADDING_DP = 4
internal const val HEADER_PROJECT_ICON_SIZE_DP = 24
internal const val HEADER_PROJECT_ICON_BUTTON_SIZE_DP = 36
internal const val HEADER_PROJECT_ICON_GAP_DP = 6
internal const val HEADER_PROJECT_ICON_MENU_WIDTH_DP = 180
internal const val HEADER_PROJECT_ICON_MENU_HEIGHT_DP = 64
private val NATIVE_TITLE_BAR_MENU_SEAM_LISTENER_KEY = Any()
private val NATIVE_TITLE_BAR_MENU_CORRECTED_BOUNDS_KEY = Any()

/**
 * 标题栏菜单中等待重命名的任务标识与当前名称。
 */
private data class HeaderTaskRenameTarget(
    val id: String,
    val title: String,
)

/**
 * IDEA 风格的一体化顶部标题栏。
 */
@Composable
internal fun WindowScope.ChatHeader(
    state: ChatWindowState,
    sidebarVisible: Boolean,
    onToggleSidebar: () -> Unit,
    windowState: WindowState,
    windowChromeMode: WindowChromeMode,
    onTitleBarClientPointerEvent: (() -> Unit)?,
    onGlobalFeedback: (AppFeedbackState) -> Unit,
    onGlobalPointerPosition: (Offset) -> Unit,
    onCloseRequest: () -> Unit,
) {
    val activeConversation = state.ui.activeConversationOrNull
    var branchName by remember(activeConversation?.workspacePath) { mutableStateOf("读取分支中…") }
    var taskContextMenuExpanded by remember(activeConversation?.id) { mutableStateOf(false) }
    var renameTarget by remember(activeConversation?.id) { mutableStateOf<HeaderTaskRenameTarget?>(null) }
    var taskTitleHeightPixels by remember(activeConversation?.id) { mutableStateOf(0) }
    var taskContextMenuClickPosition by remember(activeConversation?.id) { mutableStateOf<Offset?>(null) }
    var taskTitleHovered by remember(activeConversation?.id) { mutableStateOf(false) }
    var taskTitlePressed by remember(activeConversation?.id) { mutableStateOf(false) }
    var branchHovered by remember(activeConversation?.workspacePath) { mutableStateOf(false) }
    var branchPressed by remember(activeConversation?.workspacePath) { mutableStateOf(false) }
    var branchOrigin by remember(activeConversation?.workspacePath) { mutableStateOf(Offset.Zero) }
    var branchPointerPosition by remember(activeConversation?.workspacePath) { mutableStateOf<Offset?>(null) }
    var projectIconMenuExpanded by remember { mutableStateOf(false) }
    var projectIconHovered by remember { mutableStateOf(false) }
    var projectIconPressed by remember { mutableStateOf(false) }
    var sidebarMenuHovered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val projectIconPainter = painterResource(Res.drawable.mulehang_agent)
    val taskContextMenuMotion = rememberMenuGrowthMotion(
        expanded = taskContextMenuExpanded,
        label = "header-task-context-menu",
    )
    val taskContextMenuOffset = taskContextMenuClickPosition?.let { pointerPosition ->
        contextMenuOffsetForPointer(
            pointerPosition = pointerPosition,
            anchorHeightPixels = taskTitleHeightPixels,
            density = density.density,
            shadowInset = PopupMenuShadowInset,
        )
    } ?: DpOffset.Zero
    LaunchedEffect(activeConversation?.workspacePath) {
        branchName = activeConversation
            ?.takeIf { state.workspaceIssue(it) == null }
            ?.workspacePath
            ?.let { workspacePath ->
            readWorkspaceBranch(workspacePath)
            }
            .orEmpty()
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppHeaderBackground,
        border = androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(APP_TITLE_BAR_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(HEADER_PROJECT_ICON_BUTTON_SIZE_DP.dp)
                    .background(
                        color = titleBarComposeHoverBackground(
                            nativeHitOverlayEnabled = onTitleBarClientPointerEvent != null,
                            hovered = projectIconHovered,
                            pressed = projectIconPressed,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .onPointerEvent(PointerEventType.Enter) { projectIconHovered = true }
                    .onPointerEvent(PointerEventType.Exit) {
                        projectIconHovered = false
                        projectIconPressed = false
                    }
                    .onPointerEvent(PointerEventType.Press) { projectIconPressed = true }
                    .clickable(enabled = onTitleBarClientPointerEvent == null) {
                        projectIconMenuExpanded = true
                        projectIconPressed = false
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (onTitleBarClientPointerEvent != null) {
                    NativeTitleBarTaskHitOverlay(
                        onClientMouseEvent = onTitleBarClientPointerEvent,
                        onOpenMenu = {
                            projectIconMenuExpanded = true
                            projectIconPressed = false
                        },
                        onPointerMoved = {},
                        onOpenContextMenu = {},
                        onInteractionChanged = { hovered, pressed ->
                            projectIconHovered = hovered
                            projectIconPressed = pressed
                        },
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Image(
                    painter = projectIconPainter,
                    contentDescription = "mulehang-agent 项目图标",
                    modifier = Modifier.size(HEADER_PROJECT_ICON_SIZE_DP.dp),
                )
                DropdownMenu(
                    expanded = projectIconMenuExpanded,
                    onDismissRequest = { projectIconMenuExpanded = false },
                    modifier = Modifier
                        .padding(PopupMenuShadowInset)
                        .width(HEADER_PROJECT_ICON_MENU_WIDTH_DP.dp)
                        .height(HEADER_PROJECT_ICON_MENU_HEIGHT_DP.dp)
                        .popupMenuSurface(),
                    shape = PopupMenuShape,
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = null,
                ) {
                    Spacer(modifier = Modifier.fillMaxSize())
                }
            }
            Box(
                modifier = Modifier
                    .padding(start = HEADER_PROJECT_ICON_GAP_DP.dp),
            ) {
                val menuTooltip = if (sidebarVisible) "隐藏任务侧栏" else "显示任务侧栏"
                if (onTitleBarClientPointerEvent != null) {
                    NativeTitleBarMenuButton(
                        onClientMouseEvent = onTitleBarClientPointerEvent,
                        onClick = onToggleSidebar,
                        tooltip = menuTooltip,
                        hovered = sidebarMenuHovered,
                        onHoverChanged = { sidebarMenuHovered = it },
                    )
                } else {
                    RingHeaderActionButton(
                        glyph = HeaderGlyph.MENU,
                        onClick = onToggleSidebar,
                        inline = false,
                        tooltip = menuTooltip,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        activeConversation?.let { conversation ->
                            Text(
                                text = "${buildWorkspaceLabel(conversation.workspacePath, conversation.workspaceName)} :",
                                modifier = Modifier.widthIn(max = 192.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = AppText,
                                    fontSize = HEADER_TASK_TITLE_FONT_SIZE_SP.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            if (shouldShowHeaderBranchChip(branchName)) {
                                Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .height(HEADER_BRANCH_CHIP_HEIGHT_DP.dp)
                                    .widthIn(max = 220.dp)
                                    .onGloballyPositioned { coordinates ->
                                        branchOrigin = coordinates.positionInRoot()
                                    }
                                    .background(
                                        color = titleBarComposeHoverBackground(
                                            nativeHitOverlayEnabled = onTitleBarClientPointerEvent != null,
                                            hovered = branchHovered,
                                            pressed = branchPressed,
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .onPointerEvent(PointerEventType.Enter) { branchHovered = true }
                                    .onPointerEvent(PointerEventType.Exit) {
                                        branchHovered = false
                                        branchPressed = false
                                    }
                                    .onPointerEvent(PointerEventType.Press) { event ->
                                        branchPressed = true
                                        branchPointerPosition = event.changes.firstOrNull()
                                            ?.position
                                            ?.let(branchOrigin::plus)
                                    }
                                    .onPointerEvent(PointerEventType.Move) { event ->
                                        branchPointerPosition = event.changes.firstOrNull()
                                            ?.position
                                            ?.let(branchOrigin::plus)
                                    }
                                    .clickable(enabled = onTitleBarClientPointerEvent == null) {
                                        copyHeaderBranchToClipboard(branchName)
                                        onGlobalFeedback(
                                            AppFeedbackState(
                                                message = headerBranchCopiedFeedbackMessage(),
                                                anchor = feedbackToastAnchor(branchPointerPosition),
                                            ),
                                        )
                                        branchPressed = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (
                                    onTitleBarClientPointerEvent != null
                                ) {
                                    NativeTitleBarTaskHitOverlay(
                                        onClientMouseEvent = onTitleBarClientPointerEvent,
                                        onOpenMenu = { localPointerPosition ->
                                            copyHeaderBranchToClipboard(branchName)
                                            onGlobalFeedback(
                                                AppFeedbackState(
                                                    message = headerBranchCopiedFeedbackMessage(),
                                                    anchor = feedbackToastAnchor(branchOrigin + localPointerPosition),
                                                ),
                                            )
                                        },
                                        onPointerMoved = { localPointerPosition ->
                                            onGlobalPointerPosition(branchOrigin + localPointerPosition)
                                        },
                                        onOpenContextMenu = {},
                                        onInteractionChanged = { hovered, pressed ->
                                            branchHovered = hovered
                                            branchPressed = pressed
                                        },
                                        modifier = Modifier.matchParentSize(),
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(HEADER_BRANCH_CHIP_HORIZONTAL_PADDING_DP.dp))
                                    Text(
                                        text = branchName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = AppText,
                                            fontSize = HEADER_TASK_TITLE_FONT_SIZE_SP.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                    )
                                    Spacer(modifier = Modifier.width(HEADER_BRANCH_CHIP_HORIZONTAL_PADDING_DP.dp))
                                }
                            }
                                Text(
                                    text = "/",
                                    modifier = Modifier.padding(start = 4.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = AppText,
                                        fontSize = HEADER_TASK_TITLE_FONT_SIZE_SP.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .height(HEADER_TASK_CHIP_HEIGHT_DP.dp)
                                    .padding(start = 4.dp)
                                    .weight(1f, fill = false)
                                    .onSizeChanged { size ->
                                        taskTitleHeightPixels = size.height
                                    }
                                    .background(
                                        color = titleBarComposeHoverBackground(
                                            nativeHitOverlayEnabled = onTitleBarClientPointerEvent != null,
                                            hovered = taskTitleHovered,
                                            pressed = taskTitlePressed,
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .graphicsLayer {
                                        val scale = if (taskTitlePressed) 0.985f else 1f
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .onPointerEvent(PointerEventType.Press) { event ->
                                        if (event.buttons.isSecondaryPressed) {
                                            taskContextMenuClickPosition = event.changes.firstOrNull()?.position ?: Offset.Zero
                                            taskContextMenuExpanded = true
                                        }
                                    }
                                    .clickable(
                                        enabled = onTitleBarClientPointerEvent == null,
                                        onClick = {
                                            taskContextMenuClickPosition = null
                                            taskContextMenuExpanded = true
                                        },
                                    ),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (
                                    onTitleBarClientPointerEvent != null
                                ) {
                                    NativeTitleBarTaskHitOverlay(
                                        onClientMouseEvent = onTitleBarClientPointerEvent,
                                        onOpenMenu = {
                                            taskContextMenuClickPosition = null
                                            taskContextMenuExpanded = true
                                        },
                                        onPointerMoved = {},
                                        onOpenContextMenu = { pointerPosition ->
                                            taskContextMenuClickPosition = pointerPosition
                                            taskContextMenuExpanded = true
                                        },
                                        onInteractionChanged = { hovered, pressed ->
                                            taskTitleHovered = hovered
                                            taskTitlePressed = pressed
                                        },
                                        modifier = Modifier.matchParentSize(),
                                    )
                                }
                                Row(
                                    modifier = Modifier.padding(horizontal = HEADER_TASK_CHIP_HORIZONTAL_PADDING_DP.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TaskStatusIndicator(taskStatusFor(conversation))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (shouldShowConversationTitleText(conversation.titleState)) {
                                        Text(
                                            text = conversation.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = AppText,
                                                fontSize = HEADER_TASK_TITLE_FONT_SIZE_SP.sp,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                        )
                                    } else {
                                        Box {
                                            TitleGeneratingIndicator()
                                        }
                                    }
                                }
                                DropdownMenu(
                                    expanded = taskContextMenuExpanded,
                                    onDismissRequest = { taskContextMenuExpanded = false },
                                    offset = taskContextMenuOffset,
                                    modifier = Modifier
                                        .padding(PopupMenuShadowInset)
                                        .width(TaskContextMenuWidth)
                                        .graphicsLayer {
                                            transformOrigin = menuGrowthTransformOrigin(MenuGrowthOrigin.Context)
                                            scaleX = taskContextMenuMotion.scale
                                            scaleY = taskContextMenuMotion.scale
                                            alpha = taskContextMenuMotion.alpha
                                            translationY = taskContextMenuMotion.translationYDp * density.density
                                        }
                                        .popupMenuSurface(),
                                    shape = TaskContextMenuShape,
                                    containerColor = Color.Transparent,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    border = null,
                                ) {
                                    TaskContextMenuActions(
                                        onDelete = {
                                            taskContextMenuExpanded = false
                                            state.deleteConversation(conversation.id)
                                        },
                                        onRename = {
                                            taskContextMenuExpanded = false
                                            renameTarget = HeaderTaskRenameTarget(
                                                id = conversation.id,
                                                title = conversation.title,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                WindowDraggableArea(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
            if (windowChromeMode == WindowChromeMode.COMPOSE_FALLBACK) {
                WindowControlButton(
                    symbol = "—",
                    description = "最小化窗口",
                    onClick = { windowState.isMinimized = true },
                )
                WindowControlButton(
                    symbol = if (windowState.placement == WindowPlacement.Maximized) "❐" else "□",
                    description = if (windowState.placement == WindowPlacement.Maximized) "还原窗口" else "最大化窗口",
                    onClick = { windowState.placement = toggleWindowPlacement(windowState.placement) },
                )
                WindowControlButton(
                    symbol = "×",
                    description = "关闭窗口",
                    danger = true,
                    onClick = onCloseRequest,
                )
            }
        }
    }
    renameTarget?.let { target ->
        TaskRenameDialog(
            initialTitle = target.title,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                state.renameConversation(target.id, title)
                renameTarget = null
            },
        )
    }
}

/**
 * 返回 Compose 标题文字层的悬浮底色；原生命中层启用时由 AWT 层独占绘制，避免叠出套框。
 */
internal fun titleBarComposeHoverBackground(
    nativeHitOverlayEnabled: Boolean,
    hovered: Boolean,
    pressed: Boolean,
): Color = if (nativeHitOverlayEnabled) {
    Color.Transparent
} else {
    when {
        pressed -> AppHoverBackground.copy(alpha = 0.92f)
        hovered -> AppHoverBackground.copy(alpha = 0.72f)
        else -> Color.Transparent
    }
}

/**
 * 组装标题栏内紧凑的任务上下文，不额外插入与产品标题冲突的分隔符。
 */
internal fun buildHeaderConversationLabel(
    workspace: String,
    branch: String,
    taskTitle: String,
): String = "${buildHeaderConversationPrefix(workspace, branch)} $taskTitle"

/**
 * 组装标题栏中不会因异步标题生成而消失的项目上下文。
 */
internal fun buildHeaderConversationPrefix(
    workspace: String,
    branch: String,
): String = "$workspace : $branch /"

/** 将当前工作区分支名复制到系统剪贴板，供标题栏的单击操作使用。 */
private fun copyHeaderBranchToClipboard(branch: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(branch), null)
}

/** 返回复制分支后显示在全局 toast 中的反馈文案。 */
internal fun headerBranchCopiedFeedbackMessage(): String = "已复制"

/** 仅将成功读取到的非空分支名渲染为标题栏胶囊。 */
internal fun shouldShowHeaderBranchChip(branchName: String): Boolean = branchName.isNotBlank()

/** Git 调用失败时丢弃标准输出，避免错误文本污染标题栏。 */
internal fun resolveHeaderBranchOutput(exitCode: Int, output: String): String =
    output.trim().takeIf { exitCode == 0 }.orEmpty()

/**
 * 在后台读取工作区当前 Git 分支；非 Git 工作区保留清晰的回退文案。
 */
private suspend fun readWorkspaceBranch(workspacePath: String): String = withContext(Dispatchers.IO) {
    runCatching {
        val process = ProcessBuilder("git", "-C", workspacePath, "branch", "--show-current")
            .start()
        val branch = process.inputStream
            .bufferedReader()
            .use { it.readText().trim() }
        process.errorStream.bufferedReader().use { it.readText() }
        resolveHeaderBranchOutput(process.waitFor(), branch)
    }.getOrDefault("")
}

/**
 * 在 JBR 自定义标题栏内放置真实 AWT 命中组件，确保菜单事件先于原生拖动处理。
 */
@Composable
private fun NativeTitleBarMenuButton(
    onClientMouseEvent: () -> Unit,
    onClick: () -> Unit,
    tooltip: String,
    hovered: Boolean,
    onHoverChanged: (Boolean) -> Unit,
) {
    RingTooltip(text = tooltip, belowAnchor = true, externalHovered = hovered) {
        SwingPanel(
            factory = {
                createNativeTitleBarMenuHitTarget(
                    onClientMouseEvent = onClientMouseEvent,
                    onClick = onClick,
                    onHoverChanged = onHoverChanged,
                )
            },
            update = { component ->
                component.updateActions(
                    onClientMouseEvent = onClientMouseEvent,
                    onClick = onClick,
                    onHoverChanged = onHoverChanged,
                )
            },
            modifier = Modifier.size(36.dp),
            background = AppHeaderBackground,
        )
    }
}

/**
 * 在原生标题栏中为项目、分支和任务标题提供透明的 AWT 客户区点击层。
 */
@Composable
private fun NativeTitleBarTaskHitOverlay(
    onClientMouseEvent: () -> Unit,
    onOpenMenu: (Offset) -> Unit,
    onPointerMoved: (Offset) -> Unit,
    onOpenContextMenu: (Offset) -> Unit,
    onInteractionChanged: (hovered: Boolean, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwingPanel(
        factory = {
            createNativeTitleBarTaskHitTarget(
                onClientMouseEvent = onClientMouseEvent,
                onClick = onOpenMenu,
                onPointerMoved = onPointerMoved,
                onContextMenuClick = onOpenContextMenu,
                onInteractionChanged = onInteractionChanged,
            )
        },
        update = { component ->
            component.updateActions(
                onClientMouseEvent = onClientMouseEvent,
                onClick = onOpenMenu,
                onPointerMoved = onPointerMoved,
                onContextMenuClick = onOpenContextMenu,
                onInteractionChanged = onInteractionChanged,
            )
        },
        modifier = modifier,
        background = AppHeaderBackground,
    )
}

/**
 * 创建仅覆盖菜单按钮范围的 AWT 命中目标；窗口其余标题栏仍保留原生拖动语义。
 */
internal fun createNativeTitleBarMenuHitTarget(
    onClientMouseEvent: () -> Unit,
    onClick: () -> Unit,
    onHoverChanged: (Boolean) -> Unit = {},
): NativeTitleBarMenuHitTarget = NativeTitleBarMenuHitTarget(
    onClientMouseEvent = onClientMouseEvent,
    onClick = onClick,
    onHoverChanged = onHoverChanged,
)

/**
 * 创建覆盖任务上下文文字的透明客户端命中组件。
 */
internal fun createNativeTitleBarTaskHitTarget(
    onClientMouseEvent: () -> Unit,
    onClick: (Offset) -> Unit,
    onPointerMoved: (Offset) -> Unit = {},
    onContextMenuClick: (Offset) -> Unit = { pointerPosition -> onClick(pointerPosition) },
    onInteractionChanged: (hovered: Boolean, pressed: Boolean) -> Unit = { _, _ -> },
): NativeTitleBarTaskHitTarget = NativeTitleBarTaskHitTarget(
    onClientMouseEvent = onClientMouseEvent,
    onClick = onClick,
    onPointerMoved = onPointerMoved,
    onContextMenuClick = onContextMenuClick,
    onInteractionChanged = onInteractionChanged,
)

/**
 * 将菜单命中组件及 SwingPanel 互操作宿主同步为标题栏底色，避免边缘露出默认浅色画布。
 */
internal fun synchronizeNativeTitleBarMenuInteropBackground(component: NativeTitleBarMenuHitTarget) {
    component.background = NATIVE_TITLE_BAR_MENU_BACKGROUND
    (component.parent as? JComponent)?.apply {
        background = NATIVE_TITLE_BAR_MENU_BACKGROUND
        isOpaque = true
        border = null
        installNativeTitleBarMenuSeamCover(component)
    }
}

/**
 * 将任务标题命中层的 Swing 宿主锁定为标题栏深色，避免透明互操作层被默认白色画布清屏。
 */
internal fun synchronizeNativeTitleBarTaskInteropBackground(component: NativeTitleBarTaskHitTarget) {
    component.background = NATIVE_TITLE_BAR_MENU_BACKGROUND
    component.isOpaque = true
    (component.parent as? JComponent)?.apply {
        background = NATIVE_TITLE_BAR_MENU_BACKGROUND
        isOpaque = true
        border = null
    }
}

/**
 * 将 Swing 宿主向左扩展一个逻辑像素，并保持菜单内容原位，覆盖全屏切换后的高 DPI 混合清除缝。
 */
internal fun coverNativeTitleBarMenuInteropSeam(component: NativeTitleBarMenuHitTarget) {
    val host = component.parent as? JComponent ?: return
    val hostBounds = host.bounds
    val correctedBounds = host.getClientProperty(NATIVE_TITLE_BAR_MENU_CORRECTED_BOUNDS_KEY) as? Rectangle
    if (hostBounds.width <= 0 || hostBounds.height <= 0 || hostBounds == correctedBounds) return

    val expandedBounds = Rectangle(
        hostBounds.x - NATIVE_TITLE_BAR_MENU_SEAM_OVERLAP_PX,
        hostBounds.y,
        hostBounds.width + NATIVE_TITLE_BAR_MENU_SEAM_OVERLAP_PX,
        hostBounds.height,
    )
    host.putClientProperty(NATIVE_TITLE_BAR_MENU_CORRECTED_BOUNDS_KEY, expandedBounds)
    host.bounds = expandedBounds
    component.setBounds(
        NATIVE_TITLE_BAR_MENU_SEAM_OVERLAP_PX,
        0,
        hostBounds.width,
        hostBounds.height,
    )
}

/**
 * 监听 Compose 对 SwingPanel 宿主的后续布局更新，持续恢复一像素覆盖。
 */
private fun JComponent.installNativeTitleBarMenuSeamCover(component: NativeTitleBarMenuHitTarget) {
    if (getClientProperty(NATIVE_TITLE_BAR_MENU_SEAM_LISTENER_KEY) == null) {
        val listener = object : ComponentAdapter() {
            override fun componentMoved(event: ComponentEvent) = coverNativeTitleBarMenuInteropSeam(component)

            override fun componentResized(event: ComponentEvent) = coverNativeTitleBarMenuInteropSeam(component)
        }
        addComponentListener(listener)
        putClientProperty(NATIVE_TITLE_BAR_MENU_SEAM_LISTENER_KEY, listener)
    }
    coverNativeTitleBarMenuInteropSeam(component)
    SwingUtilities.invokeLater { coverNativeTitleBarMenuInteropSeam(component) }
}

/**
 * AWT 标题命中组件：JBR 需要它来把标题栏任务文字区域声明为客户区，并直接绘制悬浮反馈。
 */
internal class NativeTitleBarTaskHitTarget(
    onClientMouseEvent: () -> Unit,
    onClick: (Offset) -> Unit,
    onPointerMoved: (Offset) -> Unit,
    onContextMenuClick: (Offset) -> Unit,
    onInteractionChanged: (hovered: Boolean, pressed: Boolean) -> Unit,
) : JPanel() {
    private var clientMouseEventAction = onClientMouseEvent
    private var clickAction = onClick
    private var pointerMovedAction = onPointerMoved
    private var contextMenuClickAction = onContextMenuClick
    private var interactionChangedAction = onInteractionChanged
    private var hovered = false
    private var pressed = false

    private val pointerAdapter = object : MouseAdapter() {
        override fun mouseClicked(event: MouseEvent) = markClientArea()

        override fun mousePressed(event: MouseEvent) {
            markClientArea()
            if (event.button == MouseEvent.BUTTON1 || event.button == MouseEvent.BUTTON3) {
                pressed = true
                reportInteraction()
            }
        }

        override fun mouseReleased(event: MouseEvent) {
            markClientArea()
            val shouldOpenMenu = pressed &&
                    (event.button == MouseEvent.BUTTON1 || event.button == MouseEvent.BUTTON3) &&
                    contains(event.point)
            pressed = false
            reportInteraction()
            if (shouldOpenMenu) {
                if (event.button == MouseEvent.BUTTON3) {
                    contextMenuClickAction(Offset(event.x.toFloat(), event.y.toFloat()))
                } else {
                    clickAction(Offset(event.x.toFloat(), event.y.toFloat()))
                }
            }
        }

        override fun mouseEntered(event: MouseEvent) {
            markClientArea()
            hovered = true
            reportInteraction()
        }

        override fun mouseExited(event: MouseEvent) {
            hovered = false
            pressed = false
            reportInteraction()
        }

        override fun mouseDragged(event: MouseEvent) {
            markClientArea()
            reportPointerPosition(event)
        }

        override fun mouseMoved(event: MouseEvent) {
            markClientArea()
            reportPointerPosition(event)
        }
    }

    init {
        background = NATIVE_TITLE_BAR_MENU_BACKGROUND
        isOpaque = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        getAccessibleContext()?.accessibleName = "任务菜单"
        addMouseListener(pointerAdapter)
        addMouseMotionListener(pointerAdapter)
    }

    /**
     * 挂载后同步 SwingPanel 宿主底色，避免互操作层默认使用白色画布。
     */
    override fun addNotify() {
        super.addNotify()
        synchronizeNativeTitleBarTaskInteropBackground(this)
    }

    /**
     * 命中层位于 Compose 内容上方，因此需自行绘制圆角悬浮与按下反馈；标题文字仍由后续 Compose 层显示。
     */
    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        if (!hovered && !pressed) return

        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics2D.color = if (pressed) {
                java.awt.Color(0x30, 0x32, 0x37)
            } else {
                java.awt.Color(0x35, 0x38, 0x3E)
            }
            graphics2D.fillRoundRect(0, 0, width - 1, height - 1, 16, 16)
        } finally {
            graphics2D.dispose()
        }
    }

    /**
     * 在 Compose 重组时更新回调，避免 Swing 组件继续调用旧状态闭包。
     */
    fun updateActions(
        onClientMouseEvent: () -> Unit,
        onClick: (Offset) -> Unit,
        onPointerMoved: (Offset) -> Unit = {},
        onContextMenuClick: (Offset) -> Unit = { pointerPosition -> onClick(pointerPosition) },
        onInteractionChanged: (hovered: Boolean, pressed: Boolean) -> Unit,
    ) {
        clientMouseEventAction = onClientMouseEvent
        clickAction = onClick
        pointerMovedAction = onPointerMoved
        contextMenuClickAction = onContextMenuClick
        interactionChangedAction = onInteractionChanged
        synchronizeNativeTitleBarTaskInteropBackground(this)
    }

    /**
     * 将当前 AWT 鼠标事件声明为 JBR 自定义标题栏的客户区事件。
     */
    private fun markClientArea() {
        clientMouseEventAction()
    }

    /** 将原生标题栏局部坐标交给 Compose，以持续更新已复制反馈的锚点。 */
    private fun reportPointerPosition(event: MouseEvent) {
        pointerMovedAction(Offset(event.x.toFloat(), event.y.toFloat()))
    }

    /**
     * 将 AWT 触发状态回传给 Compose 标题文字，用于渲染悬浮与按下反馈。
     */
    private fun reportInteraction() {
        interactionChangedAction(hovered, pressed)
    }
}

/**
 * JBR 标题栏菜单的 Swing 命中与绘制组件。
 */
internal class NativeTitleBarMenuHitTarget(
    onClientMouseEvent: () -> Unit,
    onClick: () -> Unit,
    onHoverChanged: (Boolean) -> Unit,
) : JPanel() {
    private var clientMouseEventAction = onClientMouseEvent
    private var clickAction = onClick
    private var hoverChangedAction = onHoverChanged
    private var hovered = false
    private var pressed = false

    private val pointerAdapter = object : MouseAdapter() {
        override fun mouseClicked(event: MouseEvent) = markClientArea()

        override fun mousePressed(event: MouseEvent) {
            markClientArea()
            if (event.button == MouseEvent.BUTTON1) {
                pressed = true
                repaint()
            }
        }

        override fun mouseReleased(event: MouseEvent) {
            markClientArea()
            val shouldClick = pressed && event.button == MouseEvent.BUTTON1 && contains(event.point)
            pressed = false
            repaint()
            if (shouldClick) clickAction()
        }

        override fun mouseEntered(event: MouseEvent) {
            markClientArea()
            hovered = true
            hoverChangedAction(true)
            repaint()
        }

        override fun mouseExited(event: MouseEvent) {
            hovered = false
            pressed = false
            hoverChangedAction(false)
            repaint()
        }

        override fun mouseDragged(event: MouseEvent) = markClientArea()

        override fun mouseMoved(event: MouseEvent) = markClientArea()
    }

    init {
        background = NATIVE_TITLE_BAR_MENU_BACKGROUND
        isOpaque = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        getAccessibleContext()?.accessibleName = "显示任务侧栏"
        addMouseListener(pointerAdapter)
        addMouseMotionListener(pointerAdapter)
    }

    /**
     * 组件挂载进 SwingPanel 后立即同步互操作宿主背景。
     */
    override fun addNotify() {
        super.addNotify()
        synchronizeNativeTitleBarMenuInteropBackground(this)
    }

    /**
     * 在 Compose 重组时刷新事件动作与可访问名称，避免 Swing 工厂保留旧闭包。
     */
    fun updateActions(
        onClientMouseEvent: () -> Unit,
        onClick: () -> Unit,
        onHoverChanged: (Boolean) -> Unit,
    ) {
        clientMouseEventAction = onClientMouseEvent
        clickAction = onClick
        hoverChangedAction = onHoverChanged
        getAccessibleContext()?.accessibleName = "显示任务侧栏"
        synchronizeNativeTitleBarMenuInteropBackground(this)
    }

    /**
     * 按现有 Compose 按钮的尺寸、颜色和圆角绘制原生菜单按钮。
     */
    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val scale = if (pressed) 0.97 else 1.0
            graphics2D.translate(width / 2.0, height / 2.0)
            graphics2D.scale(scale, scale)
            graphics2D.translate(-width / 2.0, -height / 2.0)

            if (hovered || pressed) {
                graphics2D.color = java.awt.Color(0x35, 0x38, 0x3E)
                graphics2D.fillRoundRect(0, 0, width - 1, height - 1, 20, 20)
            }

            graphics2D.color = java.awt.Color.WHITE
            graphics2D.stroke = java.awt.BasicStroke(
                1.8f,
                java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND,
            )
            val left = (width * 0.31).toInt()
            val right = (width * 0.69).toInt()
            listOf(0.34, 0.50, 0.66).forEach { yRatio ->
                val y = (height * yRatio).toInt()
                graphics2D.drawLine(left, y, right, y)
            }
        } finally {
            graphics2D.dispose()
        }
    }

    /**
     * 将当前 AWT 鼠标事件声明为 JBR 客户区事件。
     */
    private fun markClientArea() {
        clientMouseEventAction()
    }
}

/**
 * 标题栏中的原生语义窗口控制按钮。
 */
@Composable
private fun WindowControlButton(
    symbol: String,
    description: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(
                color = when {
                    hovered && danger -> AppDanger.copy(alpha = 0.8f)
                    hovered -> AppHoverBackground
                    else -> Color.Transparent
                },
                shape = RectangleShape,
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium.copy(
                color = AppText,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}
