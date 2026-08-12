@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.tool.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.chat.state.PendingApprovalUiState
import com.agent.app.chat.state.PendingQuestionUiState
import com.agent.app.design.*
import com.agent.app.tool.interaction.ApprovalResponse
import com.agent.shared.tool.model.QuestionAnswer
import com.agent.shared.tool.model.QuestionPrompt
import com.agent.shared.tool.model.FileDiffPreview

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
    val diffs: List<FileDiffPreview>,
)

/** 单题回答当前所处的选择模式，空白、预设和自定义输入必须明确区分。 */
internal enum class QuestionAnswerMode {
    NONE,
    PRESET,
    CUSTOM,
}

/** 保留预设选择与自定义草稿，切换模式时不丢失用户已经输入的内容。 */
internal data class QuestionAnswerDraft(
    val mode: QuestionAnswerMode = QuestionAnswerMode.NONE,
    val presetValue: String = "",
    val customValue: String = "",
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
    diffs = pending.diffs,
)

/** apply_patch 已由编辑器 Diff 覆盖载荷内容，其他工具继续展示可审批的命令或摘要。 */
internal fun visibleApprovalPayload(model: ApprovalCardModel): String? =
    if (model.toolName == "apply_patch") null else model.rawCommand ?: model.payloadPreview

/** 判断自由回答去除空白后是否仍可提交。 */
internal fun canSubmitQuestionFreeText(value: String): Boolean = value.isNotBlank()

/** 返回已规整的自由回答；纯空白内容不会穿透到状态层。 */
internal fun questionFreeTextSubmission(value: String): String? =
    value.trim().takeIf(::canSubmitQuestionFreeText)

/** 返回当前模式真正会提交给 Agent 的回答内容。 */
internal fun questionAnswerValue(draft: QuestionAnswerDraft): String = when (draft.mode) {
    QuestionAnswerMode.PRESET -> draft.presetValue
    QuestionAnswerMode.CUSTOM -> draft.customValue
    QuestionAnswerMode.NONE -> ""
}

/** 选中预设答案，同时保留可能存在的自定义草稿。 */
internal fun selectQuestionPresetAnswer(draft: QuestionAnswerDraft, option: String): QuestionAnswerDraft =
    draft.copy(mode = QuestionAnswerMode.PRESET, presetValue = option)

/** 立即选中“自己输入”，不要求用户先键入内容才形成选中状态。 */
internal fun selectQuestionCustomAnswer(draft: QuestionAnswerDraft): QuestionAnswerDraft =
    draft.copy(mode = QuestionAnswerMode.CUSTOM)

/** 更新自定义草稿时保持其选择状态。 */
internal fun updateQuestionCustomAnswer(draft: QuestionAnswerDraft, value: String): QuestionAnswerDraft =
    draft.copy(mode = QuestionAnswerMode.CUSTOM, customValue = value)

/** 问卷只有每题都给出非空回答时才允许一次提交。 */
internal fun canSubmitQuestionnaire(answers: List<String>): Boolean = answers.all(::canSubmitQuestionFreeText)

/** 返回当前问卷标签页的主操作文案。 */
internal fun questionnaireActionLabel(activeIndex: Int, questionCount: Int): String =
    if (activeIndex >= questionCount - 1) "Submit answers" else "Next"

/** 将问卷推进到下一题，但不允许越过最后一个标签页。 */
internal fun nextQuestionnaireTabIndex(activeIndex: Int, questionCount: Int): Int =
    (activeIndex + 1).coerceAtMost((questionCount - 1).coerceAtLeast(0))

