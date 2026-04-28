# mulehang-agent

一个基于 Kotlin/JVM、JetBrains Koog 与 OpenTUI 的 agent 实验与重建仓库。

当前仓库采用 `runtime-first` 的 superpowers 工作流：

1. 先定义 runtime 契约
2. 再接 BYOK 与模型发现
3. 再接 Koog agent 与能力集成
4. 先做 CLI，再做 ACP
5. 最后补 memory、hardening 与可选 client surface

## 当前主线

仓库当前围绕这条阶段顺序推进：

1. `01-runtime-foundation-and-contracts`
2. `02-provider-byok-and-model-discovery`
3. `03-agent-strategy-and-capability-integration`
4. `04-cli-streaming-and-output`
5. `05-acp-protocol-bridge`
6. `06-memory-features-and-hardening`
7. `07-client-surfaces-optional`

核心约束：

1. JetBrains Koog 是 agent 语义中心
2. `custom provider` 是仓库自己的概念，不覆写 Koog 官方 provider
3. `custom provider` 支持提供商类型，默认 `OpenAI-compatible`
4. `cli/` 是第一用户入口，`runtime` 提供 stdio host 与后续 ACP/HTTP 能力
5. direct HTTP internal API、tool、MCP 都通过统一 capability integration 接入

## 仓库结构

```text
.
├─ runtime/
│  ├─ src/main/kotlin     # 当前 runtime、provider、capability、agent、server 主代码
│  ├─ src/test/kotlin     # 当前 runtime 模块测试
│  └─ build.gradle.kts    # runtime 模块构建与运行入口
├─ cli/
│  ├─ src/                # Bun + React + OpenTUI 终端界面
│  ├─ src/__tests__/      # CLI 单元测试
│  ├─ package.json        # CLI 脚本与依赖声明
│  └─ tsconfig.json       # TypeScript 配置
├─ docs/
│  └─ superpowers/
│     ├─ specs/           # 总设计与阶段 spec
│     └─ plans/           # 总 implementation plan 与阶段 plan
├─ vendor/kilocode        # 参考实现 submodule
├─ mulehang-agent.json.example
├─ build.gradle.kts       # 根工程聚合配置
└─ settings.gradle.kts
```

## 文档入口

先读总文档，再按阶段推进：

1. `docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md`
2. `docs/superpowers/plans/2026-04-16-koog-agent-implementation-plan.md`
3. 当前阶段对应的 `docs/superpowers/specs/*.md`
4. 当前阶段对应的 `docs/superpowers/plans/*.md`

## 本地配置

结构化配置默认放在本地 `mulehang-agent.json`，可以从示例文件复制：

```powershell
Copy-Item .\mulehang-agent.json.example .\mulehang-agent.json
```

敏感信息优先通过环境变量提供，不要提交真实 API Key 或 `.env`。

## 环境要求

runtime 侧使用 JDK 21 与 Gradle Wrapper；CLI 侧使用 Bun、TypeScript、React 与 `@opentui/react`。`cli/` 不是 Gradle 子模块，CLI 相关命令需要在 `cli` 目录下执行。

## 构建与测试

在 PowerShell 中验证 runtime：

```powershell
.\gradlew.bat build
```

编译项目并运行全部测试。

```powershell
.\gradlew.bat test
```

仅运行测试，适合快速验证。

```powershell
.\gradlew.bat clean
```

清理构建产物。

```powershell
.\gradlew.bat :runtime:installCliHostDist
```

生成 CLI 调用 runtime stdio host 所需的本地分发脚本与依赖。

在 PowerShell 中验证 CLI：

```powershell
Push-Location .\cli
bun test
bun run typecheck
Pop-Location
```

运行 CLI 单元测试与 TypeScript 静态类型检查。

仓库当前只走构建和测试，不启动开发服务器。
