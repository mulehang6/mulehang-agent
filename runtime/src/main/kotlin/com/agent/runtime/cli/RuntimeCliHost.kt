package com.agent.runtime.cli

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * 提供 runtime 的本地 `stdio` 宿主入口，供独立 CLI 子进程拉起。
 */
fun main() = runBlocking {
    RuntimeCliHostSession(
        service = DefaultRuntimeCliService(),
    ).run()
}

/**
 * 负责在标准输入输出之间桥接 runtime CLI 协议消息。
 */
class RuntimeCliHostSession(
    private val service: DefaultRuntimeCliService,
) {

    /**
     * 持续消费标准输入中的 JSON 行，并把结果按行写回标准输出。
     */
    suspend fun run() {
        System.`in`.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { line ->
                processLine(line).forEach(::writeOutbound)
            }
        }
    }

    /**
     * 处理单条入站协议消息，并返回一组可立即写出的出站消息。
     */
    suspend fun processLine(line: String): List<RuntimeCliOutboundMessage> {
        return try {
            when (
                val inbound = RuntimeCliJson.decodeFromString(
                    RuntimeCliInboundMessage.serializer(),
                    line,
                )
            ) {
                is RuntimeCliRunRequest -> service.stream(inbound).toList()
            }
        } catch (error: Throwable) {
            listOf(
                RuntimeCliFailureMessage(
                    kind = "protocol",
                    message = error.message ?: "Invalid runtime cli message.",
                ),
            )
        }
    }

    /**
     * 把单条出站消息编码成一行 JSON 并立即刷新输出流。
     */
    private fun writeOutbound(message: RuntimeCliOutboundMessage) {
        println(
            RuntimeCliJson.encodeToString(
                RuntimeCliOutboundMessage.serializer(),
                message,
            ),
        )
        System.out.flush()
    }
}
