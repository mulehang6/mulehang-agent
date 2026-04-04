package learn.day1

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import utils.loadEnv
import kotlinx.coroutines.runBlocking

/**
 * 下一步学习：对话记忆（Chat Memory）
 * 在 Koog 中，你可以通过安装 ChatMemory 特性来让智能体具备记忆能力。
 */
fun main() {
    runBlocking {
        // 1. 加载环境变量 (同前两个示例)
        val envProperties = loadEnv()

        val apiKey = envProperties.getProperty("DEEPSEEK_API_KEY")
            ?: error("未在 .env 文件中设置 API 密钥。")

        // 2. 创建智能体，并安装对话记忆特性
        val deepSeekClient = DeepSeekLLMClient(apiKey)
        val agent = AIAgent(
            promptExecutor = MultiLLMPromptExecutor(deepSeekClient),
            llmModel = DeepSeekModels.DeepSeekChat
        ) {
            /**
             * 使用 install(ChatMemory) 来配置记忆功能。
             * [InMemoryChatHistoryProvider] 是一个简单的内存记忆提供者，
             * 会话数据保存在程序运行时的内存中。
             */
            install(ChatMemory) {
                chatHistoryProvider = InMemoryChatHistoryProvider()
                // 设置窗口大小，只记住最近的 10 条消息
                windowSize(10)
            }
        }

        /**
         * 3. 交互式多轮对话
         * 为了让智能体区分不同的用户或会话，我们可以在 run 方法中提供 [sessionId]。
         */
        val sessionId = "user-12345"
        println("=== 智能体已就绪 (输入 'exit' 或 '退出' 结束对话) ===")

        while (true) {
            print("\n用户: ")
            val input = readlnOrNull() ?: break
            
            if (input.lowercase() in listOf("exit", "quit", "退出", "结束")) {
                println("再见！")
                break
            }

            if (input.isBlank()) continue

            // 使用同一个 sessionId，智能体会自动加载之前的聊天记录
            val response = agent.run(input, sessionId = sessionId)
            println("智能体: $response")
        }
    }
}

// 帮助函数，用于打印分隔符
operator fun String.times(n: Int): String = this.repeat(n)
