package com.agent.runtime.capability

import com.agent.runtime.core.CapabilityRequest
import com.agent.runtime.core.RuntimeCapabilityRequest
import com.agent.runtime.core.RuntimeInfoEvent
import com.agent.runtime.core.RuntimeResult
import com.agent.runtime.core.RuntimeSuccess
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 capability contract 能统一表达 tool、MCP 和 HTTP 能力。
 */
class CapabilityContractTest {

    @Test
    fun `should unify tool mcp and http capabilities through one contract`() = runTest {
        val toolAdapter = FakeCapabilityAdapter(
            descriptor = CapabilityDescriptor(id = "tool.echo", kind = "tool", riskLevel = ToolRiskLevel.MID),
            result = RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "tool"))),
        )
        val mcpAdapter = FakeCapabilityAdapter(
            descriptor = CapabilityDescriptor(id = "mcp.list", kind = "mcp", riskLevel = ToolRiskLevel.HIGH),
            result = RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "mcp"))),
        )
        val httpAdapter = FakeCapabilityAdapter(
            descriptor = CapabilityDescriptor(id = "http.internal", kind = "http", riskLevel = ToolRiskLevel.HIGH),
            result = RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "http"))),
        )
        val capabilitySet = CapabilitySet(adapters = listOf(toolAdapter, mcpAdapter, httpAdapter))

        assertEquals(
            listOf(toolAdapter.descriptor, mcpAdapter.descriptor, httpAdapter.descriptor),
            capabilitySet.descriptors(),
        )
        assertEquals(
            RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "http"))),
            capabilitySet.execute(
                capabilityId = "http.internal",
                request = RuntimeCapabilityRequest(capabilityId = "http.internal"),
            ),
        )
    }

    /**
     * 用于固定统一契约行为的测试适配器。
     */
    private class FakeCapabilityAdapter(
        override val descriptor: CapabilityDescriptor,
        private val result: RuntimeResult,
    ) : CapabilityAdapter {
        override suspend fun execute(request: CapabilityRequest): RuntimeResult = result
    }
}
