package agent

import kotlin.sequences.generateSequence

private val fallbackStatusCodes = listOf(400, 429)
private val openRouterStatusCodePatterns = listOf(
    Regex("""Status code:\s*(\d+)""", RegexOption.IGNORE_CASE),
    Regex("""Expected status code 200 but was\s+(\d+)""", RegexOption.IGNORE_CASE)
)

/**
 * 判断当前异常是否适合从流式请求回退到非流式请求。
 */
internal fun shouldFallbackToNonStreaming(error: Throwable): Boolean {
    val message = generateSequence(error) { it.cause }
        .mapNotNull(Throwable::message)
        .joinToString("\n")

    if (fallbackStatusCodes.any { status -> message.contains(status.toString()) }) {
        println("[调试] OpenRouter 流式请求可回退错误详情:\n$message")
    }

    return fallbackStatusCodes.any { status ->
        message.contains("Expected status code 200 but was $status", ignoreCase = true) ||
            message.contains("Status code: $status", ignoreCase = true)
    }
}

/**
 * 从 OpenRouter 相关异常文本中提取状态码，供错误分级与提示复用。
 */
internal fun extractOpenRouterStatusCode(error: Throwable): Int? {
    val message = generateSequence(error) { it.cause }
        .mapNotNull(Throwable::message)
        .joinToString("\n")

    return openRouterStatusCodePatterns.asSequence()
        .mapNotNull { pattern -> pattern.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        .firstOrNull()
}

/**
 * 将可恢复的 OpenRouter 错误映射为终端可展示的提示语。
 */
internal fun recoverableAgentFailureNotice(error: Throwable): String? {
    return when (extractOpenRouterStatusCode(error)) {
        429 -> "[系统] OpenRouter 当前被上游限流（HTTP 429），本次请求未完成，请稍后重试。"
        400 -> "[系统] OpenRouter 当前请求被拒绝（HTTP 400），本次请求未完成。"
        else -> null
    }
}

/**
 * 统一封装单轮 CLI 调用的异常处理，只吞掉已知可恢复错误。
 */
internal suspend fun runAgentCliTurn(
    execute: suspend () -> AgentRunResult,
    onError: (String) -> Unit = ::println
): AgentRunResult? {
    return try {
        execute()
    } catch (error: Throwable) {
        val notice = recoverableAgentFailureNotice(error) ?: throw error
        onError(notice)
        null
    }
}

/**
 * 描述一次智能体执行的文本结果以及是否需要在终端补打输出。
 */
internal data class AgentRunResult(
    val response: String,
    val shouldPrintResponse: Boolean
)

/**
 * 优先执行流式聊天，命中可回退错误时自动改走兜底执行器。
 */
internal suspend fun runAgentWithFallback(
    input: String,
    sessionId: String,
    streamingRunner: suspend (String, String) -> String,
    fallbackRunner: suspend (String, String) -> String,
    onFallback: (String) -> Unit = ::println
): AgentRunResult {
    return try {
        AgentRunResult(
            response = streamingRunner(input, sessionId),
            shouldPrintResponse = false
        )
    } catch (error: Throwable) {
        if (!shouldFallbackToNonStreaming(error)) {
            throw error
        }

        onFallback(STREAMING_FALLBACK_NOTICE)
        AgentRunResult(
            response = fallbackRunner(input, sessionId),
            shouldPrintResponse = true
        )
    }
}

/**
 * 按输入类型在工具模式、流式聊天和非流式兜底之间选择执行路径。
 */
internal suspend fun runAgentByMode(
    input: String,
    sessionId: String,
    streamingRunner: suspend (String, String) -> String,
    toolRunner: suspend (String, String) -> String,
    fallbackRunner: suspend (String, String) -> String = toolRunner,
    onToolModeSelected: (String) -> Unit = ::println,
    onFallback: (String) -> Unit = ::println
): AgentRunResult {
    return runAgentWithFallback(
        input = input,
        sessionId = sessionId,
        streamingRunner = streamingRunner,
        fallbackRunner = fallbackRunner,
        onFallback = onFallback
    )
}
