package com.agent.app.chat.state

import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ConversationItem
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.resolver.ModelCapabilitiesResolver

/**
 * 使用指定上下文窗口重算会话占比。
 */
internal fun ChatConversationUiState.withRecalculatedContextUsage(contextWindow: Int?): ChatConversationUiState =
    copy(
        contextUsageFraction = estimateContextUsage(
            items = items,
            attachmentCount = attachments.size,
            contextWindow = contextWindow,
        ),
    )

/**
 * 解析 profile 的上下文窗口；profile 显式配置优先，其次使用模型能力默认值。
 */
internal fun resolveContextWindow(profile: ConfigProfile): Int? =
    profile.limit?.context ?: ModelCapabilitiesResolver.resolve(profile).limit?.context

/**
 * 依据已有消息和附件粗略估计上下文占用比例；没有 context 窗口时回退为 0。
 */
internal fun estimateContextUsage(
    items: List<ConversationItem>,
    attachmentCount: Int,
    contextWindow: Int?,
): Float {
    val window = contextWindow?.takeIf { it > 0 } ?: return 0f
    val estimatedTokens = items.sumOf(::estimateTokens) + attachmentCount * ATTACHMENT_TOKEN_ESTIMATE
    return (estimatedTokens.toFloat() / window).coerceIn(0f, 1f)
}

/**
 * 粗略估算单个时间线项的 token 数。
 */
internal fun estimateTokens(item: ConversationItem): Int = when (item) {
    is ChatMessageItem -> estimateTextTokens(item.message.content)
    is ReasoningItem -> estimateTextTokens(item.rawText ?: item.displayText)
    is ToolEventItem -> estimateTextTokens(item.preview.orEmpty()) + estimateTextTokens(item.toolName)
}

/**
 * 使用常见的 4 字符约 1 token 经验值估算文本 token 数。
 */
private fun estimateTextTokens(text: String): Int =
    (text.length + CHARS_PER_TOKEN_ESTIMATE - 1) / CHARS_PER_TOKEN_ESTIMATE

private const val CHARS_PER_TOKEN_ESTIMATE = 4

private const val ATTACHMENT_TOKEN_ESTIMATE = 64
