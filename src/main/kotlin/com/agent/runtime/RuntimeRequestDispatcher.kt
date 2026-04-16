package com.agent.runtime

/**
 * 负责把上层入口请求转发到统一 runtime 路由器的最小 dispatcher。
 */
class RuntimeRequestDispatcher(
    private val capabilityRouter: RuntimeCapabilityRouter,
) {

    /**
     * 使用统一的上下文和请求契约执行一次 runtime 分发。
     */
    suspend fun dispatch(
        session: RuntimeSession,
        context: RuntimeRequestContext,
        request: CapabilityRequest,
    ): RuntimeResult {
        if (session.id != context.sessionId) {
            return RuntimeFailed(
                failure = RuntimeError(
                    message = "Runtime session '${session.id}' does not match context session '${context.sessionId}'.",
                ),
            )
        }

        return capabilityRouter.route(context = context, request = request)
    }
}
