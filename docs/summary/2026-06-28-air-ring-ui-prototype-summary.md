# 2026-06-28 Air Ring UI Prototype Summary

## 背景

用户要求把当前桌面聊天产品的 UI 改成接近 JetBrains Air 的视觉和信息架构，并额外在一个独立的 React 原型项目里，验证一版可交互的输入框/底栏交互原型，供后续继续打磨。

这次沟通里有两条线：

1. `desktopApp/` 的 Compose Desktop 正式产品 UI 方向确认
2. `agent-ui-prototype1/` 的 React 可交互原型验证

## 用户已经明确确认的需求

### 1. 整体 UI 方向

- 目标不是“只换皮”，而是 `视觉 + 信息架构 1:1` 地贴近 JetBrains Air
- 左侧主导航按 `workspace -> tasks` 组织
- 顶部、右侧工具栏、中央 transcript、底部 composer 都要往 Air 靠
- 文案不要求英文产品化，`保留中文` 即可

### 2. 输入框区域（当前最重要）

用户重点盯的是底部 composer 一排控件的交互和质感。

已经明确的正确交互：

- 左侧顺序必须是：`+`、`Provider`、`Model`
- `Provider` 先选 provider
- `Model` 再展示当前 provider 下的模型列表
- `Thinking levels` **不是**底栏常驻控件
- `Thinking levels` 必须是“放到对应模型上才显示”
- 更具体地说：在 `Model` 下拉里，只有某个模型项被 hover/聚焦/选中时，才显示这个模型支持的几档思考等级
- 思考等级只显示几档短标签，例如 `Fast / Balanced / Deep`
- 不要多余文案，尤其不要额外显示大块说明、模型说明卡、悬浮大面板里的冗余内容

### 3. 质感要求

- 用户明确要求：`必须跟 Ring UI 做的一模一样`
- 但用户对“直接套 Ring 组件然后布局变形”非常不满意
- 用户对当前几次原型的核心不满是：`质感不对`、`比例不对`、`层级不对`、`像坏掉的 demo`
- 尤其反感把 Air 外壳和 Ring 控件“硬拼”后产生的错位和挤压

## 已踩过的坑

下面这些方向已经被用户否定，另一个 AI 接手时不要再重复：

1. 不要把 `Thinking levels` 平铺在输入框底栏
2. 不要把 `Thinking levels` 做成独立的大 popup 卡片，里面再显示模型名和解释文案
3. 不要在模型列表里塞太多额外信息，比如 `family`、`blurb`、大段 note
4. 不要直接把 Ring UI 的 `Button/ButtonGroup` 生硬塞进 Air composer 外壳，容易导致按钮宽度塌陷、文字重叠、整排挤坏
5. 不要做“解释型草图”式页面，用户要的是更像真实 JetBrains 产品的成品感

## 当前代码状态（回滚后）

### 1. React 原型项目位置

项目路径：

- `agent-ui-prototype1/`

### 2. 当前目录现状

`agent-ui-prototype1` 仍然存在，但已经回滚到接近空模板的状态。

当前可以确认的事实：

- `agent-ui-prototype1/src/` 里现在只剩 `assets/`
- 之前生成的这些源码文件现在都不存在：
  - `src/App.tsx`
  - `src/App.css`
  - `src/index.css`
  - `src/main.tsx`
- `agent-ui-prototype1/README.md` 是 Vite 默认模板说明

所以，不能再假设“项目里已经有一版可继续打磨的 React 原型源码”。

### 3. 仍然保留的依赖

`agent-ui-prototype1/package.json` 里仍然保留：

- `react`
- `react-dom`
- `@jetbrains/ring-ui-built`

这意味着后续如果要重做原型，可以直接基于这个依赖起步，不需要重新安装 Ring UI。

### 4. 版本控制状态

当前 `git status` 显示：

- `agent-ui-prototype1/` 是未跟踪目录
- 这份 summary 文件也是未跟踪文件

所以接手者不能假设这个原型项目已经稳定纳入仓库主线。

## 当前推荐的接手策略

现在最稳的做法不是“继续修补之前那版原型”，因为那版源码已经被回滚掉了。

更合适的接手方式是：

1. 把 `agent-ui-prototype1` 视为一个新的空 React 原型入口
2. 只先实现 composer 这一块，不要一开始就重做整页 Air 布局
3. 先把 `+ / Provider / Model / Ask Permission / Send` 和 `Model` 下拉内部的 thinking levels 交互做准
4. `Thinking levels` 只在对应模型项上显示，不做底栏常驻，不做独立大说明弹层

## 已验证状态

这次重新检查后，只能确认：

- `agent-ui-prototype1/package.json` 里还有 `@jetbrains/ring-ui-built`
- `agent-ui-prototype1/src/` 当前没有可继续使用的原型源码

之前关于“tsc 通过”“vite build 通过”的描述，已经不应该再当作当前状态引用。

## 给接手 AI 的一句话摘要

你要接手的是一个**已经回滚过的空 React 原型项目**：`agent-ui-prototype1` 里目前没有可继续改的 UI 源码，但依赖里还保留了 `@jetbrains/ring-ui-built`。真实需求是：先单独做 composer 区域，底栏顺序是 `+ / Provider / Model / Ask Permission / Send`，而 `Thinking levels` 只能出现在 `Model` 下拉里的对应模型项上，不能平铺在输入框底栏，也不要做带冗余说明的大弹层。
