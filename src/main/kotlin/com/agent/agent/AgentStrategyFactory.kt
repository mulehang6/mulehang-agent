package com.agent.agent

/**
 * 负责提供仓库当前阶段可用的 agent strategy 标识。
 */
object AgentStrategyFactory {

    /**
     * 返回阶段 03 默认使用的 single-run strategy 标识。
     */
    fun singleRun(): String = "single-run"
}
