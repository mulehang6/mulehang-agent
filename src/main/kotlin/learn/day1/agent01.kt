package learn.day1

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import utils.loadEnv
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        // 从 .env 文件中加载环境变量
        val envProperties = loadEnv()

        // 从加载的环境变量中获取 DeepSeek API 密钥
        val apiKey = envProperties.getProperty("DEEPSEEK_API_KEY")
            ?: error("未在 .env 文件中设置 API 密钥。")

        // 创建 LLM 客户端
        val deepSeekClient = DeepSeekLLMClient(apiKey)

        // 创建智能体
        val agent = AIAgent(
            // 使用 LLM 客户端创建一个提示词执行器
            promptExecutor = MultiLLMPromptExecutor(deepSeekClient),
            // 提供模型
            llmModel = DeepSeekModels.DeepSeekChat
        )

        // 运行智能体
        val result = agent.run("你好，你能帮我做事吗？")
        println(result)
    }
}
