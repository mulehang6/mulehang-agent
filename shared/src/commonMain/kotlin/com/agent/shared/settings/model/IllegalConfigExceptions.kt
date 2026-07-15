package com.agent.shared.settings.model

/**
 * 表示配置内容无法用于当前执行链路。
 */
class IllegalConfigExceptions(
    messageProvider: () -> String,
) : RuntimeException(messageProvider())
