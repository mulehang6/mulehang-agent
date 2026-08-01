package com.agent.app.chat.component

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import com.agent.app.design.AppMarkdownLink

/**
 * 返回适合暗色聊天正文的 Markdown 链接样式，避免库默认的高饱和纯蓝。
 */
internal fun assistantMarkdownLinkStyle(): SpanStyle = SpanStyle(
    color = AppMarkdownLink,
    textDecoration = TextDecoration.Underline,
)
