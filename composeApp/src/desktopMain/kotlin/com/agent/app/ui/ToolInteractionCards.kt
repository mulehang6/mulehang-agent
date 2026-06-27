package com.agent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 问题卡片的展示模型。
 */
data class QuestionCardModel(
    val title: String,
    val options: List<String>,
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
)

/**
 * 将挂起问题状态映射为界面模型。
 */
internal fun buildQuestionCardModel(pending: PendingQuestionUiState): QuestionCardModel = QuestionCardModel(
    title = pending.question,
    options = pending.options,
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
)

/**
 * transcript 内嵌的问题卡片。
 */
@Composable
fun QuestionCard(
    pending: PendingQuestionUiState,
    onOptionClick: (String) -> Unit,
    onSubmitText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = buildQuestionCardModel(pending)
    var draft by remember(pending.requestId) { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF17191D),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34373D)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Agent 需要补充信息",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color(0xFFA3A7AE),
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = model.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color(0xFFF2F4F8),
                    lineHeight = 24.sp,
                ),
            )
            if (model.options.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    model.options.forEach { option ->
                        Button(
                            onClick = { onOptionClick(option) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1F7DE8),
                                contentColor = Color(0xFFF8FAFC),
                            ),
                        ) {
                            Text(option)
                        }
                    }
                }
            }
            if (model.allowFreeText) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("自定义回答") },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                )
                Button(
                    onClick = { onSubmitText(draft.trim()) },
                    enabled = draft.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1FA982),
                        contentColor = Color(0xFFF8FAFC),
                    ),
                ) {
                    Text("提交")
                }
            }
        }
    }
}

/**
 * transcript 内嵌的审批卡片。
 */
@Composable
fun ApprovalCard(
    pending: PendingApprovalUiState,
    onApprove: () -> Unit,
    onReject: () -> Unit,
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
                    color = Color(0xFFE6476B),
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = model.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color(0xFFF2F4F8),
                    lineHeight = 24.sp,
                ),
            )
            model.targetPath?.takeIf { it.isNotBlank() }?.let { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFA3A7AE)),
                )
            }
            model.payloadPreview?.takeIf { it.isNotBlank() }?.let { preview ->
                Text(
                    text = preview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF262A30), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFD3D7DE),
                        lineHeight = 20.sp,
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F7DE8),
                        contentColor = Color(0xFFF8FAFC),
                    ),
                ) {
                    Text("允许")
                }
                Button(
                    onClick = onReject,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2F3339),
                        contentColor = Color(0xFFA3A7AE),
                    ),
                ) {
                    Text("拒绝")
                }
            }
        }
    }
}
