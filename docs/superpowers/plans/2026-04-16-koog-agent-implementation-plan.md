# Koog Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前仓库的文档主线从旧的手搓阶段说明，重建为以 JetBrains Koog 为核心、采用 `runtime-first` 架构的 superpowers workflow 文档体系。

**Architecture:** 先保留已经确认的总设计文档，再围绕它重写仓库入口文档、7 组阶段 spec/plan，并删除旧的手搓阶段文档与过时设计/计划文档。新的文档体系以 runtime 主轴为中心，明确分层纳入 BYOK、自定义提供商类型、模型发现、Koog agent、tool/MCP/direct HTTP、CLI、ACP、memory 和未来 client surface。

**Tech Stack:** Markdown, JetBrains Koog, Kotlin/JVM, Gradle, JetBrains file inspection

---

### Task 1: 重写仓库入口文档

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Check: `mulehang-agent.json.example`

- [ ] **Step 1: 重写 `README.md` 的项目定位**

把当前“纯手搓重建”表述替换为新的主线，至少覆盖以下内容：

```md
# mulehang-agent

一个基于 Kotlin/JVM 与 JetBrains Koog 的 agent 实验与重建仓库。

当前仓库采用 `runtime-first` 的 superpowers 工作流：

1. 先定义 runtime 契约
2. 再接 BYOK 与模型发现
3. 再接 Koog agent 与能力集成
4. 先做 CLI，再做 ACP
5. 最后补 memory、hardening 与可选 client surface
```

- [ ] **Step 2: 删除 `docs/README.md`，把入口收敛到总 spec / 总 plan**

删除 `docs/README.md`，避免在 `docs` 目录继续保留非 superpowers 标准入口。删除后，仓库入口只保留：

