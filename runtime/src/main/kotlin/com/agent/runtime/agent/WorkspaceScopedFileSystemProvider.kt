package com.agent.runtime.agent

import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.JVMFileSystemProvider
import java.nio.file.Path
import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * 为 Koog built-in 文件工具提供受工作区根目录约束的文件系统 provider。
 */
object WorkspaceScopedFileSystemProvider {
    /**
     * 创建只读 provider。
     */
    fun readOnly(root: Path): FileSystemProvider.ReadOnly<Path> =
        ScopedReadOnly(
            root = root.toAbsolutePath().normalize(),
            delegate = JVMFileSystemProvider.ReadOnly,
        )

    /**
     * 创建可读写 provider。
     */
    fun readWrite(root: Path): FileSystemProvider.ReadWrite<Path> =
        ScopedReadWrite(
            root = root.toAbsolutePath().normalize(),
            delegate = JVMFileSystemProvider.ReadWrite,
        )
}

/**
 * 对只读文件系统操作增加工作区根目录校验。
 */
private open class ScopedReadOnly(
    private val root: Path,
    private val delegate: FileSystemProvider.ReadOnly<Path>,
) : FileSystemProvider.ReadOnly<Path> {
    override fun toAbsolutePathString(path: Path): String = delegate.toAbsolutePathString(path.also(::requireWithinRoot))

    override fun fromAbsolutePathString(path: String): Path = delegate.fromAbsolutePathString(path).also(::requireWithinRoot)

    override fun joinPath(base: Path, vararg parts: String): Path =
        delegate.joinPath(base.also(::requireWithinRoot), *parts).also(::requireWithinRoot)

    override fun name(path: Path): String = delegate.name(path.also(::requireWithinRoot))

    override fun extension(path: Path): String = delegate.extension(path.also(::requireWithinRoot))

    override suspend fun metadata(path: Path): FileMetadata? = delegate.metadata(path.also(::requireWithinRoot))

    override suspend fun getFileContentType(path: Path): FileMetadata.FileContentType =
        delegate.getFileContentType(path.also(::requireWithinRoot))

    override suspend fun list(directory: Path): List<Path> = delegate.list(directory.also(::requireWithinRoot))

    override fun parent(path: Path): Path? = delegate.parent(path.also(::requireWithinRoot))?.takeIf(::isWithinRoot)

    override fun relativize(root: Path, path: Path): String? =
        delegate.relativize(root.also(::requireWithinRoot), path.also(::requireWithinRoot))

    override suspend fun exists(path: Path): Boolean = delegate.exists(path.also(::requireWithinRoot))

    override suspend fun readBytes(path: Path): ByteArray = delegate.readBytes(path.also(::requireWithinRoot))

    override suspend fun inputStream(path: Path): Source = delegate.inputStream(path.also(::requireWithinRoot))

    override suspend fun size(path: Path): Long = delegate.size(path.also(::requireWithinRoot))

    /**
     * 校验路径没有逃出工作区根目录。
     */
    protected fun requireWithinRoot(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        require(isWithinRoot(normalized)) {
            "Path '$normalized' is outside workspace root '$root'."
        }
    }

    /**
     * 判断标准化后的路径是否位于工作区内。
     */
    protected fun isWithinRoot(path: Path): Boolean = path.toAbsolutePath().normalize().startsWith(root)
}

/**
 * 对读写文件系统操作增加工作区根目录校验。
 */
private class ScopedReadWrite(
    root: Path,
    private val delegate: FileSystemProvider.ReadWrite<Path>,
) : ScopedReadOnly(root = root, delegate = delegate), FileSystemProvider.ReadWrite<Path> {
    override suspend fun create(path: Path, type: FileMetadata.FileType) =
        delegate.create(path.also(::requireWithinRoot), type)

    override suspend fun move(source: Path, target: Path) =
        delegate.move(source.also(::requireWithinRoot), target.also(::requireWithinRoot))

    override suspend fun copy(source: Path, target: Path) =
        delegate.copy(source.also(::requireWithinRoot), target.also(::requireWithinRoot))

    override suspend fun writeBytes(path: Path, data: ByteArray) =
        delegate.writeBytes(path.also(::requireWithinRoot), data)

    override suspend fun outputStream(path: Path, append: Boolean): Sink =
        delegate.outputStream(path.also(::requireWithinRoot), append)

    override suspend fun delete(path: Path) =
        delegate.delete(path.also(::requireWithinRoot))
}
