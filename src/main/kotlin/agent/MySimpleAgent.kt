package agent

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import utils.loadEnv
import java.io.File
import kotlin.sequences.generateSequence
import kotlin.uuid.Uuid

private const val CHAT_HISTORY_DIR = "my_chat_history"
private const val TOOL_MODE_NOTICE = "[系统] 检测到文件操作请求，已切换到非流式工具模式。"
private const val STREAMING_FALLBACK_NOTICE = "[系统] DeepSeek 流式请求失败（400），已自动切换到非流式模式重试。"
private const val SYSTEM_PROMPT = "你是一个友好的 AI 助手。对于普通聊天和常规问答，请直接给出自然、简洁的回答。"
private val TOOL_REQUEST_KEYWORDS = listOf(
    "文件", "目录", "路径", "列出", "读取", "读", "查看", "打开", "写入", "写", "保存", "创建", "删除", "修改"
)

class MyChatHistory(private val storageDir: String): ChatHistoryProvider {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        File(storageDir).mkdirs()
    }

    private fun getFile(conversationId: String) = File(storageDir, "$conversationId.json")

    override suspend fun load(conversationId: String): List<Message> {
        val file = getFile(conversationId)
        if (!file.exists()) return emptyList()

        return try {
            val content = file.readText()
            json.decodeFromString<List<Message>>(content)
        } catch (e: Exception) {
            println("[系统] 读取聊天记录失败：${e.message}")
            emptyList()
        }
    }

    override suspend fun store(conversationId: String, messages: List<Message>) {
        val file = getFile(conversationId)
        try {
            val content = json.encodeToString(messages)
            file.writeText(content)
        } catch (e: Exception) {
            println("[系统] 存储聊天记录失败：${e.message}")
        }
    }
}

private fun streamingSingleRunStrategy() = strategy<String, String>("streaming_single_run") {
    val nodeStreaming by nodeLLMRequestStreamingAndSendResults<String>()

    edge(nodeStart forwardTo nodeStreaming)
    edge(
        nodeStreaming forwardTo nodeFinish
            onMultipleAssistantMessages { true }
            transformed { messages -> messages.joinToString("\n") { it.content } }
    )
}

private fun createStreamingChatAgent(
    promptExecutor: MultiLLMPromptExecutor,
    streamedOutput: StringBuilder
) = AIAgent(
    promptExecutor = promptExecutor,
    strategy = streamingSingleRunStrategy(),
    llmModel = DeepSeekModels.DeepSeekChat,
    temperature = 0.7,
    systemPrompt = SYSTEM_PROMPT
) {
    install(ChatMemory) {
        chatHistoryProvider = MyChatHistory(CHAT_HISTORY_DIR)
        windowSize(20)
    }

    handleEvents {
        onLLMCallStarting { eventContext ->
            println("[事件] 发送请求到 LLM: ${eventContext.prompt.messages.last().content.take(30)}...")
        }
        onLLMCallCompleted { eventContext ->
            println("[事件] LLM 响应成功，模型: ${eventContext.model}")
        }
        onAgentCompleted { eventContext ->
            println("[事件] ${eventContext.context.llm.model} 智能体运行完成！")
        }

        onLLMStreamingFrameReceived { eventContext ->
            val frame = eventContext.streamFrame
            if (frame is StreamFrame.TextDelta) {
                val text = frame.text
                print(text)
                System.out.flush()
                streamedOutput.append(text)
            }
        }
    }
}

