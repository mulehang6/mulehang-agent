package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppAccent
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSidebarBackground
import com.agent.app.design.AppText
import com.agent.app.design.RingIsland
import com.agent.shared.chat.model.ConversationItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.tool.plan.parseUpdatePlanPreview

/**
 * 计划卡片里的步骤条目。
 */
internal data class TaskPlanEntry(
    val number: Int,
    val text: String,
    val active: Boolean = false,
)

/**
 * 从真实 update_plan 工具事件中提取出的 plan 卡片。
 */
internal data class PlanCardUiState(
    val title: String,
    val entries: List<TaskPlanEntry>,
)

private data class UpdatePlanPayload(
    val explanation: String? = null,
    val plan: List<UpdatePlanStepPayload> = emptyList(),
)

private data class UpdatePlanStepPayload(
    val step: String,
    val status: String,
)

/**
 * 仅当 agent 真实调用 update_plan 且携带 plan 数据时，才显示 Plan 卡片。
 */
internal fun extractPlanCard(items: List<ConversationItem>): PlanCardUiState? {
    val payload = items
        .asReversed()
        .filterIsInstance<ToolEventItem>()
        .filter { it.toolName == "update_plan" && !it.preview.isNullOrBlank() }
        .firstNotNullOfOrNull { it.preview?.let(::parseUpdatePlanPayload) }
        ?: return null
    if (payload.plan.isEmpty()) return null
    return PlanCardUiState(
        title = "Plan",
        entries = payload.plan.mapIndexed { index, step ->
            TaskPlanEntry(
                number = index + 1,
                text = step.step,
                active = step.status == "in_progress",
            )
        },
    )
}

/**
 * 解析 update_plan 的工具参数预览。
 */
private fun parseUpdatePlanPayload(preview: String): UpdatePlanPayload? = runCatching {
    val parsedPreview = parseUpdatePlanPreview(preview) ?: return@runCatching null
    if (parsedPreview.plan.isEmpty()) {
        null
    } else {
        UpdatePlanPayload(
            explanation = parsedPreview.explanation,
            plan = parsedPreview.plan.map { step ->
                UpdatePlanStepPayload(
                    step = step.step,
                    status = step.status,
                )
            },
        )
    }
}.getOrNull()

/**
 * 计划卡片。
 */
@Composable
internal fun PlanCard(
    title: String,
    entries: List<TaskPlanEntry>,
    modifier: Modifier = Modifier,
) {
    RingIsland(
        modifier = modifier,
        color = AppSidebarBackground,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = AppText,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            entries.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(22.dp),
                        shape = CircleShape,
                        color = if (entry.active) AppAccent else AppChipBackground,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = entry.number.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (entry.active) Color.White else AppMuted,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyMedium.copy(color = AppText),
                    )
                }
            }
        }
    }
}
