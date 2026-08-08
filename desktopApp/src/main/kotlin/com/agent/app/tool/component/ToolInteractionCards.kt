package com.agent.app.tool.component

import com.agent.app.chat.state.PendingApprovalUiState
import com.agent.app.chat.state.PendingQuestionUiState
import com.agent.app.tool.interaction.ApprovalResponse
import com.agent.shared.tool.model.QuestionAnswer
import com.agent.shared.tool.model.QuestionPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.design.AppAccent
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppDanger
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppSidebarBackground
import com.agent.app.design.AppSuccess
import com.agent.app.design.AppText
import com.agent.app.design.RingPrimaryButton

/**
 * 问题卡片的展示模型。
 */
data class QuestionCardModel(
    val questions: List<QuestionPrompt>,
    val allowFreeText: Boolean,
)

/**
 * 审批卡片的展示模型。
 */
data class ApprovalCardModel(
    val title: String,
    val toolName: String,
    val targetPath: String?,
    val payloadPreview: String?,
    val operationIntent: String?,
    val rawCommand: String?,
)

/**
 * 将挂起问题状态映射为界面模型。
 */
internal fun buildQuestionCardModel(pending: PendingQuestionUiState): QuestionCardModel = QuestionCardModel(
    questions = pending.effectiveQuestions,
    allowFreeText = pending.allowFreeText,
)

/**
 * 将挂起审批状态映射为界面模型。
 */
internal fun buildApprovalCardModel(pending: PendingApprovalUiState): ApprovalCardModel = ApprovalCardModel(
    title = pending.summary,
    toolName = pending.toolName,
    targetPath = pending.targetPath,
    payloadPreview = pending.payloadPreview,
    operationIntent = pending.summary.takeIf { pending.toolName == "run_powershell" },
    rawCommand = pending.payloadPreview.takeIf { pending.toolName == "run_powershell" },
)

/** 判断自由回答去除空白后是否仍可提交。 */
internal fun canSubmitQuestionFreeText(value: String): Boolean = value.isNotBlank()

/** 返回已规整的自由回答；纯空白内容不会穿透到状态层。 */
internal fun questionFreeTextSubmission(value: String): String? =
    value.trim().takeIf(::canSubmitQuestionFreeText)

/** 问卷只有每题都给出非空回答时才允许一次提交。 */
internal fun canSubmitQuestionnaire(answers: List<String>): Boolean = answers.all(::canSubmitQuestionFreeText)

/**
 * transcript 内嵌的问题卡片。
 */
@Composable
fun QuestionCard(
    pending: PendingQuestionUiState,
    onSubmitAnswers: (List<QuestionAnswer>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = buildQuestionCardModel(pending)
    var answers by remember(pending.requestId) { mutableStateOf(List(model.questions.size) { "" }) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppSidebarBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Agent needs answers",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = AppMuted,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            model.questions.forEachIndexed { index, prompt ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${index + 1}. ${prompt.question}",
                        style = MaterialTheme.typography.bodyLarge.copy(color = AppText, lineHeight = 24.sp),
                    )
                    prompt.options.take(5).forEach { option ->
                        RingPrimaryButton(
                            text = option,
                            onClick = {
                                answers = answers.toMutableList().also { it[index] = option }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = if (answers[index] == option) AppAccent else AppChipBackground,
                        )
                    }
                    if (model.allowFreeText) {
                        BasicTextField(
                            value = answers[index].takeIf { answer -> answer !in prompt.options }.orEmpty(),
                            onValueChange = { value ->
                                answers = answers.toMutableList().also { it[index] = value }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AppLine.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                                .background(AppPanelBackground, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppText),
                            cursorBrush = SolidColor(AppText),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (answers[index].isBlank() || answers[index] in prompt.options) {
                                        Text("Other…", style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted))
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }
                }
            }
            RingPrimaryButton(
                text = "Submit answers",
                onClick = {
                    onSubmitAnswers(model.questions.mapIndexed { index, prompt ->
                        QuestionAnswer(question = prompt.question, answer = answers[index].trim())
                    })
                },
                enabled = canSubmitQuestionnaire(answers),
                containerColor = AppAccent,
            )
        }
    }
}

/**
 * transcript 内嵌的审批卡片。
 */
@Composable
fun ApprovalCard(
    pending: PendingApprovalUiState,
    onResponse: (ApprovalResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = buildApprovalCardModel(pending)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1D171A),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4C2630)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Agent 需要执行确认",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = AppDanger,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = model.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = AppText,
                    lineHeight = 24.sp,
                ),
            )
            model.targetPath?.takeIf { it.isNotBlank() }?.let { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
                )
            }
            (model.rawCommand ?: model.payloadPreview)?.takeIf { it.isNotBlank() }?.let { preview ->
                Text(
                    text = preview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppPanelBackground, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AppText,
                        lineHeight = 20.sp,
                    ),
                )
            }
            ApprovalResponseActions(onResponse)
        }
    }
}

/**
 * 呈现桌面风格的自由回答输入区，将输入和提交动作组织为同一个操作表面。
 */
@Composable
private fun QuestionFreeTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppLine.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
            .background(AppPanelBackground, RoundedCornerShape(14.dp)),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = AppText,
                lineHeight = 22.sp,
            ),
            cursorBrush = SolidColor(AppText),
            minLines = 3,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isBlank()) {
                        Text(
                            text = "补充你的回答…",
                            style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted),
                        )
                    }
                    innerTextField()
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RingPrimaryButton(
                text = "提交回答",
                onClick = { questionFreeTextSubmission(value)?.let(onSubmit) },
                enabled = canSubmitQuestionFreeText(value),
                containerColor = AppSuccess,
            )
        }
    }
}

/**
 * 直接嵌入工具调用卡片的审批动作区，不再生成独立审批浮层。
 */
@Composable
fun InlineToolApprovalActions(
    onResponse: (ApprovalResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "需要执行确认",
            style = MaterialTheme.typography.labelLarge.copy(
                color = AppDanger,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        ApprovalResponseActions(onResponse)
    }
}

/**
 * 渲染审批卡与内嵌审批区共享的三种执行决策动作。
 */
@Composable
private fun ApprovalResponseActions(onResponse: (ApprovalResponse) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RingPrimaryButton(
            text = "同意",
            onClick = { onResponse(ApprovalResponse.APPROVE_ONCE) },
            containerColor = AppAccent,
        )
        RingPrimaryButton(
            text = "此类命令都同意",
            onClick = { onResponse(ApprovalResponse.APPROVE_TOOL_TYPE) },
            containerColor = AppChipBackground,
        )
        RingPrimaryButton(
            text = "拒绝并停止",
            onClick = { onResponse(ApprovalResponse.REJECT_AND_STOP) },
            containerColor = AppChipBackground,
        )
    }
}
