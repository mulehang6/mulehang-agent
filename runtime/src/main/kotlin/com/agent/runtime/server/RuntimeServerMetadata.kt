package com.agent.runtime.server

import kotlinx.serialization.Serializable

/**
 * 表示共享本地 runtime server 对外公开的最小元信息。
 */
@Serializable
data class RuntimeServerMetadata(
    val service: String,
    val protocolVersion: String,
    val serverVersion: String,
    val authMode: String,
)
