# 模型能力提供者设计

## 目标

将模型能力规则从中央 `ModelCapabilitiesResolver` 拆出：官方 DeepSeek 与 OpenAI 模型能力由代码维护；用户配置的 OpenAI-compatible 模型能力由 `settings.json` 显式声明。

## 范围

- 支持 reasoning effort、默认 reasoning effort 和模型上下文限制的能力解析。
- 移除中央 resolver 中的厂商和模型名称判断。
- 维持现有 UI、会话状态和发送链路消费 `ModelCapabilities` 的方式。

不在本次范围内：自动探测第三方 API 能力、为环境变量新增 reasoning 配置项、修改 DeepSeek 请求协议。

## 运行时架构

新增 `ModelCapabilityProvider` 接口：

```kotlin
interface ModelCapabilityProvider {
    fun resolve(profile: ConfigProfile): ModelCapabilities?
}
```

`ModelCapabilitiesResolver` 仅按以下顺序询问 provider，并返回第一个非空结果：

1. `ConfiguredModelCapabilityProvider`
2. `DeepSeekModelCapabilityProvider`
3. `OpenAIModelCapabilityProvider`
4. 无匹配时的空能力

显式配置拥有最高优先级，因此用户可以为任何模型覆盖代码内置能力。没有显式配置时：

- `DeepSeekModelCapabilityProvider` 识别模型名以 `deepseek` 开头或 base URL 含 `deepseek` 的 OpenAI chat-completions profile，提供 `high`、`max` 和现有的 DeepSeek 默认限制。
- `OpenAIModelCapabilityProvider` 仅识别 `https://api.openai.com` 的 OpenAI Responses profile 中的已知 GPT/Codex reasoning 模型，提供 `low`、`medium`、`high`。
- 任意用户自定义 OpenAI-compatible profile 不按模型名称推测能力；未配置能力时不显示 reasoning 控件，也不会携带 `reasoning_effort`。

`ModelCapabilities` 保持为 UI 与发送层的唯一运行时模型；其默认档位规则不变：优先 `medium`，否则使用首个可选档位。

## settings.json

在 `models[]` 的 `ModelProfile` 中新增以下可选字段：

```json
{
  "id": "custom-reasoning-model",
  "reasoningEfforts": ["low", "medium", "high"],
  "defaultReasoningEffort": "medium"
}
```

语义如下：

- `reasoningEfforts` 缺失：不使用配置 provider，由代码 provider 或空能力决定。
- `reasoningEfforts: []`：显式声明模型不支持 reasoning。
- `reasoningEfforts` 可选值仅为 `low`、`medium`、`high`、`max`，并保留声明顺序。
- `defaultReasoningEffort` 可省略；省略时使用 `ModelCapabilities` 的默认规则。
- 显式 `defaultReasoningEffort` 必须属于 `reasoningEfforts`；非法值或不在列表中的值应在配置加载时以含 provider/model 标识的错误拒绝。

`ModelProfile` 中的用户输入在 `SettingsMerger` 展平时传递给 `ConfigProfile`，供 `ConfiguredModelCapabilityProvider` 使用。环境变量覆盖模型名时没有对应的能力字段，因此不携带 JSON 模型能力。

## 测试与验收

- DeepSeek provider 单元测试：匹配条件、`high/max` 和默认限制。
- OpenAI provider 单元测试：仅官方 OpenAI Responses 的 GPT/Codex reasoning 模型匹配；自定义 URL 不匹配。
- 配置合并测试：`reasoningEfforts`、显式默认值及空列表从 JSON 传递到最终 profile。
- 配置能力 provider 测试：显式配置覆盖代码默认能力，缺失配置返回不匹配。
- 配置校验测试：未知档位及默认档位不在列表中均失败，并包含模型标识。
- 既有聊天状态回归测试：DeepSeek 初始会话仍展示 `high`，切换模型后不会显示不受支持的档位。

## 兼容性

已有 settings 文件不包含新增字段，加载结果保持现状：DeepSeek 与官方 OpenAI 的内置能力继续生效，其他自定义模型不展示 reasoning。
