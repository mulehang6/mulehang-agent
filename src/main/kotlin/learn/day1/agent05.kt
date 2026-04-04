package learn.day1

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import utils.loadEnv

/**
 * 下一步学习：流式输出 (Streaming) 与 自定义存储 (Custom Persistence)
 * 
 * 在本示例中，我们将：
 * 1. 实现一个自定义的 [ChatHistoryProvider]，将聊天记录持久化到本地 JSON 文件。
 * 2. 在控制台实现“打字机”效果的流式输出，提升交互体验。
 */

/**
 * 自定义本地文件聊天记录提供者。
 * 通过实现 [ChatHistoryProvider] 接口，我们可以控制聊天记录如何保存和加载。
 */
class JsonFileChatHistoryProvider(private val storageDir: String) : ChatHistoryProvider {
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
            println("[系统] 加载历史记录失败: ${e.message}")
            emptyList()
        }
    }

    override suspend fun store(conversationId: String, messages: List<Message>) {
        val file = getFile(conversationId)
        try {
            val content = json.encodeToString(messages)
            file.writeText(content)
        } catch (e: Exception) {
            println("[系统] 保存历史记录失败: ${e.message}")
        }
    }
}

private fun streamingSingleRunStrategy() = strategy("streaming_single_run") {
    val nodeStreaming by nodeLLMRequestStreamingAndSendResults<String>()

    edge(nodeStart forwardTo nodeStreaming)
    edge(
        nodeStreaming forwardTo nodeFinish
            onMultipleAssistantMessages { true }
            transformed { messages -> messages.joinToString("\n") { it.content } }
    )
}

fun main() {
    runBlocking {
        // 1. 加载环境变量
        val envProperties = loadEnv()
        val apiKey = envProperties.getProperty("DEEPSEEK_API_KEY")
            ?: error("未在 .env 文件中设置 API 密钥。")

        // 2. 创建智能体
        val deepSeekClient = DeepSeekLLMClient(apiKey)
        val streamedOutput = StringBuilder() // 用于收集流式内容
        
        val agent = AIAgent(
            promptExecutor = MultiLLMPromptExecutor(deepSeekClient),
            strategy = streamingSingleRunStrategy(),
            llmModel = DeepSeekModels.DeepSeekChat,
            temperature = 0.7, // 适当的温度有助于流式输出的多样性
            systemPrompt = "你是一个贴心的个人助手。你会记住用户的偏好并给出专业的建议。"
        ) {
            // 安装自定义的文件持久化记忆
            install(ChatMemory) {
                // 使用我们自己写的 JsonFileChatHistoryProvider
                chatHistoryProvider = JsonFileChatHistoryProvider("chat_history_example")
                windowSize(20)
            }

            // 安装事件处理器，用于实现流式输出
            handleEvents {
                onLLMStreamingFrameReceived { eventContext ->
                    val frame = eventContext.streamFrame
                    // 如果收到的是文本片段，则立即打印到控制台
                    if (frame is StreamFrame.TextDelta) {
                        val text = frame.text
                        print(text)
                        System.out.flush()
                        streamedOutput.append(text) // 记录已打印的内容
                        // 强制暂停一小会儿，模拟打字机效果（如果模型太快的话）
                        //delay(20)
                    }
                }
            }
        }
        
        // 3. 交互式对话
        val sessionId = "permanent-user-01"

        println("=== 增强型智能体 (流式输出 + 本地文件持久化记忆) ===")
        println("提示：你的聊天记录将保存在 'chat_history/$sessionId.json'。即使重启程序，它也会记得你。")

        while (true) {
            print("\n用户: ")
            val input = readlnOrNull() ?: break
            
            if (input.lowercase() in listOf("exit", "quit", "退出", "结束")) {
                println("\n再见！")
                break
            }

            if (input.isBlank()) continue

            print("智能体: ")
            streamedOutput.setLength(0) // 每一轮开始前清空流式缓存

            // 运行智能体。
            val response = agent.run(input, sessionId = sessionId)
            
            // 确保输出结束并换行
            if (streamedOutput.isNotEmpty()) {
                println()
                System.out.flush()
            }
            
            // 兜底逻辑：如果流式输出没有任何内容被打印出来，则直接打印完整响应
            if (streamedOutput.isEmpty() && response.isNotBlank()) {
                println(response)
                System.out.flush()
            }
        }
    }
}
