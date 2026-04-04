package learn.day1

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import utils.loadEnv
import kotlinx.coroutines.runBlocking

/**
 * 下一步学习：可观测性 (Observability) 与 系统提示词 (System Prompt)
 * 
 * 本示例将集成之前的“工具调用”和“对话记忆”，并展示如何：
 * 1. 设定智能体的人设 (System Prompt)。
 * 2. 监听智能体的生命周期事件 (handleEvents)，观察后台发生了什么。
 */

// 定义工具集 (复用之前的数学工具)
class Agent04MathTools : ToolSet {
    @Tool
    @LLMDescription("计算两个数字的和")
    fun add(
        @LLMDescription("第一个数字") a: Int,
        @LLMDescription("第二个数字") b: Int
    ): Int {
        return a + b
    }

    @Tool
    @LLMDescription("计算两个数字的积")
    fun multiply(
        @LLMDescription("第一个数字") a: Int,
        @LLMDescription("第二个数字") b: Int
    ): Int {
        return a * b
    }
}

fun main() {
    runBlocking {
        // 1. 加载环境变量
        val envProperties = loadEnv()
        val apiKey = envProperties.getProperty("DEEPSEEK_API_KEY")
            ?: error("未在 .env 文件中设置 API 密钥。")

        // 2. 准备工具
        val toolRegistry = ToolRegistryBuilder()
            .tools(Agent04MathTools())
            .build()

        // 3. 创建智能体，并配置各项功能
        val deepSeekClient = DeepSeekLLMClient(apiKey)
        val agent = AIAgent(
            promptExecutor = MultiLLMPromptExecutor(deepSeekClient),
            llmModel = DeepSeekModels.DeepSeekChat,
            toolRegistry = toolRegistry,
            // 设定人设：一个说话带幽默感的数学天才
            systemPrompt = "你是一个风趣幽默的数学天才。在回答问题时，你会加入一些有趣的点评，并根据需要调用数学工具。"
        ) {
            // 安装对话记忆
            install(ChatMemory) {
                chatHistoryProvider = InMemoryChatHistoryProvider()
                windowSize(10)
            }

            // [核心重点] 安装事件处理器，用于监控运行状态
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
                    println("[事件] 智能体运行完成！")
                }
            }
        }

        // 4. 交互式对话
        val sessionId = "user-pro"
        println("=== 进阶智能体 (已开启人设和监控) ===")
        println("提示：可以尝试询问数学题，观察后台日志如何反映工具调用。")

        while (true) {
            print("\n用户: ")
            val input = readlnOrNull() ?: break
            
            if (input.lowercase() in listOf("exit", "quit", "退出", "结束")) {
                println("再见！")
                break
            }

            if (input.isBlank()) continue

            // 运行智能体
            val response = agent.run(input, sessionId = sessionId)
            println("智能体: $response")
        }
    }
}