private fun createToolAgent(
    promptExecutor: MultiLLMPromptExecutor,
    toolRegistry: ToolRegistry
) = AIAgent(
    promptExecutor = promptExecutor,
    toolRegistry = toolRegistry,
    llmModel = DeepSeekModels.DeepSeekChat,
    temperature = 0.7,
    systemPrompt = SYSTEM_PROMPT
) {
    install(ChatMemory) {
        chatHistoryProvider = MyChatHistory(CHAT_HISTORY_DIR)
        windowSize(20)
    }

    handleEvents {
        onLLMCallStarting { eventContext ->
            println("[事件] 发送请求到 LLM: ${eventContext.prompt.messages.last().content.take(30)}...")
        }
        onLLMCallCompleted { eventContext ->
            println("[事件] LLM 响应成功，模型: ${eventContext.model}")
        }
        onToolCallStarting { eventContext ->
            println("[事件] 正在调用工具: ${eventContext.toolName}，参数: ${eventContext.toolArgs}")
        }
        onToolCallCompleted { eventContext ->
            println("[事件] 工具调用结束，结果: ${eventContext.toolResult}")
        }
        onAgentCompleted { eventContext ->
            println("[事件] ${eventContext.context.llm.model} 智能体运行完成！")
        }
    }
}

internal fun shouldUseToolAgent(input: String): Boolean {
    return TOOL_REQUEST_KEYWORDS.any(input::contains)
}

internal fun shouldFallbackToNonStreaming(error: Throwable): Boolean {
    return generateSequence(error) { it.cause }
        .mapNotNull(Throwable::message)
        .any { message ->
            message.contains("Expected status code 200 but was 400", ignoreCase = true) ||
                (message.contains("Status code: 400", ignoreCase = true) &&
                    message.contains("DeepSeek", ignoreCase = true))
        }
}

internal suspend fun runAgentWithFallback(
    input: String,
    sessionId: String,
    streamedOutput: StringBuilder,
    streamingRunner: suspend (String, String) -> String,
    fallbackRunner: suspend (String, String) -> String,
    onFallback: (String) -> Unit = ::println
): String {
    return try {
        streamingRunner(input, sessionId)
    } catch (error: Throwable) {
        if (!shouldFallbackToNonStreaming(error)) {
            throw error
        }

        streamedOutput.clear()
        onFallback(STREAMING_FALLBACK_NOTICE)
        fallbackRunner(input, sessionId)
    }
}

internal suspend fun runAgentByMode(
    input: String,
    sessionId: String,
    streamedOutput: StringBuilder,
    streamingRunner: suspend (String, String) -> String,
    toolRunner: suspend (String, String) -> String,
    onToolModeSelected: (String) -> Unit = ::println,
    onFallback: (String) -> Unit = ::println
): String {
    return if (shouldUseToolAgent(input)) {
        onToolModeSelected(TOOL_MODE_NOTICE)
        toolRunner(input, sessionId)
    } else {
        runAgentWithFallback(
            input = input,
            sessionId = sessionId,
            streamedOutput = streamedOutput,
            streamingRunner = streamingRunner,
            fallbackRunner = toolRunner,
            onFallback = onFallback
        )
    }
}

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
fun main() {
    runBlocking {
        val apiKey: String = loadEnv()
            .getProperty("DEEPSEEK_API_KEY")
            ?: error("没有在.env文件中找到DEEPSEEK_API_KEY")

        val deepseekClient = DeepSeekLLMClient(apiKey)
        val promptExecutor = MultiLLMPromptExecutor(deepseekClient)
        val streamedOutput = StringBuilder()
        val allBuiltInTools = ToolRegistryBuilder()
            .tool(SayToUser)
            .tool(AskUser)
            .tool(ExitTool)
            .tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            .tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))
            .tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
            .build()

        val sessionId = Uuid.random()
        val sessionIdValue = sessionId.toString()

        val streamingAgent = createStreamingChatAgent(promptExecutor, streamedOutput)
        val toolAgent = createToolAgent(promptExecutor, allBuiltInTools)


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

            // 每轮开始时清除流式缓存
            streamedOutput.clear()

            val response = runAgentByMode(
                input = userInput,
                sessionId = sessionIdValue,
                streamedOutput = streamedOutput,
                streamingRunner = { input, sessionId ->
                    streamingAgent.run(input, sessionId = sessionId)
                },
                toolRunner = { input, sessionId ->
                    toolAgent.run(input, sessionId = sessionId)
                }
            )
            // 兜底逻辑：如果流式输出没有任何内容被打印出来，则直接打印完整响应
            if (streamedOutput.isEmpty() && response.isNotBlank()) {
                println(response)
                System.out.flush()
            }
        }
    }
}
