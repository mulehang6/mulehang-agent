package com.agent.shared.agent.resource

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** 资源快照两阶段发布的回归测试。 */
class AgentResourceRuntimeTest {
    /** 准备新快照不会替换旧运行时，只有显式 publish 才对后续读取可见。 */
    @Test
    fun `should preserve current snapshot until prepared reload is published`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val runtime = AgentResourceRuntime()
        val request = AgentResourceLoadRequest(userHome = home)
        val first = runtime.reload(request)

        val candidate = runtime.prepareReload(request)

        assertEquals(first, runtime.currentSnapshot())
        assertEquals(first.version + 1L, candidate.version)
        runtime.publish(candidate)
        assertEquals(candidate, runtime.currentSnapshot())
    }
}
