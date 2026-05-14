package com.agent.runtime.capability

/**
 * 表示一项 Koog built-in 文件工具声明及其工作区边界。
 */
data class BuiltInFileToolCapability(
    val descriptor: CapabilityDescriptor,
    val workspaceRoot: String,
) {
    companion object {
        /**
         * 声明目录树读取工具。
         */
        fun listDirectory(root: String): BuiltInFileToolCapability = BuiltInFileToolCapability(
            descriptor = CapabilityDescriptor(
                id = "__list_directory__",
                kind = "filesystem",
                riskLevel = ToolRiskLevel.LOW,
            ),
            workspaceRoot = root,
        )

        /**
         * 声明文本文件读取工具。
         */
        fun readFile(root: String): BuiltInFileToolCapability = BuiltInFileToolCapability(
            descriptor = CapabilityDescriptor(
                id = "__read_file__",
                kind = "filesystem",
                riskLevel = ToolRiskLevel.LOW,
            ),
            workspaceRoot = root,
        )

        /**
         * 声明文本文件覆盖写入工具。
         */
        fun writeFile(root: String): BuiltInFileToolCapability = BuiltInFileToolCapability(
            descriptor = CapabilityDescriptor(
                id = "__write_file__",
                kind = "filesystem",
                riskLevel = ToolRiskLevel.MID,
            ),
            workspaceRoot = root,
        )

        /**
         * 声明单次补丁式编辑工具。
         *
         * Koog 0.8.0 的实际工具名是 `edit_file`。
         */
        fun editFile(root: String): BuiltInFileToolCapability = BuiltInFileToolCapability(
            descriptor = CapabilityDescriptor(
                id = "edit_file",
                kind = "filesystem",
                riskLevel = ToolRiskLevel.MID,
            ),
            workspaceRoot = root,
        )
    }
}
