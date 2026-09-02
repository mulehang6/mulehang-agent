package com.agent.shared.settings.resolver

import com.agent.shared.settings.model.ConfigProfile

/**
 * 判断当前 profile 是否可接收图片输入。
 *
 * settings.json 的显式 `supportsVision` 总是优先；未声明时仅对白名单中的常见视觉模型返回
 * true，未知模型保守拒绝，避免把图片静默降级为文字或发往不支持的 provider。
 */
fun ConfigProfile.supportsImageInput(): Boolean = supportsVision ?: model.lowercase().let { modelId ->
    modelId.contains("vision") ||
        modelId.contains("gpt-4o") ||
        modelId.contains("gpt-4.1") ||
        modelId.contains("gpt-5") ||
        modelId.contains("codex") ||
        modelId.contains("claude-3") ||
        modelId.contains("claude-4") ||
        modelId.contains("gemini")
}
