package com.agent.shared.config

import kotlinx.serialization.Serializable

/**
 * 模型 token 窗口限制，按 Kilo 风格区分总上下文、输入和输出上限。
 */
@Serializable
data class ModelLimit(
    val context: Int? = null,
    val input: Int? = null,
    val output: Int? = null,
)
