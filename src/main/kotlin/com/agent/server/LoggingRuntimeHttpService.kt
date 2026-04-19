package com.agent.server

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
    override suspend fun run(request: RuntimeRunHttpRequest): RuntimeRunHttpResponse {
        logger.info(
            "AOP进入 runtime 接口：providerId={} providerType={} baseUrl={} modelId={} promptLength={}",
            request.provider.providerId,
            request.provider.providerType,
            request.provider.baseUrl,
            request.provider.modelId,
            request.prompt.length,
        )

        return try {
            val response = delegate.run(request)
            logResponse(response)
            response
        } catch (error: Throwable) {
            logger.error(
                "AOP捕获 runtime 接口异常：providerId={} providerType={} modelId={} error={}",
                request.provider.providerId,
                request.provider.providerType,
                request.provider.modelId,
                error.message,
                error,
            )
            throw error
        }
    }

    /**
     * 按成功或失败结果记录统一出口日志。
     */
    private fun logResponse(response: RuntimeRunHttpResponse) {
        if (response.success) {
            logger.info(
                "AOP退出 runtime 接口：success=true sessionId={} requestId={} eventCount={}",
                response.sessionId,
                response.requestId,
                response.events.size,
            )
            return
        }

        logger.warn(
            "AOP退出 runtime 接口：success=false sessionId={} requestId={} failureKind={} message={}",
            response.sessionId,
            response.requestId,
            response.failure?.kind,
            response.failure?.message,
        )
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(LoggingRuntimeHttpService::class.java)
    }
}
