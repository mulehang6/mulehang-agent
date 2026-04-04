package agent

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
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
import ai.koog.prompt.dsl.prompt
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
private const val CHAT_HISTORY_WINDOW_SIZE = 20
private const val TOOL_MODE_NOTICE = "[系统] 检测到文件操作请求，已切换到非流式工具模式。"
private const val STREAMING_FALLBACK_NOTICE = "[系统] DeepSeek 流式请求失败（400），已自动切换到非流式模式重试。"
private const val SYSTEM_PROMPT = "你是一个友好的 AI 助手。对于普通聊天和常规问答，请直接给出自然、简洁的回答。"
private val TOOL_REQUEST_KEYWORDS = listOf(
    "文件", "目录", "路径", "列出", "读取", "读", "查看", "打开", "写入", "写", "保存", "创建", "删除", "修改"
)

/**
 * 过滤聊天消息，只保留可安全复用的用户与助手文本消息。
 */
internal fun retainChatMessages(messages: List<Message>): List<Message> {
    return messages.filter { message ->
        message is Message.User || message is Message.Assistant
    }
}

/**
 * 基于本地 JSON 文件提供会话历史的读取与存储能力。
 */
class MyChatHistory(private val storageDir: String): ChatHistoryProvider {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        File(storageDir).mkdirs()
    }

    /**
     * 根据会话 ID 生成对应的历史记录文件路径。
     */
    private fun getFile(conversationId: String) = File(storageDir, "$conversationId.json")

    /**
     * 加载指定会话的历史记录，并过滤掉不适合复用的中间消息。
     */
    override suspend fun load(conversationId: String): List<Message> {
        val file = getFile(conversationId)
        if (!file.exists()) return emptyList()

        return try {
            val content = file.readText()
            retainChatMessages(json.decodeFromString<List<Message>>(content))
        } catch (e: Exception) {
            println("[系统] 读取聊天记录失败：${e.message}")
            emptyList()
        }
    }

    /**
     * 将指定会话的聊天记录写入本地文件。
     */
    override suspend fun store(conversationId: String, messages: List<Message>) {
        val file = getFile(conversationId)
        try {
            val content = json.encodeToString(retainChatMessages(messages))
            file.writeText(content)
        } catch (e: Exception) {
            println("[系统] 存储聊天记录失败：${e.message}")
        }
    }
}

/**
 * 创建用于处理文件工具调用的非流式智能体。
 */
