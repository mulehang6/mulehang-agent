# Provider Probe And Model Discovery Design

## 背景

当前仓库已经具备基于 `ProviderRegistry`、`ProviderGateway` 和 `ProviderConfig` 的多 provider 配置骨架，支持通过 `apiKey`、`baseUrl` 和自定义请求头组装执行绑定。

但现阶段仍有两个明显缺口：

1. 用户配置 provider 后，缺少“测试连接是否可用”的独立能力。
2. 用户无法从已配置 provider 中拉取当前可用模型列表，只能依赖本地静态硬编码模型。

本设计只补齐“连接探测 + 模型发现”能力，不在这一阶段重构现有聊天执行链路。当前聊天主链路仍以 OpenRouter 为中心，避免在 phase0-1 的增量内同时改动运行时执行器和 provider 探测体系。

## 目标

新增一套与聊天执行器解耦的 provider 探测层，使用户在配置好 `baseUrl` 和 `apiKey` 后，可以：

- 通过独立 CLI 命令测试单个 provider 是否可用
- 通过独立 CLI 命令获取该 provider 的可用模型列表
- 一次性检查所有已启用 provider 的连通性
- 在 CLI 启动时自动执行非阻塞的后台健康检查

首批支持范围限定为以下 7 家 provider，并且都允许自定义 `baseUrl`：

- `openai`
- `openrouter`
- `anthropic`
- `google`
- `deepseek`
- `mistral`
- `ollama`

## 非目标

以下内容不属于本次设计范围：

- 不把聊天主执行链路从 OpenRouter 改造成真正的多 provider 运行时
- 不把远端模型列表持久化到缓存文件
- 不把远端模型列表回写到 `mulehang-agent.json`
- 不引入额外的 provider UI、交互式配置向导或图形界面
- 不在本阶段支持 Koog 其余 provider，如 Azure、Bedrock、Alibaba、Vertex 等

## 设计原则

### 1. 探测与执行解耦

provider 探测层只负责：

- 测试连接
- 获取模型列表
- 归一化错误消息

它不直接参与聊天推理执行，不依赖 `AgentApp` 的现有 OpenRouter 主链路。这样可以先把配置校验和模型发现做好，后续如果需要把执行链路改成真正多 provider，只需要在已有 provider 配置基础上扩展，不会推倒这次工作。

### 2. provider 差异收敛在 probe client 内部

每个 provider 的接口路径、认证方式和模型枚举协议都有差异。本设计不做“单一 OpenAI 兼容协议适配全部 provider”的偷懒方案，而是在 probe 层为每家 provider 实现独立 client，把差异封装在边界内部。

### 3. 用户输出简洁，内部错误可诊断

CLI 默认展示简洁结果，例如：

- 认证失败
- 服务不可达
- 响应格式异常
- 模型列表为空

内部实现保留状态码和响应片段，便于测试断言和后续调试，但不默认在 CLI 中打印大段原始响应体。

## 架构设计

### 模块划分

新增 `mulehang.provider.probe` 包，职责如下：

- `ProviderProbeRequest`
  - 统一承载 `providerId`、`baseUrl`、`apiKey`、`headers`
- `RemoteModelSummary`
  - 表示远端返回的模型摘要
- `ProviderProbeResult`
  - 表示一次探测结果，包括成功状态、消息、模型列表和耗时
- `ProviderProbeClient`
  - 抽象单个 provider 的探测能力
- `ProviderProbeRegistry`
  - 负责根据 `providerId` 选择正确的 probe client
- `ProviderProbeService`
  - 聚合配置、provider 注册信息和 probe client，对 CLI 暴露统一接口

建议的数据结构如下：

```kotlin
data class ProviderProbeRequest(
    val providerId: String,
    val baseUrl: String,
    val apiKey: String,
    val headers: Map<String, String> = emptyMap()
)

data class RemoteModelSummary(
    val id: String,
    val displayName: String,
    val rawProvider: String,
    val supportsTools: Boolean? = null,
    val supportsStreaming: Boolean? = null
)

data class ProviderProbeResult(
    val providerId: String,
    val success: Boolean,
    val message: String,
    val models: List<RemoteModelSummary> = emptyList(),
    val latencyMs: Long
)

interface ProviderProbeClient {
    suspend fun testConnection(request: ProviderProbeRequest): ProviderProbeResult
    suspend fun listModels(request: ProviderProbeRequest): List<RemoteModelSummary>
}
```

### 与现有模块的关系

- `ProviderRegistry`
  - 继续负责“本地已知 provider 定义”和默认 `baseUrl`
  - 需要补齐首批 7 家 provider 的静态注册信息
- `ProviderGateway`
  - 继续负责运行时绑定解析
  - 不直接承担探测能力
- `ConfigLoader`
  - 保持现有 `apiKeyEnv -> apiKey` 注入逻辑
- `AgentRouting` / `AgentApp`
  - 新增 CLI 命令路由和启动时后台检查触发逻辑

## Provider 支持策略

### 首批 provider

首批支持以下 7 个 provider：

- `openai`
- `openrouter`
- `anthropic`
- `google`
- `deepseek`
- `mistral`
- `ollama`

`ProviderRegistry` 需要为这些 provider 补齐：

- `id`
- `displayName`
- `defaultBaseUrl`
- `supportsCustomBaseUrl = true`

### 模型获取策略

#### OpenAI / OpenRouter / DeepSeek / Mistral

