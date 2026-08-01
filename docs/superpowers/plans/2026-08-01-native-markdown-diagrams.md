# 原生 Markdown 与图表渲染实施计划

## 目标

在 Compose Desktop 内原生展示 Agent 回复，覆盖常用 Markdown/GFM 扩展、数学公式、受控 HTML
语义标签和 Mermaid/PlantUML 图表；不嵌入浏览器内核，也不执行回复中的 JavaScript 或 CSS。

## 已完成且必须保留的修复

- DeepSeek 流式分片保留空白，避免 `# ` 和换行被丢弃。
- 新的回复、思考和工具事件到达时，流式 assistant 项保持在时间线末尾。
- `say_to_user` 工具已移除；可见答复走普通 assistant 文本事件。
- 每轮结束后写入完整 JSONL 运行记录，而非逐 token 记录。

## 实施步骤

1. 为共享 Agent 增加唯一的基础 system prompt，并以测试锁定：直接答复、正确 Markdown
   空白、图表代码围栏约定和不再调用已删除工具。
2. 为桌面渲染定义最小的显示策略：流式阶段只展示代码，回复完成后才把完整的
   `mermaid`、`plantuml`/`puml`、`dot` 围栏交给图表组件，避免不完整图反复布局。
3. 受控接入纯 Kotlin/Compose 的 Markdown renderer，并执行 JVM 编译验证。该候选提供
   CommonMark/GFM、表格、任务列表、脚注、定义列表、公式、代码高亮和原生图表；若与
   当前 Kotlin/Compose 版本不兼容，则保留成熟 Markdown renderer 并将 PlantUML SVG
   renderer 作为 JVM 独立回退。
4. 为正文、链接、代码块和图表容器提供当前桌面主题的显式前景/背景色，修复暗色主题的
   黑字和低对比度问题。
5. 用单元测试锁定策略及 system prompt，用受影响模块的测试与 `:desktopApp:compileKotlin`
   验证依赖/API；不启动桌面应用。

## 安全与性能边界

- HTML 仅映射安全的文本语义和结构，不运行脚本、内联事件处理器或任意 CSS。
- 远程图片和链接保持显式、可控；渲染器不会代表用户发起外部导航。
- 图表仅对已完成的 fenced code block 渲染；解析/渲染失败时展示原始代码块，回复不丢失。
- 不引入 JCEF、Chromium 或 Node.js 运行时。
