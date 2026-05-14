# Koog Built-in File Tools Design

## 背景

当前仓库已经具备真实 Koog agent 装配链路，但工具侧主要覆盖三类仓库自有桥接：

1. local/custom tool
2. MCP-backed capability
3. direct HTTP internal API capability

现阶段还没有把 Koog 官方 built-in tools 接入到统一 runtime 主轴中。尤其是文件与目录工具，已经具备明确能力边界和成熟实现，适合作为第一批官方工具接入点。

## 目标

本轮只接入 Koog 官方文件与目录 built-in tools，并让它们通过现有 `CapabilitySet -> KoogToolRegistryAssembler -> AIAgent` 链路生效。

接入范围固定为：

1. `__list_directory__`
2. `__read_file__`
3. `__write_file__`
4. `edit_file`

## 非目标

本轮不处理以下内容：

1. `SayToUser`
2. `AskUser`
3. `ExitTool`
4. shell / process execution
5. 基于自定义 schema 的业务工具设计
6. 跨工作区或系统全盘文件访问策略

## 已确认约束

1. 工具分类按风险分层，而不是按来源分层
2. 风险分层只使用结构化枚举：`LOW`、`MID`、`HIGH`
3. 注释需要说明三档风险的语义，但注释不能替代结构化字段
4. `__list_directory__` 与 `__read_file__` 归类为 `LOW`
5. `__write_file__` 与 `edit_file` 归类为 `MID`
6. 本轮不引入 `HIGH` 风险工具，但模型必须为后续 shell 等能力预留位置
7. 文件工具默认限制在当前仓库根目录 `D:\JetBrains\projects\idea_projects\mulehang-agent`
8. 现有 custom/MCP/HTTP capability 继续保留，不因本轮接入被删除或重写
9. `EditFileTool` 在当前 Koog `0.8.0` 源码中的实际 descriptor name 为 `edit_file`，实现与测试应以源码真值为准

## 设计选择

采用“风险分层 capability 模型 + Koog built-in file tool 桥接”的方案，而不是把 built-in tools 无条件直接塞进 registry。

原因：

1. 当前仓库已经把 `CapabilitySet` 作为 runtime 的统一能力边界
2. 工具来源并不稳定，后续同一类风险能力可以来自 Koog built-in、自定义工具或其他桥接
3. 风险分层比“built-in/custom”更适合作为后续策略控制、CLI 展示和测试断言的稳定维度
4. 直接无条件注册 built-in tools 会让所有会话默认拥有文件写权限，边界过粗

## 风险分层模型

### ToolRiskLevel

新增统一风险枚举：

1. `LOW`
   - 只读
   - 无持久副作用
   - 典型例子：读文件、列目录、搜索
2. `MID`
   - 会修改工作区内容
   - 副作用受限在受控文件边界内
   - 典型例子：写文件、编辑文件
3. `HIGH`
   - 可能触发外部执行或更强副作用
   - 典型例子：shell、进程执行、系统级操作

本轮虽然只接入 `LOW` 和 `MID`，但 `HIGH` 必须在模型层预先定义，避免后续再改 descriptor 契约。

## 能力模型

### 1. Descriptor 扩展

现有能力描述需要补充风险字段，而不是只保留 `id` 与 `kind`。

推荐方向：

1. 保留现有 `id`
2. 保留或收敛现有 `kind`
3. 新增 `riskLevel: ToolRiskLevel`

这样后续可以在不改变调用方主逻辑的前提下：

1. 对工具做风险过滤
2. 在 CLI/UI 展示风险标签
3. 在测试中断言工具归类

### 2. 文件工具 capability

本轮新增一个面向文件系统 built-in tools 的 capability 适配层，用来声明：

1. 允许的根目录
2. 是否启用目录/读取能力
3. 是否启用写入/编辑能力
4. 每个具体工具对应的风险等级

该 capability 的职责是表达“当前 runtime 是否允许 agent 拥有哪些文件能力”，而不是重复实现文件读写逻辑。

### 3. 为什么不把 built-in file tools 伪装成普通 custom tool

因为普通 custom tool 的语义是“仓库自定义业务能力”，而 Koog built-in file tools 的语义是“通用宿主文件系统能力”。两者在这些方面都不同：

1. 权限模型
2. 参数形状
3. 实现来源
4. 后续安全约束

因此本轮保留它们作为独立 capability 入口，但仍旧服从统一的风险分层和 registry 装配主线。

## Registry 装配

### KoogToolRegistryAssembler

`KoogToolRegistryAssembler` 需要扩展为同时处理：

1. 现有 local/custom tool
2. 现有 MCP capability
3. 现有 HTTP capability
4. 新增 built-in file tools capability

具体 built-in tool 的注册方式固定为：

1. `ListDirectoryTool(JVMFileSystemProvider.ReadOnly)`
2. `ReadFileTool(JVMFileSystemProvider.ReadOnly)`
3. `WriteFileTool(JVMFileSystemProvider.ReadWrite)`
4. `EditFileTool(JVMFileSystemProvider.ReadWrite)`，对应工具名 `edit_file`

但不能直接把默认 `JVMFileSystemProvider` 暴露到整个系统根目录。需要在桥接层额外包一层“工作区根目录限制”。

## 文件系统边界

### 工作区限制

文件与目录 built-in tools 必须默认限制在当前仓库根目录：

`D:\JetBrains\projects\idea_projects\mulehang-agent`

这样做的原因：

1. 当前工具接入目标是工程内 agent，而不是通用桌面 agent
2. `__write_file__` 和 `edit_file` 属于 `MID` 风险，不能默认获得系统范围写权限
3. 这与当前仓库“runtime-first + workspace task”定位一致

### 读写权限边界

工具范围按风险固定为：

1. `LOW`
   - `__list_directory__`
   - `__read_file__`
2. `MID`
   - `__write_file__`
   - `edit_file`

后续如果需要更细粒度策略，例如“只允许读、不允许写”，应当通过 capability 配置或 policy 层控制，而不是在 agent prompt 中口头约束。

## 实现边界

本轮实现应尽量保持改动集中在工具桥接层，不回头重写 runtime 主轴：

1. `CapabilityContract.kt`
2. `CapabilitySet.kt`
3. 新增或扩展文件工具 capability adapter
4. `KoogToolRegistryAssembler.kt`
5. `AgentAssembly.kt` 如需透传新 capability
6. 对应测试

HTTP server、CLI transcript、provider binding 和 agent streaming 主链路不应因本轮接入而被大改。

## 测试策略

至少补齐以下验证：

1. descriptor 能正确暴露 `LOW` / `MID`
2. 文件工具 capability 能声明受限根目录
3. assembler 在 capability 启用时能注册 4 个 Koog built-in file tools
4. assembler 在只允许只读能力时，只注册 `__list_directory__` 和 `__read_file__`
5. assembler 在超出工作区路径时会拒绝访问
6. 现有 custom/MCP/HTTP capability 装配不回归

## 成功标准

本轮完成后，系统应满足：

1. agent 能通过 Koog built-in tools 列出仓库目录
2. agent 能读取仓库内文本文件
3. agent 能在仓库根目录约束内写文件和编辑文件
4. 文件工具能力在 runtime 模型中带有明确 `LOW` / `MID` 风险标记
5. 现有 custom/MCP/HTTP capability 装配与测试不被破坏
