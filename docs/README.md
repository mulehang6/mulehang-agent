# Docs

## 文档主线

这套文档现在服务的是基于 JetBrains Koog 的 superpowers 工作流，而不是旧的手搓学习路径。

推荐阅读顺序：

1. 总设计文档
2. 总 implementation plan
3. 当前阶段对应的 spec
4. 当前阶段对应的 plan

总文档入口：

1. `docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md`
2. `docs/superpowers/plans/2026-04-16-koog-agent-implementation-plan.md`

## 阶段顺序

1. `01-runtime-foundation-and-contracts`
2. `02-provider-byok-and-model-discovery`
3. `03-agent-strategy-and-capability-integration`
4. `04-cli-streaming-and-output`
5. `05-acp-protocol-bridge`
6. `06-memory-features-and-hardening`
7. `07-client-surfaces-optional`

## 这些阶段分别解决什么

1. `01` 先定义 runtime 主轴和核心契约
2. `02` 再接 `custom provider`、提供商类型、自动探测与模型发现
3. `03` 再接 Koog `AIAgent`、tool、MCP 与 direct HTTP internal API
4. `04` 先把 CLI 做成第一主入口，并处理 streaming 与输出
5. `05` 再把 ACP 作为第二入口桥接进来
6. `06` 最后补 memory、snapshot、observability 与 hardening
7. `07` 为 KMP desktop 和 Web UI 预留 client surface

## 文档约束

1. 总 spec 负责全局架构与边界
2. 总 plan 负责全局执行顺序
3. 阶段 spec 只写当前阶段的目标、边界、非目标和验证标准
4. 阶段 plan 必须写成可执行文档，包含文件路径、验证方式和构建命令
