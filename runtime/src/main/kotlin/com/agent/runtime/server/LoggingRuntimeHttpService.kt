package com.agent.runtime.server

import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory

/**
 * 以装饰器方式为 RuntimeHttpService 增加中文日志，不改变被包装服务的执行语义。
 */
class LoggingRuntimeHttpService(
    private val delegate: RuntimeHttpService,
) : RuntimeHttpService {

    /**
     * 记录 runtime 请求进入、响应完成和异常；不会输出 apiKey 或 prompt 原文。
     */
    override suspend fun run(request: RuntimeRunHttpRequest): Result<RuntimeRunPayload> {
        val provider = request.provider
        logger.info(
            "AOP进入 runtime 接口：providerId={} providerType={} baseUrl={} modelId={} promptLength={}",
            provider?.providerId ?: "runtime-default",
            provider?.providerType ?: "runtime-default",
            provider?.baseUrl ?: "runtime-default",
            provider?.modelId ?: "runtime-default",
            request.prompt.length,
        )

        return try {
            val response = delegate.run(request)
            logResponse(response)
            response
        } catch (error: Throwable) {
            logger.error(
                "AOP捕获 runtime 接口异常：providerId={} providerType={} modelId={} error={}",
                provider?.providerId ?: "runtime-default",
                provider?.providerType ?: "runtime-default",
                provider?.modelId ?: "runtime-default",
                error.message,
                error,
            )
            throw error
        }
    }

    /**
     * 记录 runtime SSE 请求进入和结束，但不改变被包装服务的事件语义。
     */
    override fun stream(request: RuntimeRunHttpRequest): Flow<RuntimeSseEvent> {
        val provider = request.provider
        logger.info(
            "AOP进入 runtime SSE 接口：providerId={} providerType={} baseUrl={} modelId={} promptLength={}",
            provider?.providerId ?: "runtime-default",
            provider?.providerType ?: "runtime-default",
            provider?.baseUrl ?: "runtime-default",
            provider?.modelId ?: "runtime-default",
            request.prompt.length,
        )
        return delegate.stream(request)
    }

    /**
     * 按成功或失败结果记录统一出口日志。
     */
    private fun logResponse(response: Result<RuntimeRunPayload>) {
        if (response.code == 1) {
            logger.info(
                "AOP退出 runtime 接口：success=true sessionId={} requestId={} eventCount={}",
                response.data.sessionId,
                response.data.requestId,
                response.data.events.size,
            )
            return
        }

        logger.warn(
            "AOP退出 runtime 接口：success=false sessionId={} requestId={} failureKind={} message={}",
            response.data.sessionId,
            response.data.requestId,
            response.data.failureEvent()?.failureKind,
            response.message,
        )
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(LoggingRuntimeHttpService::class.java)
    }
}
