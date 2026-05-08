package com.agent.runtime.server

/**
 * 表示共享本地 runtime server 的最小认证策略。
 */
class RuntimeServerAuth private constructor(
    private val requiredToken: String?,
) {

    /**
     * 判断当前请求 token 是否满足最小认证要求。
     */
    fun isAuthorized(token: String?): Boolean = requiredToken == null || token == requiredToken

    companion object {

        /**
         * 创建要求 Bearer token 的认证策略。
         */
        fun required(token: String): RuntimeServerAuth = RuntimeServerAuth(token)

        /**
         * 创建关闭鉴权的认证策略，供测试或显式本地调试使用。
         */
        fun disabledForTests(): RuntimeServerAuth = RuntimeServerAuth(null)
    }
}
