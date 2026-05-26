package com.agent.shared.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 支持的 provider 协议兼容类型。
 */
@Serializable
enum class ProviderType {
    @SerialName("openai-responses")
    OPENAI_RESPONSES,

    @SerialName("openai-chat-completions")
    OPENAI_CHAT_COMPLETIONS,

    @SerialName("anthropic")
    ANTHROPIC,

    @SerialName("google")
    GOOGLE,
}
