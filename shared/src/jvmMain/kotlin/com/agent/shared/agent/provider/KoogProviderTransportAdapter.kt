package com.agent.shared.agent.provider

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSessionCommon
import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.provider.deepseek.DeepSeekKoogTransportAdapter
import com.agent.shared.settings.model.ConfigProfile
import kotlinx.coroutines.flow.Flow

/**
 * 将特定模型提供商的 wire-format 差异隔离在传输适配器中。
 *
 * Agent 图和 Koog 通用 HTTP 客户端只依赖此协议；适配器是否生效由启动本轮运行的
 * [ConfigProfile] 决定，避免某个 provider 的兼容补丁污染其他模型。
 */
internal interface KoogProviderTransportAdapter {
    /** 返回该适配器是否负责指定 profile 的传输协议。 */
    fun supports(profile: ConfigProfile): Boolean

    /**
     * 返回自定义流式帧；返回 null 时继续使用 Koog 的默认流式实现。
     */
    suspend fun streamFrames(
        session: AIAgentLLMWriteSessionCommon,
        request: AgentRunRequest,
    ): Flow<StreamFrame>?

    /** 在 Koog 解码 SSE JSON 前修正 provider 特有的合法 wire-format。 */
    fun normalizeSseData(data: String): String = data

    /** 在 Koog 发出请求前修正 provider 特有的回放 wire-format。 */
    fun normalizeRequestBody(data: String): String = data
}

/**
 * 内建 provider 适配器的确定性注册表。
 *
 * 列表顺序即冲突优先级；后续扩展运行时会把受控包提供的适配器追加到这一边界，而不让
 * provider 判断散落进 Koog 图节点。
 */
internal object ProviderKoogTransportAdapters {
    private val adapters: List<KoogProviderTransportAdapter> = listOf(
        DeepSeekKoogTransportAdapter,
    )

    /** 返回第一个匹配 profile 的适配器，保持资源运行时的首项生效规则。 */
    fun forProfile(profile: ConfigProfile): KoogProviderTransportAdapter? =
        adapters.firstOrNull { adapter -> adapter.supports(profile) }

    /** 请求 provider 自定义流；没有适配器或适配器未覆盖时由调用方走默认流。 */
    suspend fun streamFramesOrNull(
        session: AIAgentLLMWriteSessionCommon,
        request: AgentRunRequest,
    ): Flow<StreamFrame>? = forProfile(request.profile)?.streamFrames(session, request)
}
