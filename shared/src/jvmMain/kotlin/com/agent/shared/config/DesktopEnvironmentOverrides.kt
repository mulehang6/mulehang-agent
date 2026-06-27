package com.agent.shared.config

/**
 * 提供桌面环境下的环境变量覆盖。
 */
class DesktopEnvironmentOverrides(
    private val source: Map<String, String> = System.getenv(),
) {
    /**
     * 返回环境变量快照。
     */
    fun asMap(): Map<String, String> = source
}
