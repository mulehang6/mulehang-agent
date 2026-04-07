package agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import kotlin.uuid.Uuid

/**
 * 构建当前 CLI 会话可用的内置工具集合。
 */
private fun createBuiltInToolRegistry(): ToolRegistry {
    return ToolRegistryBuilder()
        .tool(SayToUser)
        .tool(AskUser)
        .tool(ExitTool)
        .tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
        .tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))
        .tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
        .build()
}

/**
 * 运行交互式命令行智能体，并按输入路由到对应处理模式。
 */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
internal fun runAgentCli() {
    runBlocking {
        val defaultBinding = DEFAULT_BINDING
            ?: error("当前默认 provider 未配置 apiKey: ${APP_CONFIG.defaultProvider}")
        val apiKey = defaultBinding.apiKey.ifBlank {
            error("当前默认 provider 未配置 apiKey: ${defaultBinding.providerId}")
        }

        val chatHistoryProvider = FileChatHistoryProvider(CHAT_HISTORY_DIR)
        val sessionId = Uuid.random().toString()
        val toolRegistry = createBuiltInToolRegistry()

        while (true) {
            print("> ")
            val userInput = readlnOrNull() ?: break

            if (userInput.lowercase() in listOf("exit", "quit", "bye", "q", "out")) {
                println("再见")
                break
            }

            if (userInput.isBlank()) {
                continue
            }

            val result = runAgentCliTurn(
                execute = {
                    runAgentWithFallback(
                        input = userInput,
                        sessionId = sessionId,
                        streamingRunner = { input, currentSessionId ->
                            runStreamingAgent(
                                apiKey = apiKey,
                                chatHistoryProvider = chatHistoryProvider,
                                toolRegistry = toolRegistry,
                                input = input,
                                sessionId = currentSessionId
                            )
                        },
                        fallbackRunner = { input, currentSessionId ->
                            runNonStreamingChat(
                                apiKey = apiKey,
                                chatHistoryProvider = chatHistoryProvider,
                                toolRegistry = toolRegistry,
                                input = input,
                                sessionId = currentSessionId
                            )
                        }
                    )
                }
            ) ?: continue

            if (result.shouldPrintResponse && result.response.isNotBlank()) {
                println(result.response)
            }
        }
    }
}
