package com.agent.shared.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.agent.shared.persistence.db.MulehangDatabase
import com.agent.shared.persistence.db.MulehangDatabaseQueries
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 桌面端统一 SQLDelight 数据库。
 *
 * 单个进程中的所有访问都经过同一把锁，避免 UI 状态同步读写与协程后台写入交错。
 * SQLite 仍通过 WAL 允许其他只读连接观察已提交数据。
 */
class DesktopPersistenceDatabase private constructor(
    private val driver: SqlDriver,
    private val database: MulehangDatabase,
) : AutoCloseable {
    private val accessLock = ReentrantLock()

    /** 在串行访问区中执行只读操作。 */
    fun <T> read(block: (MulehangDatabaseQueries) -> T): T = accessLock.withLock {
        block(database.mulehangDatabaseQueries)
    }

    /** 在串行访问区和一个 SQL 事务中执行写操作。 */
    fun <T> write(block: (MulehangDatabaseQueries) -> T): T = accessLock.withLock {
        database.transactionWithResult {
            block(database.mulehangDatabaseQueries)
        }
    }

    /** 关闭底层数据库连接。 */
    override fun close() {
        accessLock.withLock(driver::close)
    }

    companion object {
        /** 打开或创建统一数据库，并启用外键、WAL 与合理的锁等待时间。 */
        fun open(databasePath: Path): DesktopPersistenceDatabase {
            val absolutePath = databasePath.toAbsolutePath().normalize()
            absolutePath.parent?.let(Files::createDirectories)
            val driver = JdbcSqliteDriver(
                url = "jdbc:sqlite:$absolutePath",
                properties = Properties().apply {
                    setProperty("foreign_keys", "true")
                    setProperty("journal_mode", "WAL")
                    setProperty("busy_timeout", "5000")
                },
                schema = MulehangDatabase.Schema,
            )
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            driver.execute(null, "PRAGMA journal_mode = WAL", 0)
            driver.execute(null, "PRAGMA busy_timeout = 5000", 0)
            val database = MulehangDatabase(driver)
            return DesktopPersistenceDatabase(driver, database)
        }
    }
}
