package com.agent.shared.agent.api

/**
 * 用户输入的有序片段。
 *
 * 文本、工作区文件快照和图片必须在同一个列表中保存与重放，不能再把图片或文件单独挂在
 * 草稿对象上，否则“图 1 / 图 2”与交错文字会在发送时失去顺序。
 */
sealed interface UserInputPart {
    /** 普通可编辑文本。 */
    data class Text(
        val text: String,
    ) : UserInputPart

    /**
     * 发送时按 Pi 的 `<file>` 语义展开的工作区文件快照。
     *
     * [path] 仅用于展示与审计；[content] 是选择当时读取的不可变内容，后续磁盘改动不会
     * 改写本轮或历史重放的上下文。
     */
    data class FileSnapshot(
        val path: String,
        val content: String,
        val mimeType: String = "text/plain",
    ) : UserInputPart

    /**
     * 会话媒体库中的图片引用。
     *
     * [storagePath] 指向用户数据目录而非项目临时目录。运行时读取该引用构造 provider 图片
     * part；[label] 是稳定的“图N”编号，用于紧邻图片地注入给模型。
     */
    data class Image(
        val mediaId: String,
        val storagePath: String,
        val mimeType: String,
        val label: String,
    ) : UserInputPart
}
