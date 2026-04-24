package com.agent.runtime.core

/**
 * 负责把统一的 runtime 请求路由到具体能力实现的抽象边界。
 */
interface RuntimeCapabilityRouter {

    /**
     * 根据上下文和能力请求返回统一的 runtime 结果。
     */
    suspend fun route(
        context: RuntimeRequestContext,
        request: CapabilityRequest,
    ): RuntimeResult
}
