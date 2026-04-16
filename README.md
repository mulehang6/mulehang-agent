# mulehang-agent

一个基于 Kotlin/JVM 与 JetBrains Koog 的 agent 实验与重建仓库。

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
4. CLI 是第一主入口，ACP 是第二入口
5. direct HTTP internal API、tool、MCP 都通过统一 capability integration 接入

## 仓库结构

```text
.
├─ src/main/kotlin        # Kotlin 生产代码
├─ src/test/kotlin        # Kotlin 测试代码
├─ docs/
│  └─ superpowers/
│     ├─ specs/           # 总设计与阶段 spec
│     └─ plans/           # 总 implementation plan 与阶段 plan
├─ vendor/kilocode        # 参考实现 submodule
├─ mulehang-agent.json.example
└─ build.gradle.kts
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

## 构建与测试

在 PowerShell 中执行：

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

仓库当前只走构建和测试，不启动开发服务器。