private fun createToolAgent(
    promptExecutor: MultiLLMPromptExecutor,
    toolRegistry: ToolRegistry,
    chatHistoryProvider: ChatHistoryProvider
) = AIAgent(
    promptExecutor = promptExecutor,
    toolRegistry = toolRegistry,
    llmModel = DeepSeekModels.DeepSeekChat,
    temperature = 0.7,
    systemPrompt = SYSTEM_PROMPT
) {
    install(ChatMemory) {
        this.chatHistoryProvider = chatHistoryProvider
        windowSize(CHAT_HISTORY_WINDOW_SIZE)
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

/**
 * 根据历史消息和本轮输入构建普通聊天提示词。
 */
private fun buildChatPrompt(history: List<Message>, input: String) = prompt("chat-session") {
    system(SYSTEM_PROMPT)

    history.forEach { message ->
        when (message) {
            is Message.User -> user(message.content)
            is Message.Assistant -> assistant(message.content)
            else -> Unit
        }
    }

    user(input)
}

/**
 * 构建单轮用户输入与助手回复组成的消息列表。
 */
private fun buildChatTurn(input: String, response: String): List<Message> {
    return prompt("chat-turn") {
        user(input)
        assistant(response)
    }.messages
}

/**
 * 持久化当前轮次的聊天内容，并按窗口大小裁剪历史记录。
 */
private suspend fun persistChatTurn(
    chatHistoryProvider: MyChatHistory,
    sessionId: String,
    history: List<Message>,
    input: String,
    response: String
) {
    val updatedHistory = (history + buildChatTurn(input, response))
        .takeLast(CHAT_HISTORY_WINDOW_SIZE)

    chatHistoryProvider.store(sessionId, updatedHistory)
}

/**
 * 以流式方式执行普通聊天，并在输出完成后写回聊天历史。
 */
internal suspend fun runStreamingChat(
    client: DeepSeekLLMClient,
    chatHistoryProvider: MyChatHistory,
    input: String,
    sessionId: String,
    streamedOutput: StringBuilder
): String {
    val history = chatHistoryProvider.load(sessionId).takeLast(CHAT_HISTORY_WINDOW_SIZE)
    val prompt = buildChatPrompt(history, input)
    val responseBuilder = StringBuilder()
    var hasTextDelta = false

    println("[事件] 发送请求到 LLM: ${input.take(30)}...")

    client.executeStreaming(prompt = prompt, model = DeepSeekModels.DeepSeekChat).collect { frame ->
        when (frame) {
            is StreamFrame.TextDelta -> {
                hasTextDelta = true
                print(frame.text)
                System.out.flush()
                streamedOutput.append(frame.text)
                responseBuilder.append(frame.text)
            }

            is StreamFrame.TextComplete -> {
                if (!hasTextDelta) {
                    print(frame.text)
                    System.out.flush()
                    streamedOutput.append(frame.text)
                    responseBuilder.append(frame.text)
                }
            }

            else -> Unit
        }
    }

    val response = responseBuilder.toString()
    println("[事件] LLM 响应成功，模型: ${DeepSeekModels.DeepSeekChat}")
    println("[事件] ${DeepSeekModels.DeepSeekChat} 智能体运行完成！")
    persistChatTurn(chatHistoryProvider, sessionId, history, input, response)
    return response
}

/**
 * 以非流式方式执行普通聊天，并在结束后写回聊天历史。
 */
internal suspend fun runNonStreamingChat(
    client: DeepSeekLLMClient,
    chatHistoryProvider: MyChatHistory,
    input: String,
    sessionId: String
): String {
    val history = chatHistoryProvider.load(sessionId).takeLast(CHAT_HISTORY_WINDOW_SIZE)
    val prompt = buildChatPrompt(history, input)

    println("[事件] 发送请求到 LLM: ${input.take(30)}...")
    val response = client.execute(prompt = prompt, model = DeepSeekModels.DeepSeekChat)
        .joinToString("\n") { it.content }
    println("[事件] LLM 响应成功，模型: ${DeepSeekModels.DeepSeekChat}")
    println("[事件] ${DeepSeekModels.DeepSeekChat} 智能体运行完成！")

    persistChatTurn(chatHistoryProvider, sessionId, history, input, response)
    return response
}

/**
 * 根据输入内容判断本轮是否需要走工具智能体。
 */
internal fun shouldUseToolAgent(input: String): Boolean {
    return TOOL_REQUEST_KEYWORDS.any(input::contains)
}

/**
 * 判断流式请求异常是否应回退到非流式模式。
 */
internal fun shouldFallbackToNonStreaming(error: Throwable): Boolean {
    val message = generateSequence(error) { it.cause }
        .mapNotNull(Throwable::message)
        .joinToString("\n")
    
    if (message.contains("400")) {
        println("[调试] DeepSeek 流式请求 400 错误详情:\n$message")
    }

    return message.contains("Expected status code 200 but was 400", ignoreCase = true) ||
            (message.contains("Status code: 400", ignoreCase = true) &&
                    message.contains("DeepSeek", ignoreCase = true))
}

/**
 * 先尝试流式执行，若命中可回退错误则自动切换为非流式执行。
 */
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

/**
 * 根据输入内容在普通流式聊天、工具模式与非流式兜底之间进行路由。
 */
internal suspend fun runAgentByMode(
    input: String,
    sessionId: String,
    streamedOutput: StringBuilder,
    streamingRunner: suspend (String, String) -> String,
    toolRunner: suspend (String, String) -> String,
    fallbackRunner: suspend (String, String) -> String = toolRunner,
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
            fallbackRunner = fallbackRunner,
            onFallback = onFallback
        )
    }
}

/**
 * 启动命令行聊天程序，并根据用户输入选择对应的处理模式。
 */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
fun main() {
    runBlocking {
        val apiKey: String = loadEnv()
            .getProperty("DEEPSEEK_API_KEY")
            ?: error("没有在.env文件中找到DEEPSEEK_API_KEY")

        val deepseekClient = DeepSeekLLMClient(apiKey)
        val chatHistoryProvider = MyChatHistory(CHAT_HISTORY_DIR)
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

        val sessionId = Uuid.random().toString()

        val toolAgent = createToolAgent(promptExecutor, allBuiltInTools, chatHistoryProvider)


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
                sessionId = sessionId,
                streamedOutput = streamedOutput,
                streamingRunner = { input, sessionId ->
                    runStreamingChat(
                        client = deepseekClient,
                        chatHistoryProvider = chatHistoryProvider,
                        input = input,
                        sessionId = sessionId,
                        streamedOutput = streamedOutput
                    )
                },
                toolRunner = { input, sessionId ->
                    toolAgent.run(input, sessionId = sessionId)
                },
                fallbackRunner = { input, sessionId ->
                    runNonStreamingChat(
                        client = deepseekClient,
                        chatHistoryProvider = chatHistoryProvider,
                        input = input,
                        sessionId = sessionId
                    )
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