优先使用远端 `GET /models` 枚举模型列表。成功时将远端返回结果归一化为 `RemoteModelSummary`。

#### Ollama

使用 `GET /api/tags` 获取本地模型标签列表，并转换为 `RemoteModelSummary`。

#### Anthropic / Google

第一版采用“连接探测优先，模型列表保底”的策略：

- `testConnection`
  - 使用最小合法请求验证 `baseUrl` 与 `apiKey` 是否可用
- `listModels`
  - 优先尝试 provider 官方或兼容端点
  - 如果该端点在当前配置下不可稳定依赖，则返回内置候选模型列表，并在 `message` 或 CLI 输出中明确标注“静态候选列表”

这样可以保证首批 7 家都进入统一的 probe 架构，而不会为了强行远端枚举把接口层做脏。后续如需升级为完全真实远端模型发现，只需替换对应 provider 的 probe client，不影响 CLI 命令或服务层接口。

## CLI 交互设计

新增如下命令：

- `:help`
  - 增加 provider 命令说明
- `:providers`
  - 列出受支持 provider、启用状态、是否已配置 `apiKey`、当前 `baseUrl`
- `:providers test <providerId>`
  - 测试单个 provider 连通性
- `:providers models <providerId>`
  - 测试单个 provider 并展示模型列表
- `:providers test-all`
  - 逐个探测所有已启用 provider，并输出汇总结果

### 输出规则

#### `:providers`

输出重点：

- provider 名称
- 是否已启用
- 是否已配置 `apiKey`
- 实际生效 `baseUrl`

#### `:providers test <providerId>`

输出重点：

- 成功或失败
- 失败原因摘要
- 耗时

#### `:providers models <providerId>`

输出重点：

- 连接是否可用
- 获取到的模型数量
- 每个模型的 `id`
- 如果是静态候选列表，明确标注

#### `:providers test-all`

输出重点：

- 每个 provider 的探测结果
- 失败项数量
- 是否建议用户执行更细粒度命令查看详情

## 启动时自动检查

CLI 启动时在首次提示符前触发一次非阻塞后台检查：

- 只检查 `enabled = true` 且基本配置满足要求的 provider
- 不阻塞 CLI 进入交互
- 成功时静默
- 失败时打印单行提示，不打断用户输入流程

建议提示风格：

```text
[系统] provider openai 连通性检查失败，使用 :providers test openai 查看详情。
```

自动检查不打印模型列表，也不缓存模型结果。用户显式执行 `:providers models <providerId>` 时，始终发起一次实时请求。

## 错误处理

### 用户可见错误分类

- `Unknown provider`
  - providerId 不在首批支持清单内
- `Missing apiKey`
  - 非 Ollama provider 缺少 `apiKey`
- `Invalid baseUrl`
  - `baseUrl` 为空、格式不合法或无法构造请求 URI
- `Authentication failed`
  - 401/403 类认证失败
- `Provider unreachable`
  - DNS、连接超时、拒绝连接
- `Unexpected provider response`
  - 返回结构不符合预期
- `No models returned`
  - 请求成功但模型列表为空

### 内部实现建议

- probe 层捕获底层异常并映射为统一结果
- 对状态码保留判定分支，便于测试断言
- 对原始响应体只截取少量片段用于调试，不直接打印全部内容

## 测试设计

本功能应按 TDD 实现，测试优先于生产代码。建议最少覆盖以下场景：

### Provider 注册与配置

- 首批 7 个 provider 已在 `ProviderRegistry` 中注册
- 每个 provider 都声明支持自定义 `baseUrl`

### Probe 服务层

- 单个 provider 探测成功
- 认证失败时返回统一错误消息
- `baseUrl` 不可达时返回统一错误消息
- provider 返回异常结构时返回统一错误消息
- 模型列表为空时给出明确结果

### CLI 路由

- `:providers` 能输出 provider 状态
- `:providers test <providerId>` 会调用正确的 probe 服务
- `:providers models <providerId>` 能展示模型列表
- `:providers test-all` 能输出汇总
- 未知命令或未知 provider 会给出明确提示

### 启动自动检查

- 自动检查不会阻塞 CLI
- 自动检查失败只打印提示，不终止程序

## 实施顺序建议

建议按以下顺序落地：

1. 扩展 `ProviderRegistry`，补齐首批 provider 元数据
2. 写 probe DTO 与服务层测试
3. 为各 provider 实现 probe client
4. 接入 CLI 命令解析与展示
5. 接入启动时后台自动检查
6. 补充帮助文案与回归测试

## 风险与后续演进

### 风险

- 各 provider 的模型枚举接口稳定性和认证要求不同，Anthropic / Google 第一版可能需要静态候选模型兜底
- 现有聊天执行链路仍然偏向 OpenRouter，用户可能误以为“能探测”就代表“已可直接用该 provider 聊天”

### 后续演进方向

- 将真正的聊天执行器改造成按 provider 选择不同 LLM provider 与请求客户端
- 增加模型能力元数据补全，例如工具调用、流式、推理能力
- 引入可选缓存层，减少频繁获取模型列表的请求成本
- 扩展到更多 Koog provider

## 成功标准

完成后，用户应能做到：

- 在 `mulehang-agent.json` 中配置首批支持 provider 的 `apiKey` 与 `baseUrl`
- 通过 CLI 独立命令验证某个 provider 是否可用
- 通过 CLI 查看该 provider 的可用模型或静态候选模型
- 在启动时收到非阻塞的 provider 健康检查反馈

