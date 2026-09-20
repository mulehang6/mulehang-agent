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

    /** 重叠准备必须拿到不同版本，较旧候选不能覆盖较新的已发布快照。 */
    @Test
    fun `should reject stale prepared snapshot`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val runtime = AgentResourceRuntime()
        val request = AgentResourceLoadRequest(userHome = home)
        runtime.reload(request)

        val older = runtime.prepareReload(request)
        val newer = runtime.prepareReload(request)

        assertEquals(older.version + 1L, newer.version)
        assertEquals(newer, runtime.publish(newer))
        assertEquals(newer, runtime.publish(older))
        assertEquals(newer, runtime.currentSnapshot())
    }
}
