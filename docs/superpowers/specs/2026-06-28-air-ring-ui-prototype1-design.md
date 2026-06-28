# 2026-06-28 Air Ring UI Prototype 1 Design

## 背景

用户要求在 `agent-ui-prototype1/` 中制作一版接近 JetBrains Air 截图的 React 原型，并明确增加以下约束：

1. 页面需要先以给定截图为视觉锚点，优先做到接近 `1:1` 的外观和比例。
2. 原型不能只做单一静态页面，必须支持切换不同任务状态。
3. 整页尽量全部使用 Ring UI 组件和 Ring UI 样式体系。
4. 顶栏、正文、侧栏、右侧工具轨、composer，以及图标都应保持在 Ring UI 体系内，不引入另一套视觉语言。
5. 所有可见容器和控件都要统一采用圆角风格。

## 目标

本次原型的目标如下：

1. 在 `agent-ui-prototype1/` 中交付一版可运行的 React 原型页面。
2. 页面视觉与参考截图在布局、密度、层级、深色质感上保持高度接近。
3. 左侧任务列表切换后，中间标题、正文、计划卡片和 composer 状态可以联动切换。
4. 底部 composer 保持此前已经确认的交互约束：
   - 顺序固定为 `+ / Provider / Model / Ask Permission / Send`
   - `Thinking levels` 不在底栏常驻
   - `Thinking levels` 只在 `Model` 下拉对应模型项中显示
5. 页面仍然保持原型定位，不接真实后端，不引入复杂状态机。

## 非目标

本次不做以下内容：

1. 不接入真实聊天后端。
2. 不实现真正的任务执行、流式输出或审批流程。
3. 不实现完整可配置的多页面信息架构。
4. 不为了“通用性”提前抽象出复杂组件体系或设计系统包装层。
5. 不引入 Ring UI 之外的新 UI 依赖。

## 视觉与组件约束

### Ring UI 约束

1. 全局使用 `@jetbrains/ring-ui-built/components/style.css`。
2. 优先使用 Ring UI 现成组件构建页面：
   - `Button`
   - `Input`
   - `Select`
   - `Toggle`
   - `Header`
   - `Panel`
   - `Line`
   - `ScrollableSection`
   - `Tooltip`
   - `Icon`
   - 其他确有必要的 Ring UI 页面级或基础组件
3. 页面图标也优先使用 Ring UI 支持的图标接入方式，不补自定义 SVG。
4. 允许补少量自定义 CSS，只用于布局、密度、尺寸、边距、边框和圆角校准，不重新定义另一套视觉 token。

### 圆角约束

1. 所有可见容器和控件统一采用圆角风格。
2. 圆角应尽量基于 Ring UI token 做统一覆盖，而不是每个组件零散硬编码。
3. 不允许出现页面内一部分是硬直角、一部分是圆角的混合状态。

## 信息架构

页面固定拆为五个可见区域：

1. 顶部栏
2. 左侧任务栏
3. 中间正文区
4. 底部计划卡片与 composer 区
5. 右侧竖向工具轨

### 顶部栏

顶部栏负责表现参考图中的以下信息：

1. 左上产品/导航区
2. 当前 workspace 或路径 breadcrumb
3. 当前任务标题
4. 右上操作图标

它主要承担“外观对齐”和“产品氛围建立”的作用，不承担真实导航逻辑。

### 左侧任务栏

左侧任务栏负责：

1. 展示搜索框
2. 展示 `New Task`
3. 展示 `Running` 区任务列表
4. 展示 `Done` 区任务列表
5. 允许点击任务，切换当前显示内容

任务项应包含：

1. 标题
2. 副文案
3. 状态视觉
4. 增减行数等摘要信息

### 中间正文区

正文区负责呈现当前任务的：

1. 用户输入卡片
2. 当前回答正文
3. 次级状态文本，例如 `Updating plan...`

首屏默认状态应尽量贴近参考图中的内容排布与留白比例。

### 底部计划卡片与 Composer

底部区域由两层组成：

1. 上层计划卡片，展示当前 task 的 plan 列表
2. 下层 composer，负责展示输入与发送相关控件

Composer 仍保持既定交互规则：

1. 从左到右固定为 `+ / Provider / Model / Ask Permission / Send`
2. `Thinking levels` 只出现在 `Model` 下拉的对应模型项里
3. 底栏允许只回显当前选中的思考档短标签，但不展开成独立大控件

### 右侧工具轨

右侧使用 Ring UI 图标和按钮风格做一列垂直工具入口，承担以下作用：

1. 复刻参考图的右侧密集操作轨
2. 增强 Air 风格的工作台感
3. 作为可点击但无真实业务逻辑的原型入口

## 数据与状态设计

本原型采用本地 mock 数据驱动，不接服务端。

至少维护三类状态：

1. 当前选中的 task
2. 当前 composer 的 provider / model / thinking / ask permission / text
3. 当前页面所展示的正文内容、计划项和任务列表状态

建议将页面主数据整理为一份任务数组，每个任务包含：

1. 任务 id
2. 所属分组，例如 `running` 或 `done`
3. 标题与副文案
4. 统计信息
5. 正文标题
6. 正文段落
7. 计划项列表
8. composer 默认值或提示态

这样点击任务时，只需切换当前 task id，即可联动刷新页面主要内容。

## 实现策略

采用“截图优先的通用原型”策略：

1. 先把参考截图这一刻的页面状态作为默认首屏完成复刻。
2. 再把页面内容抽成最小数据驱动结构，支持切换多个任务。
3. 不先抽象“产品壳”或“工作台框架”，避免为了通用性牺牲截图相似度。

## 文件范围

预计主要修改文件如下：

1. `agent-ui-prototype1/src/App.tsx`
2. `agent-ui-prototype1/src/Composer.tsx`
3. `agent-ui-prototype1/src/ModelSelect.tsx`
4. `agent-ui-prototype1/src/data.ts`
5. `agent-ui-prototype1/src/index.css`
6. 如有必要，调整 `agent-ui-prototype1/src/main.tsx`

如果现有 `icons.tsx` 仍依赖自定义 SVG，则应尽量改为 Ring UI 图标接入方式，或删除不再需要的自定义图标实现。

## 验证方式

完成实现后至少执行：

```powershell
pnpm --dir agent-ui-prototype1 build
```

如存在 lint 配置且代价可控，再执行：

```powershell
pnpm --dir agent-ui-prototype1 lint
```

此外还应检查修改文件的问题状态，确保不存在明显类型错误或未使用导入。

## 成功标准

完成后应满足：

1. 原型可以构建通过。
2. 页面整体外观明显接近参考图。
3. 左侧任务可以切换，并驱动中间内容与底部计划区变化。
4. composer 顺序与交互满足既有约束。
5. 整页主要可见元素保持 Ring UI 风格并统一圆角。

## Self-Review

1. 未保留 `TODO`、`TBD` 等占位语。
2. 目标、非目标、组件约束、交互边界和验证方式已明确。
3. “尽量全部使用 Ring UI” 与 “允许少量布局校准 CSS” 之间的边界已经写明，避免实现时过度自由发挥。
