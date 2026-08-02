# 任务完整持久化设计

## 目标

在 Windows Desktop 应用中持久化完整任务（即聊天会话），使应用重启后能够恢复任务列表、时间线与供 Agent 续接的结构化历史。

持久化范围包括：

- 任务元数据：标识、标题、工作区、推理档位、上下文占用、状态与时间。
- 全部时间线项：用户与助手消息、原始 reasoning、工具调用参数、工具结果及展示字段。
- `AgentConversationHistoryMessage` 结构化历史，以便恢复后继续向 Agent 发送后续消息。

不包含云同步、自动备份、全文检索、任务归档或运行中任务续跑。

## 方案选择

采用 SQLite，而非单一 JSON 文件或 Kilo 的完整事件投影架构。

SQLite 适合本次完整会话数据：任务元数据可查询，异构时间线与历史负载可保留为 JSON，删除可由外键级联，未来可增量加入搜索与归档。实现只借鉴 Kilo 的“会话元数据 + 有序事件 + 迁移”边界；不引入其同步、事件投影、多端或复杂领域表。

数据库位于 `%USERPROFILE%\\.mulehang\\tasks.db`。启动时启用 SQLite 外键与 WAL（预写日志）模式：外键用于级联删除关联数据，WAL 降低写入时对历史读取的阻塞并改善异常退出后的恢复能力。

## 架构与数据模型

`shared/src/commonMain` 提供持久化仓库契约和与 UI 无关的 DTO；SQLite 的 JDBC 实现放在 `shared/src/jvmMain`。`desktopApp` 只负责通过状态层调用仓库，不向 `shared` 引入 Compose 依赖。

数据库包含下列表：

| 表 | 职责 | 关键字段 |
| --- | --- | --- |
| `task` | 任务的可查询元数据 | `id`、`title`、`workspace_path`、`reasoning_effort`、`context_usage_fraction`、`execution_state`、`created_at`、`updated_at` |
| `task_timeline_item` | 按显示顺序保存完整聊天时间线 | `task_id`、`sequence`、`kind`、`payload_json` |
| `task_history_item` | 按 Agent 上下文顺序保存结构化历史 | `task_id`、`sequence`、`kind`、`payload_json` |
| `schema_migration` | 记录已成功执行的数据库迁移 | `version`、`applied_at` |

`task_timeline_item` 与 `task_history_item` 通过 `task_id` 外键引用 `task`，并在任务删除时级联删除。时间线和历史分别编号，避免展示模型与 Agent 协议模型互相耦合。

DTO 显式编解码现有密封类型，覆盖聊天消息、reasoning、工具事件、用户/助手历史以及历史中的文本、reasoning、工具调用和工具结果。不会为持久化目的修改现有领域模型的可见接口。

## 写入、恢复与失败策略

创建任务、重命名、删除、附件变更、发送消息和非流式 Agent 状态变更后立即提交任务快照。流式正文和 reasoning 的连续增量使用短暂合并写入；当任务完成、失败、取消或窗口关闭时强制写入最终状态。

所有 SQLite I/O 在主线程之外串行执行。每次保存以单个事务替换一个任务的时间线与 history，确保元数据、时间线和 Agent 上下文不会只写入其中一部分。

启动时先从数据库读取任务，再构建 `ChatWindowState`。恢复的任务可直接显示在侧栏，后续发送使用恢复的 history。运行中、等待提问或等待审批的任务不会尝试恢复协程或工具进程；它们恢复为包含“执行已中断”错误的安全终态，并清除不可恢复的挂起问题与审批。

数据库缺失时创建空库。数据库读取、迁移或解码失败时，应用仍可启动为新的空白任务，并向用户显示“历史任务未加载”的错误；不会覆盖原数据库。

## 安全边界

原始 reasoning、完整工具参数和工具结果仅写入本机 `tasks.db`。它们不写入普通应用日志、不上传、不同步，也不自动备份。

SQLite 不提供静态加密；数据库安全依赖 Windows 当前用户目录的访问控制。该限制在设置或用户文档中明确说明。

## `paicli` 子模块移除

移除 `vendor/paicli` 的 gitlink 和 `.gitmodules` 注册项。已确认主工程没有对该子模块的代码、构建或文档引用。Kilo 与 liquid-glass 子模块不受影响。

## 验证

- SQLite 仓库 JVM 测试：空库初始化、迁移、完整任务往返读取、全部时间线类型、原始 reasoning、完整工具参数和结果、级联删除。
- 状态层测试：创建、重命名、发送、流式终态和删除均触发保存；重启恢复；未完成执行恢复为“执行已中断”。
- 所有持久化测试使用临时数据库，不读取或修改用户实际的 `tasks.db`。
- 移除 `paicli` 后检查 Git 子模块配置，并执行最小相关 Gradle 测试和 Desktop Kotlin 编译；不启动桌面应用。
