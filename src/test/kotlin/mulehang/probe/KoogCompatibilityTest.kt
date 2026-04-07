package mulehang.probe

import ai.koog.agents.features.acp.AcpAgent
import ai.koog.agents.mcp.McpToolRegistryProvider
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 验证当前依赖中已暴露路线图所需的 Koog 能力入口。
 */
class KoogCompatibilityTest {
    @Test
    fun `should expose Koog feature classes required by the roadmap`() {
        assertNotNull(AcpAgent)
        assertNotNull(McpToolRegistryProvider::class)
    }
}
