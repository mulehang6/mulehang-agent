# Anthropic Compatible Model Support Design

## Goal

让任何使用 `providerType: "anthropic"` 的兼容服务端能够使用配置中声明的任意模型 ID，而不受 Koog 内置 Claude 模型白名单限制。

## Background

当前桌面端为 `ProviderType.ANTHROPIC` 创建 `AnthropicLLMClient` 时只传入 `baseUrl`。Koog 会在序列化请求前使用 `AnthropicClientSettings.modelVersionsMap` 将 `LLModel` 查找为 HTTP 请求的 `model` 字段；其默认映射仅包含内置 Claude 模型。

因此，配置为 `deepseek-v4-flash`、端点为 `https://api.deepseek.com/anthropic` 的 Anthropic 兼容 profile 会在网络请求前失败，并报 `Unsupported model`。

## Design

在 `buildPromptExecutor` 的 Anthropic 分支中，为创建的 `AnthropicClientSettings` 提供一项自定义 `modelVersionsMap`：

- 键：由当前 `ConfigProfile` 构造的 `LLModel`；
- 值：未修改的 `ConfigProfile.model` 字符串；
- 保留现有 `baseUrl`、认证头、`anthropic-version`、消息路径、流式处理和推理参数映射。

该映射仅影响当前 profile 创建的客户端。由于运行时请求也从同一 profile 构造等价的 `LLModel`，Koog 能找到映射并在 Anthropic 兼容请求体中发送配置的模型 ID。

## Alternatives Considered

1. 将 DeepSeek Anthropic profile 改为 OpenAI Chat Completions：会改变用户明确选择的协议，拒绝。
2. 仅硬编码 `deepseek-v4-flash` 和 `deepseek-v4-pro`：无法支持其他 Anthropic 兼容服务端与其自定义模型 ID，拒绝。
3. 使用当前 profile 构造单项映射：改动最小，适用于所有 Anthropic 兼容端点，采用。

## Error Handling

不新增网络层兜底。若兼容服务端不支持某个模型、工具调用或推理字段，服务端响应会沿现有 Anthropic 错误路径上报。这里仅消除 Koog 在发送请求前产生的错误模型白名单限制。

## Testing

新增 JVM 测试，验证 Anthropic 兼容 profile 的 `AnthropicClientSettings` 包含：

- 与运行时 profile 等价的 `LLModel` 键；
- 值为原始模型 ID，例如 `deepseek-v4-flash`；
- 未改变 `baseUrl`。

保留已有直连 Claude profile 的行为，确保默认 Anthropic 模型仍可映射。
