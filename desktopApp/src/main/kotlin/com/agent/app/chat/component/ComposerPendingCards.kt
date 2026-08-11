package com.agent.app.chat.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.tool.component.ApprovalCard
import com.agent.app.tool.component.QuestionCard

internal const val PENDING_CARD_ENTER_DURATION_MILLIS = 180
internal const val PENDING_CARD_EXIT_DURATION_MILLIS = 120
internal const val PENDING_CARD_ENTER_INITIAL_SCALE = 0.96f

/**
 * 在 composer 上方叠加展示挂起的问题或审批卡片。
 */
@Composable
internal fun PendingInteractionCards(
    conversation: ChatConversationUiState,
    state: ChatWindowState,
) {
    val pendingQuestion = conversation.pendingQuestion
    val pendingApproval = conversation.pendingApproval
    var isReadyForEntryAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isReadyForEntryAnimation = true
    }

    AnimatedVisibility(
        visible = pendingInteractionCardVisibility(
            isReadyForEntryAnimation = isReadyForEntryAnimation,
            hasPendingQuestion = pendingQuestion != null,
            hasPendingApproval = pendingApproval != null,
        ),
        enter = fadeIn(tween(PENDING_CARD_ENTER_DURATION_MILLIS)) +
                slideInVertically(tween(PENDING_CARD_ENTER_DURATION_MILLIS)) { height -> height / 8 } +
                scaleIn(
                    initialScale = PENDING_CARD_ENTER_INITIAL_SCALE,
                    animationSpec = tween(PENDING_CARD_ENTER_DURATION_MILLIS),
                ),
        exit = fadeOut(tween(PENDING_CARD_EXIT_DURATION_MILLIS)) +
                slideOutVertically(tween(PENDING_CARD_EXIT_DURATION_MILLIS)) { height -> -height / 12 },
    ) {
        when {
            pendingQuestion != null -> QuestionCard(
                pending = pendingQuestion,
                onSubmitAnswers = state::answerPendingQuestions,
            )

            pendingApproval != null -> ApprovalCard(
                pending = pendingApproval,
                onResponse = state::answerPendingApproval,
            )
        }
    }
}

/**
 * 初次组合的卡片先保持隐藏，让 AnimatedVisibility 获得明确的入场状态变化。
 */
internal fun pendingInteractionCardVisibility(
    isReadyForEntryAnimation: Boolean,
    hasPendingQuestion: Boolean,
    hasPendingApproval: Boolean,
): Boolean = isReadyForEntryAnimation && (hasPendingQuestion || hasPendingApproval)
