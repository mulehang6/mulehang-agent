package com.agent.runtime.cli

import kotlinx.coroutines.flow.Flow
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
    private val streamRequest: (RuntimeCliRunRequest) -> Flow<RuntimeCliOutboundMessage>,
    private val inputLines: () -> Sequence<String> = { System.`in`.bufferedReader().lineSequence() },
    private val writeMessage: (RuntimeCliOutboundMessage) -> Unit = ::writeOutbound,
) {

    constructor(service: DefaultRuntimeCliService) : this(
        streamRequest = { request -> service.stream(request) },
    )

    /**
     * 持续消费标准输入中的 JSON 行，并把结果按行写回标准输出。
     */
    suspend fun run() {
        inputLines().filter { it.isNotBlank() }.forEach { line ->
            processLineToOutput(line)
        }
    }

    /**
     * 处理单条入站协议消息，并在 flow 每次 emit 时立即写出。
     */
    private suspend fun processLineToOutput(line: String) {
        try {
            when (
                val inbound = RuntimeCliJson.decodeFromString(
                    RuntimeCliInboundMessage.serializer(),
                    line,
                )
            ) {
                is RuntimeCliRunRequest -> streamRequest(inbound).collect { message ->
                    writeMessage(message)
                }
            }
        } catch (error: Throwable) {
            writeMessage(
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
    private companion object {
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
}
