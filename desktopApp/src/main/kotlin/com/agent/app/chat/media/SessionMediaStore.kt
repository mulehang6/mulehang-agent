package com.agent.app.chat.media

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** 已写入会话媒体库的一张 PNG 图片引用。 */
data class StoredSessionImage(
    val mediaId: String,
    val path: Path,
    val mimeType: String = "image/png",
)

/** 将粘贴图片保存到用户媒体库的边界，避免污染工作区或项目临时目录。 */
interface SessionMediaStore {
    /** 保存已规整为 PNG 的图片，并返回稳定的会话引用。 */
    fun storePng(
        conversationId: String,
        bytes: ByteArray,
    ): StoredSessionImage
}

/**
 * 默认桌面媒体库实现。每个会话单独落在 `~/.mulehang/media/<conversationId>/` 下，历史任务
 * 只持久化引用路径和媒体 id，因此恢复时仍可按原顺序读取同一张图片。
 */
class DesktopSessionMediaStore(
    private val userHome: Path,
) : SessionMediaStore {
    /** 原子写入 PNG 文件，避免崩溃后暴露半截媒体。 */
    override fun storePng(
        conversationId: String,
        bytes: ByteArray,
    ): StoredSessionImage {
        val mediaId = UUID.randomUUID().toString()
        val directory = userHome.resolve(".mulehang/media/${conversationId.safeMediaSegment()}")
        Files.createDirectories(directory)
        val target = directory.resolve("$mediaId.png")
        val temporary = Files.createTempFile(directory, "$mediaId-", ".tmp")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, target)
        } finally {
            Files.deleteIfExists(temporary)
        }
        return StoredSessionImage(mediaId = mediaId, path = target)
    }
}

/** 防止会话 id 中的路径分隔符改变媒体库根目录。 */
private fun String.safeMediaSegment(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")