```md
1. `README.md`
2. `AGENTS.md`
3. `docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md`
4. `docs/superpowers/plans/2026-04-16-koog-agent-implementation-plan.md`
5. 当前阶段对应的 `docs/superpowers/specs/*.md`
6. 当前阶段对应的 `docs/superpowers/plans/*.md`
```

- [ ] **Step 3: 同步 `AGENTS.md` 的仓库结构与文档入口**

确认 `AGENTS.md` 不再提及手搓主线、`ancient-handcrafted` 或旧阶段名，并把文档入口同步成新的 superpowers 结构。至少把项目结构段落改成下面这个方向：

```md
设计文档与实施计划统一放在 `docs/superpowers/specs` 和 `docs/superpowers/plans`。
总设计文档与总 implementation plan 用日期命名；
阶段文档按 `01-...` 到 `07-...` 命名。
```

- [ ] **Step 4: 检查 `mulehang-agent.json.example` 的注释与字段描述**

如果示例文件的说明仍然暗示旧的手搓主线或旧 provider 模型，记录并在后续阶段文档中纳入同步修改；如果没有旧描述，明确不改该文件。

- [ ] **Step 5: 运行文档检查**

对以下文件运行 JetBrains file inspection，要求 error 和 warning 为零：

```text
README.md
AGENTS.md
```

Expected: no errors, no warnings.

### Task 2: 重写阶段 spec 命名与结构

**Files:**
- Create: `docs/superpowers/specs/01-runtime-foundation-and-contracts.md`
- Create: `docs/superpowers/specs/02-provider-byok-and-model-discovery.md`
- Create: `docs/superpowers/specs/03-agent-strategy-and-capability-integration.md`
- Create: `docs/superpowers/specs/04-cli-streaming-and-output.md`
- Create: `docs/superpowers/specs/05-acp-protocol-bridge.md`
- Create: `docs/superpowers/specs/06-memory-features-and-hardening.md`
- Create: `docs/superpowers/specs/07-client-surfaces-optional.md`

- [ ] **Step 1: 写 `01-runtime-foundation-and-contracts.md`**

文档必须明确：

```md
## 目标
先定义 session、request context、capability request、event/result、error boundary。

## 非目标
不提前实现 BYOK、tool、ACP 或 memory。

## 验证标准
后续每个阶段的新能力都必须能挂到这套 runtime 契约上。
```

- [ ] **Step 2: 写 `02-provider-byok-and-model-discovery.md`**

文档必须明确：

```md
## 目标
支持 `custom provider`、`baseUrl`、`apiKey`、提供商类型、保存后自动探测与模型发现。

## 关键边界
区分用户输入配置、远端探测事实、运行时 binding。

## 支持的提供商类型
1. `OpenAI-compatible`
2. `Anthropic-compatible`
3. `Gemini-compatible`
```

- [ ] **Step 3: 写 `03-agent-strategy-and-capability-integration.md`**

文档必须明确：

```md
## 目标
接入 JetBrains Koog `AIAgent` 与 `singleRunStrategy`。

## 能力接入
1. local/custom tools
2. MCP
3. direct HTTP internal API

## 关键边界
agent 消费统一 capability contract，而不是直接依赖接入细节。
```

- [ ] **Step 4: 写 `04-cli-streaming-and-output.md`**

文档必须明确：

```md
## 目标
把 CLI 做成第一主入口。

## 范围
输入解析、streaming、structured output、状态提示、错误展示。

## 非目标
不在这一阶段引入 ACP。
```

- [ ] **Step 5: 写 `05-acp-protocol-bridge.md`**

文档必须明确：

```md
## 目标
用 Koog `AcpAgent` feature 把同一套 runtime 主轴桥接给 ACP。

## 关键边界
ACP 是第二入口，不是第二套 runtime。
```

- [ ] **Step 6: 写 `06-memory-features-and-hardening.md`**

文档必须明确：

```md
## 目标
接入 memory、snapshot/persistence、tracing/observability、失败恢复与集成验证。

## 非目标
不回头重写前面阶段的核心契约。
```

- [ ] **Step 7: 写 `07-client-surfaces-optional.md`**

文档必须明确：

```md
## 目标
为 KMP desktop 和 Web UI 预留 client surface。

## 关键边界
它们复用 runtime、agent 和协议层，不重写核心。
```

### Task 3: 重写阶段 plan 为可执行文档

**Files:**
- Create: `docs/superpowers/plans/01-runtime-foundation-and-contracts.md`
- Create: `docs/superpowers/plans/02-provider-byok-and-model-discovery.md`
- Create: `docs/superpowers/plans/03-agent-strategy-and-capability-integration.md`
- Create: `docs/superpowers/plans/04-cli-streaming-and-output.md`
- Create: `docs/superpowers/plans/05-acp-protocol-bridge.md`
- Create: `docs/superpowers/plans/06-memory-features-and-hardening.md`
- Create: `docs/superpowers/plans/07-client-surfaces-optional.md`

- [ ] **Step 1: 写 `01-runtime-foundation-and-contracts.md` 的执行计划**

计划必须给出精确文件路径、测试入口和构建命令，至少覆盖：

```md
**Files:**
- Create: `src/main/kotlin/com/agent/runtime/...`
- Create: `src/test/kotlin/...`

- [ ] 定义 runtime 契约测试
- [ ] 运行单测验证失败
- [ ] 写最小实现
- [ ] 运行 `.\gradlew.bat test`
```

- [ ] **Step 2: 写 `02-provider-byok-and-model-discovery.md` 的执行计划**

计划必须明确拆分：

```md
1. `custom provider` 配置模型
2. 提供商类型选择
3. probe adapter
4. discovery adapter
5. binding resolver
```

并在验证步骤里显式要求覆盖：

```md
- OpenAI-compatible
- Anthropic-compatible
- Gemini-compatible
- 提供商类型变更后的重新探测
```

- [ ] **Step 3: 写 `03-agent-strategy-and-capability-integration.md` 的执行计划**

计划必须明确：

```md
1. Koog `AIAgent` 最小装配
2. `singleRunStrategy`
3. tool registry
4. MCP 接入
5. direct HTTP adapter
```

- [ ] **Step 4: 写 `04-cli-streaming-and-output.md` 的执行计划**

计划必须明确：

```md
1. CLI 入口文件
2. streaming 输出策略
3. structured output 呈现
4. 用户可见错误与状态消息
```

- [ ] **Step 5: 写 `05-acp-protocol-bridge.md` 的执行计划**

计划必须明确：

```md
1. ACP session bridge
2. `AcpAgent` feature 安装
3. ACP 事件映射
4. 与 CLI 共用 runtime 的验证
```

- [ ] **Step 6: 写 `06-memory-features-and-hardening.md` 的执行计划**

计划必须明确：

```md
1. memory feature
2. snapshot / persistence
3. tracing / observability
4. 失败恢复
5. 集成测试
```

- [ ] **Step 7: 写 `07-client-surfaces-optional.md` 的执行计划**

计划必须明确：

```md
1. KMP desktop 的入口假设
2. Web UI 的入口假设
3. 复用 runtime / protocol 的限制
```

### Task 4: 删除旧的手搓阶段文档与过时设计文档

**Files:**
- Delete: `docs/superpowers/specs/00-minimal-skeleton.md`
- Delete: `docs/superpowers/specs/01-config-and-provider.md`
- Delete: `docs/superpowers/specs/02-runtime-and-cli.md`
- Delete: `docs/superpowers/specs/03-tooling-and-permission.md`
- Delete: `docs/superpowers/plans/00-minimal-skeleton.md`
- Delete: `docs/superpowers/plans/01-config-and-provider.md`
- Delete: `docs/superpowers/plans/02-runtime-and-cli.md`
- Delete: `docs/superpowers/plans/03-tooling-and-permission.md`
- Delete: `docs/superpowers/specs/2026-04-16-handcrafted-docs-reorganization-design.md`
- Delete: `docs/superpowers/plans/2026-04-16-handcrafted-docs-reorganization.md`
- Delete: `docs/superpowers/plans/2026-04-16-agents-handcrafted-gate.md`

- [ ] **Step 1: 删除旧阶段 spec**

Run:

```powershell
Get-ChildItem docs\superpowers\specs
```

Expected before delete: 能看到 `00-minimal-skeleton.md` 到 `03-tooling-and-permission.md`。

- [ ] **Step 2: 删除旧阶段 plan**

Run:

```powershell
Get-ChildItem docs\superpowers\plans
```

Expected before delete: 能看到 `00-minimal-skeleton.md` 到 `03-tooling-and-permission.md`。

- [ ] **Step 3: 删除过时的手搓重组文档**

删除今天生成但已经失效的手搓重组设计/计划文档，保留新的 Koog 总 spec 与总 implementation plan。

- [ ] **Step 4: 再次列出目录确认结果**

Run:

```powershell
Get-ChildItem docs\superpowers\specs
Get-ChildItem docs\superpowers\plans
```

Expected after delete: 只剩新的总文档与 `01-07` 阶段文档。

### Task 5: 全量检查文档一致性

**Files:**
- Check: `README.md`
- Check: `AGENTS.md`
- Check: `docs/superpowers/specs/*.md`
- Check: `docs/superpowers/plans/*.md`

- [ ] **Step 1: 对所有新增和修改的 Markdown 运行 JetBrains file inspection**

至少检查：

```text
docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md
docs/superpowers/plans/2026-04-16-koog-agent-implementation-plan.md
docs/superpowers/specs/01-runtime-foundation-and-contracts.md
docs/superpowers/specs/02-provider-byok-and-model-discovery.md
docs/superpowers/specs/03-agent-strategy-and-capability-integration.md
docs/superpowers/specs/04-cli-streaming-and-output.md
docs/superpowers/specs/05-acp-protocol-bridge.md
docs/superpowers/specs/06-memory-features-and-hardening.md
docs/superpowers/specs/07-client-surfaces-optional.md
docs/superpowers/plans/01-runtime-foundation-and-contracts.md
docs/superpowers/plans/02-provider-byok-and-model-discovery.md
docs/superpowers/plans/03-agent-strategy-and-capability-integration.md
docs/superpowers/plans/04-cli-streaming-and-output.md
docs/superpowers/plans/05-acp-protocol-bridge.md
docs/superpowers/plans/06-memory-features-and-hardening.md
docs/superpowers/plans/07-client-surfaces-optional.md
```

Expected: no errors, no warnings.

- [ ] **Step 2: 手工通读术语一致性**

确认以下术语在所有文档里统一：

```text
runtime-first
custom provider
提供商类型
CLI first
ACP second
direct HTTP internal API
```

- [ ] **Step 3: 手工通读阶段顺序**

确认所有入口文档和阶段文档都使用同一顺序：

```text
01-runtime-foundation-and-contracts
02-provider-byok-and-model-discovery
03-agent-strategy-and-capability-integration
04-cli-streaming-and-output
05-acp-protocol-bridge
06-memory-features-and-hardening
07-client-surfaces-optional
```

- [ ] **Step 4: 记录验证结果**

在最终汇报中列出：

```text
1. 新增了哪些总文档
2. 新增了哪些阶段文档
3. 删除了哪些旧文档
4. inspection 是否全部通过
```

## Self-Review

- Spec coverage:
  - 入口文档同步：Task 1
  - 7 组阶段 spec：Task 2
  - 7 组阶段 plan：Task 3
  - 删除旧手搓文档：Task 4
  - 全量一致性检查：Task 5
- Placeholder scan:
  - 没有 `TODO`、`TBD` 或“后续补充”占位语
  - 每个任务都给出明确文件路径、目标结构和检查要求
- Type consistency:
  - 总设计文档里的阶段顺序与本计划完全一致
  - `custom provider`、提供商类型、CLI first、ACP second 等术语已固定
