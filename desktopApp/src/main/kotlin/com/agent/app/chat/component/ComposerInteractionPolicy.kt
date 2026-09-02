package com.agent.app.chat.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Dp
import com.agent.app.design.AppAccent
import com.agent.app.design.HeaderGlyph
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset

internal const val COMPOSER_BORDER_FLOW_DURATION_MILLIS = 2_200

/** 仅在 Agent 实际执行工具或生成输出时启用 Composer 的流光反馈。 */
internal fun shouldAnimateComposerBorder(executionState: ExecutionState): Boolean =
    executionState == ExecutionState.Running

/** 描述沿 Composer 边框路径移动的一段连续高亮。 */
internal data class ComposerBorderFlowSegment(
    val startDistance: Float,
    val endDistance: Float,
)

/**
 * 将环形边框上的流光拆分为一个或两个可绘制路径段，跨越路径末端时从起点继续。
 */
internal fun composerBorderFlowSegments(
    pathLength: Float,
    progress: Float,
    ratio: Float = 0.18f,
): List<ComposerBorderFlowSegment> {
    require(pathLength > 0f) { "Path length must be positive" }
    require(ratio in 0f..1f) { "Flow ratio must be between zero and one" }
    val head = ((progress % 1f + 1f) % 1f) * pathLength
    val tail = head - pathLength * ratio
    return if (tail >= 0f) {
        listOf(ComposerBorderFlowSegment(tail, head))
    } else {
        listOfNotNull(
            ComposerBorderFlowSegment(tail + pathLength, pathLength).takeIf { it.startDistance < it.endDistance },
            ComposerBorderFlowSegment(0f, head).takeIf { it.startDistance < it.endDistance },
        )
    }
}

/**
 * Composer 底部可互斥展开的菜单。
 */
internal enum class ComposerMenu {
    PROVIDER,
    MODEL,
    REASONING,
    PERMISSION,
}

/**
 * 点击另一触发器时直接切换菜单，重复点击当前触发器时关闭。
 */
internal fun nextComposerMenu(
    current: ComposerMenu?,
    requested: ComposerMenu,
): ComposerMenu? = requested.takeUnless { it == current }

/**
 * 旧 popup 的延迟关闭回调只能关闭自己，不能覆盖刚切换的新菜单。
 */
internal fun dismissComposerMenu(
    current: ComposerMenu?,
    dismissed: ComposerMenu,
): ComposerMenu? = current.takeUnless { it == dismissed }

/**
 * 将 Composer 主动作状态映射为矢量图标。
 */
internal fun composerPrimaryActionGlyph(danger: Boolean): HeaderGlyph =
    if (danger) HeaderGlyph.STOP else HeaderGlyph.SEND

/**
 * 输入框最多占用主工作区的一半，确保时间线始终保留足够的可见空间。
 */
internal fun maxComposerInputHeight(workspaceHeight: Dp): Dp = workspaceHeight / 2

/**
 * 仅在输入内容超过可见区域时显示输入区滚动条。
 */
internal fun shouldShowComposerInputScrollbar(maxScrollValue: Int): Boolean = maxScrollValue > 0

/**
 * 拖选文本接近输入框边缘时返回应执行的滚动增量，中央区域保持静止。
 */
internal fun composerSelectionScrollDelta(pointerY: Float, viewportHeight: Int): Float = when {
    pointerY < 18f -> -24f
    pointerY > viewportHeight - 18f -> 24f
    else -> 0f
}

/**
 * Shift 加方向键扩展选择范围时，让外层输入区域跟随选区继续滚动。
 */
internal fun composerKeyboardSelectionScrollDelta(
    key: Key,
    isShiftPressed: Boolean,
): Float = if (!isShiftPressed) {
    0f
} else {
    when (key) {
        Key.DirectionUp -> -28f
        Key.DirectionDown -> 28f
        else -> 0f
    }
}

/**
 * 仅在 Enter 抬起且未按住 Shift 时发送 composer。
 */
internal fun shouldSubmitComposerKey(
    key: Key,
    eventType: KeyEventType,
    isShiftPressed: Boolean,
): Boolean = key == Key.Enter && eventType == KeyEventType.KeyUp && !isShiftPressed

/**
 * 提取当前光标处可用于 `/` 浏览器筛选的命令片段。命令仅在消息开头、且尚未输入参数时显示，避免
 * 正常正文中的斜杠触发菜单。
 */
internal fun activeSlashCommandQuery(
    draft: String,
    selectionStart: Int,
): String? {
    val cursor = selectionStart.coerceIn(0, draft.length)
    val prefix = draft.substring(0, cursor)
    if (!prefix.startsWith('/')) return null
    val query = prefix.drop(1)
    return query.takeUnless { value -> value.any(Char::isWhitespace) }
}

/** 从当前光标处提取未完成的 `@` 工作区文件查询；邮件地址等普通文本不会触发。 */
internal fun activeWorkspaceReferenceQuery(
    draft: String,
    selectionStart: Int,
): String? {
    val cursor = selectionStart.coerceIn(0, draft.length)
    if (cursor == 0) return null
    val atIndex = draft.lastIndexOf('@', startIndex = cursor - 1)
    if (atIndex < 0 || (atIndex > 0 && !draft[atIndex - 1].isWhitespace())) return null
    val query = draft.substring(atIndex + 1, cursor)
    return query.takeUnless { value -> value.any(Char::isWhitespace) }
}

/** 权限模式在选择器及菜单中共用的文案与风险色。 */
internal data class PermissionPresentation(
    val label: String,
    val description: String,
    val tone: Color,
)

/** 为每种权限模式提供唯一且一致的展示信息。 */
internal fun permissionPresentation(permissionPreset: PermissionPreset): PermissionPresentation =
    when (permissionPreset) {
        PermissionPreset.DEFAULT -> PermissionPresentation(
            label = "Ask",
            description = "首次使用每种工具时请求确认",
            tone = Color(0xFF5A5C60),
        )

        PermissionPreset.AUTO -> PermissionPresentation(
            label = "Auto",
            description = "由独立审批模型决定执行或询问",
            tone = Color(0xFF245286),
        )

        PermissionPreset.EDIT_ALLOW -> PermissionPresentation(
            label = "Allow Edits",
            description = "自动接受文件编辑权限",
            tone = Color(0xFF76561B),
        )

        PermissionPreset.PLAN -> PermissionPresentation(
            label = "Plan",
            description = "修改前先完成计划",
            tone = Color(0xFF55479A),
        )

        PermissionPreset.BRAVE -> PermissionPresentation(
            label = "Full Access",
            description = "跳过所有权限确认",
            tone = Color(0xFF8E3541),
        )
    }

/** Ask 沿用当前蓝色，其他权限模式使用其菜单徽标的语义色描绘 Composer 边框。 */
internal fun composerBorderTone(permissionPreset: PermissionPreset): Color = when (permissionPreset) {
    PermissionPreset.DEFAULT -> AppAccent
    else -> permissionPresentation(permissionPreset).tone
}