/** 从问题文本生成紧凑的标签标题，避免题目过长撑开整张卡片。 */
private fun questionnaireTabLabel(question: String): String = question.trim().take(12).ifBlank { "Question" }

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
    var answers by remember(pending.requestId) { mutableStateOf(List(model.questions.size) { QuestionAnswerDraft() }) }
    var activeQuestionIndex by remember(pending.requestId) { mutableStateOf(0) }

    if (model.questions.isEmpty()) return

    val activePrompt = model.questions[activeQuestionIndex]
    val activeAnswer = answers[activeQuestionIndex]

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppSidebarBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppAccent.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                model.questions.forEachIndexed { index, prompt ->
                    RingPrimaryButton(
                        text = questionnaireTabLabel(prompt.question),
                        onClick = { activeQuestionIndex = index },
                        containerColor = if (index == activeQuestionIndex) AppAccent else AppChipBackground,
                    )
                }
            }
            Text(
                text = "${activeQuestionIndex + 1}. ${activePrompt.question}",
                style = MaterialTheme.typography.bodyLarge.copy(color = AppText, lineHeight = 24.sp),
            )
            activePrompt.options.take(5).forEachIndexed { optionIndex, option ->
                QuestionOptionRow(
                    number = optionIndex + 1,
                    text = option,
                    selected = activeAnswer.mode == QuestionAnswerMode.PRESET && activeAnswer.presetValue == option,
                    onClick = {
                        answers = answers.toMutableList().also { drafts ->
                            drafts[activeQuestionIndex] = selectQuestionPresetAnswer(activeAnswer, option)
                        }
                    },
                )
            }
            if (model.allowFreeText) {
                QuestionOptionRow(
                    number = activePrompt.options.take(5).size + 1,
                    text = "自己输入",
                    selected = activeAnswer.mode == QuestionAnswerMode.CUSTOM,
                    onClick = {
                        answers = answers.toMutableList().also { drafts ->
                            drafts[activeQuestionIndex] = selectQuestionCustomAnswer(activeAnswer)
                        }
                    },
                )
                AnimatedVisibility(visible = activeAnswer.mode == QuestionAnswerMode.CUSTOM) {
                    BasicTextField(
                        value = activeAnswer.customValue,
                        onValueChange = { value ->
                            answers = answers.toMutableList().also { drafts ->
                                drafts[activeQuestionIndex] = updateQuestionCustomAnswer(activeAnswer, value)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 10.dp, top = 2.dp, bottom = 6.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppText),
                        cursorBrush = SolidColor(AppText),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                if (activeAnswer.customValue.isBlank()) {
                                    Text(
                                        "Type something else…",
                                        style = MaterialTheme.typography.bodyLarge.copy(color = AppMuted)
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                val isLastQuestion = activeQuestionIndex == model.questions.lastIndex
                RingPrimaryButton(
                    text = questionnaireActionLabel(activeQuestionIndex, model.questions.size),
                    onClick = {
                        if (isLastQuestion) {
                            onSubmitAnswers(model.questions.mapIndexed { index, prompt ->
                                QuestionAnswer(question = prompt.question, answer = questionAnswerValue(answers[index]).trim())
                            })
                        } else {
                            activeQuestionIndex = nextQuestionnaireTabIndex(activeQuestionIndex, model.questions.size)
                        }
                    },
                    enabled = if (isLastQuestion) {
                        canSubmitQuestionnaire(answers.map(::questionAnswerValue))
                    } else {
                        canSubmitQuestionFreeText(questionAnswerValue(activeAnswer))
                    },
                    containerColor = AppAccent,
                )
            }
        }
    }
}

/** 渲染桌面式单行候选项，使用选择指示与文字层级代替 Material 按钮反馈。 */
@Composable
private fun QuestionOptionRow(
    number: Int,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = when {
                    selected -> AppAccent.copy(alpha = 0.12f)
                    hovered -> AppLine.copy(alpha = 0.38f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(8.dp),
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .border(1.dp, if (selected) AppAccent else AppLine, CircleShape)
                .background(if (selected) AppAccent else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(AppSidebarBackground, CircleShape),
                )
            }
        }
        Text(
            text = "$number. $text",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = when {
                    selected -> AppText
                    hovered -> AppText
                    else -> AppMuted
                },
            ),
        )
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
            model.diffs.forEach { diff -> EditorDiffPreview(diff) }
            visibleApprovalPayload(model)
                ?.takeIf { it.isNotBlank() }
                ?.let { preview ->
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
