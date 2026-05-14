# IDEA MCP Priority Skill Design

## 背景

当前全局规则已经要求在可用时优先使用 IDEA MCP，但 agent 在实际执行中仍会绕过 `mcp__idea__*` 结构化工具，转而使用通用搜索、通用读文件、文本替换或猜测运行命令。这不是工具缺失问题，而是工具选择纪律不稳定。

## 目标

创建一个全局 skill，在任务涉及当前工作区工程时默认触发，并把 `mcp__idea__*` 的结构化工具提升为第一选择。

## 已确认约束

1. skill 安装位置为 `~/.agents/skills`
2. 只创建一个 skill
3. 覆盖范围仅限 `mcp__idea__*`
4. 包含工程高频工具、数据库工具和 `xdebug` 调试工具
5. 只要任务涉及当前工作区工程，就默认触发
6. 若存在匹配的 IDEA MCP 结构化工具，必须优先使用
7. `mcp__idea__execute_terminal_command` 不做强制优先要求，终端执行可继续使用 agent 自带 shell

## 设计选择

采用“强制决策规则 + 工具组最佳实践”的混合结构，而不是纯工具手册。

原因：

1. 问题核心不是“不知道工具怎么用”，而是“知道仍不优先选”
2. 仅罗列工具说明，无法稳定约束 agent 的选择顺序
3. 先给硬规则，再按工具组补充适用场景和禁忌，更能直接修正行为

## Skill 结构

skill 名称定为 `idea-mcp-priority`，主要由以下部分组成：

1. 触发条件：当前工作区工程任务且 `mcp__idea__*` 可用
2. 硬规则：结构化 IDEA 工具优先、允许退回的条件、完成前检查要求
3. 主路由表：任务类型到工具的默认映射
4. 工具组规则：
   - 文件与搜索
   - 符号与语义分析
   - 编辑、重构与校验
   - 构建、运行与测试
   - 调试
   - 数据库
   - inspection/PSI
5. 反模式：显式列出不应优先采取的做法

## 关键决策

### 1. 为什么不把 `execute_terminal_command` 也做成强制兜底

因为目标是优先选择结构化 IDEA MCP 工具，而不是把所有终端行为都塞进 IDEA 终端。对于纯命令执行，agent 自带 shell 反而更直接；真正需要纠正的是“有更合适的结构化工具却没用”。

### 2. 为什么把数据库和调试器也纳入

这两类工具同样具备强结构化能力，而且最容易被“猜测式操作”替代。把它们纳入 skill，可以统一约束：

1. 先查连接、schema、对象，再执行 SQL
2. 先确认 session/breakpoint，再启动和继续调试

## 成功标准

如果后续 agent 读取该 skill，应表现出以下选择顺序：

1. 项目内检索先选 IDEA MCP 搜索/文件工具
2. 符号问题先选 `search_symbol` / `get_symbol_info`
3. 构建先选 `build_project`
4. 运行与测试先选 `get_run_configurations` + `execute_run_configuration`
5. 调试先选 `xdebug_*`
6. 数据库任务先选 metadata 工具，再决定是否执行 SQL
