package com.agent.shared.settings.model

/**
 * Agent 循环次数的全局约束。
 *
 * `-1` 仅是持久化语义，运行时必须转换成 [KOOG_MAXIMUM]，避免把无效负数传给 Koog。
 */
object AgentIterationLimit {
    const val MINIMUM = 50
    const val UNLIMITED = -1
    const val KOOG_MAXIMUM = Int.MAX_VALUE
    const val DEFAULT = MINIMUM

    /** 返回可传给 Koog 的正整数，非法值产生可定位的配置错误。 */
    fun resolve(value: Int?): Int = when {
        value == null -> DEFAULT
        value == UNLIMITED -> KOOG_MAXIMUM
        value >= MINIMUM -> value
        else -> throw IllegalConfigExceptions {
            "maxIterations 必须为 $UNLIMITED（表示最大值）或介于 $MINIMUM 到 $KOOG_MAXIMUM 之间"
        }
    }
}
