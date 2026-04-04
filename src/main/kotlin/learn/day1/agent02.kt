package learn.day1

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import utils.loadEnv
import kotlinx.coroutines.runBlocking

/**
 * 这是一个工具集，定义了智能体可以调用的外部方法。
 * 在 Koog 中，工具集建议实现 [ToolSet] 接口。
 */
class MathTools : ToolSet {
    /**
     * 使用 @Tool 注解将方法标记为一个工具。
     * 使用 @LLMDescription 提供描述，帮助大模型理解工具的用途和参数含义。
     */
    @Tool
    @LLMDescription("计算两个数字的和")
    fun add(
        @LLMDescription("第一个数字") a: Int,
        @LLMDescription("第二个数字") b: Int
    ): Int {
        println("[系统日志] 调用工具 add($a, $b)")
        return a + b
    }

    @Tool
    @LLMDescription("计算两个数字的积")
    fun multiply(
        @LLMDescription("第一个数字") a: Int,
        @LLMDescription("第二个数字") b: Int
    ): Int {
        println("[系统日志] 调用工具 multiply($a, $b)")
        return a * b
    }
}

fun main() {
    runBlocking {
        // 1. 加载环境变量
        val envProperties = loadEnv()

        val apiKey = envProperties.getProperty("DEEPSEEK_API_KEY")
            ?: error("未在 .env 文件中设置 API 密钥。")

        // 2. 创建工具注册表
        val mathTools = MathTools()
        // ToolRegistryBuilder 可以直接接收 ToolSet 实例并提取其中的工具
        val toolRegistry = ToolRegistryBuilder()
            .tools(mathTools)
            .build()

        // 3. 创建智能体，并传入工具注册表
        val deepSeekClient = DeepSeekLLMClient(apiKey)
        val agent = AIAgent(
            promptExecutor = MultiLLMPromptExecutor(deepSeekClient),
            llmModel = DeepSeekModels.DeepSeekChat,
            toolRegistry = toolRegistry // 将工具提供给智能体
        )

        // 4. 运行智能体
        // 提一个需要调用工具的问题，演示多步思考和工具调用
        val question = "请帮我计算 123 加上 456 的结果，然后再将结果乘以 2。"
        println("用户提问: $question")
        
        val result = agent.run(question)
        println("\n智能体回答:")
        println(result)
    }
}
