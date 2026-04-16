package com.agent.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.singleRunStrategy

/**
 * 负责提供仓库当前阶段可用的真实 Koog strategy。
 */
object AgentStrategyFactory {

    /**
     * 返回阶段 03 默认使用的 Koog single-run strategy。
     */
    fun singleRun(): AIAgentGraphStrategy<String, String> = singleRunStrategy()
}
