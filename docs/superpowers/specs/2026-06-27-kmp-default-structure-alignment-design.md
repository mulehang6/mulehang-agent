# 2026-06-27 KMP Default Structure Alignment Design

## 背景

当前仓库主线已经收敛为 `shared/` 与 `composeApp/` 两个模块，整体方向是 `Kotlin Multiplatform + Compose Multiplatform Desktop`，并且当前只聚焦 Windows Desktop。

不过，当前工程结构仍然保留了一部分旧模板痕迹：

1. 应用模块名称仍为 `composeApp`，与 JetBrains 2026 年新的 KMP 默认结构中的 `desktopApp` 不一致。
2. `composeApp` 当前使用 `kotlin("multiplatform")`，但实际上只承载桌面 JVM 应用入口。
3. `shared/` 仍然使用 `desktopMain` 与 `desktopTest` 源码集命名，而不是当前默认结构中的 `jvmMain` 与 `jvmTest`。
4. `composeApp/src/desktopMain` 与 `composeApp/src/desktopTest` 也仍然沿用旧命名，而不是新的 `src/main` 与 `src/test`。

JetBrains 在 2026 年新的 KMP 默认结构说明中，已经将“共享模块”和“各端应用模块”明确拆分为单职责布局。对于当前仓库来说，这不是功能性阻塞问题，但它已经开始带来两个实际成本：

1. 新加入的开发者在阅读工程时，需要额外理解当前结构为什么与最新模板不一致。
2. 后续参考官方文档、样例项目或 `demo1` 进行迁移时，需要不断做目录与模块名的脑内映射。

因此，本轮工作的目标不是“为了追模板而追模板”，而是把当前项目收敛到与最新 KMP 默认结构一致的工程布局，减少结构层面的认知噪音。

## 目标

本轮设计的目标如下：

1. 将当前工程结构对齐到 JetBrains 2026 新的 KMP 默认布局思路。
2. 将桌面应用模块从 `composeApp` 重命名为 `desktopApp`。
3. 将 `shared/` 中的 JVM 专属源码集从 `desktopMain` / `desktopTest` 迁移为 `jvmMain` / `jvmTest`。
4. 将桌面应用模块源码目录迁移为 `desktopApp/src/main` 与 `desktopApp/src/test`。
5. 保持当前项目仍然是 `desktop-only`，不引入 `webApp`。
6. 在不改变既有桌面功能行为的前提下完成结构迁移。

## 非目标

本轮不做以下内容：

1. 不新增 `webApp` 模块。
2. 不重做当前业务架构、包结构或状态模型。
3. 不顺手引入与结构迁移无直接关系的新依赖或新功能。
4. 不强行把整个仓库一比一改造成官方模板示例，包括不以新增 `libs.versions.toml` 或统一版本目录为本轮硬目标。
5. 不重写所有历史文档，只修正会误导当前主线的模块名与路径引用。
6. 不改变当前桌面端 UI、聊天流程、配置读取和工具交互行为。

## 方案对比

### 方案 1：完全照搬 `demo1`

将仓库尽可能改成和 `demo1` 一致，包括模块命名、源码目录、Gradle 组织方式以及版本目录习惯。

优点：

1. 与官方样例和 `demo1` 的相似度最高。
2. 后续参考样例时几乎不需要做结构映射。

缺点：

1. 变更面会膨胀到工程风格迁移，而不仅仅是结构迁移。
2. 很多差异只影响外观，不提升当前项目可维护性。

### 方案 2：结构对齐，构建保守

将模块职责、模块命名、源码目录和关键 Gradle 组织对齐到新的默认结构，但不把本轮扩展成模板化重建。

优点：

1. 可以达到“布局严格对齐”的主要目标。
2. 变更范围可控，重点集中在结构，而不是风格。
3. 更符合当前仓库已经有大量业务代码和文档沉淀的现实。

缺点：

1. 不会与官方模板做到字节级一致。
2. 仍然会保留一部分项目特有的 Gradle 与文档组织方式。

### 方案 3：只改表面名称

仅把 `composeApp` 改名为 `desktopApp`，其余结构尽量不动。

优点：

1. 风险最小。

缺点：

1. 无法真正解决源码集命名与模块职责不清的问题。
2. 看起来接近新结构，实际上仍然保留旧布局核心差异。

## 结论

本轮采用方案 2：结构对齐，构建保守。

原因如下：

1. 用户明确要求严格参考 `demo1` 的最新 KMP 布局。
2. 当前项目没有引入 Android，也没有引入 Web，因此只需要对齐与 `desktop-only` 相关的那部分结构。
3. 对当前仓库而言，真正有价值的是模块职责与源码集布局的一致性，而不是把整个工程重建成模板样板。

## 目标结构

重构完成后的主线结构如下：

```text
desktopApp/
  build.gradle.kts
  src/
    main/
      kotlin/
      resources/
    test/
      kotlin/

shared/
  build.gradle.kts
  src/
    commonMain/
      kotlin/
    commonTest/
      kotlin/
    jvmMain/
      kotlin/
    jvmTest/
      kotlin/
```

根工程继续保持当前双模块主线：

```text
settings.gradle.kts
build.gradle.kts
desktopApp/
shared/
docs/
```

## 模块职责

### desktopApp

`desktopApp/` 负责：

1. 桌面应用入口。
2. Compose Desktop UI。
3. 窗口生命周期。
4. 与桌面应用直接相关的测试与资源。

`desktopApp/` 不负责：

1. 配置模型定义。
2. Koog 适配逻辑。
3. 共享状态模型与应用用例。

### shared

`shared/` 负责：

1. `commonMain` 中的共享配置、状态、用例与 agent 抽象。
2. `jvmMain` 中的 JVM 专属实现，例如桌面端文件路径、JVM 客户端或本地存储实现。
3. `commonTest` 与 `jvmTest` 中的对应测试。

## 迁移范围

本轮迁移仅覆盖工程结构和会被结构变化直接影响的引用。

### 必须修改

1. `composeApp/` 重命名为 `desktopApp/`。
2. `composeApp/src/desktopMain` 迁移为 `desktopApp/src/main`。
3. `composeApp/src/desktopTest` 迁移为 `desktopApp/src/test`。
4. `shared/src/desktopMain` 迁移为 `shared/src/jvmMain`。
5. `shared/src/desktopTest` 迁移为 `shared/src/jvmTest`。
6. `settings.gradle.kts` 中的模块声明与相关引用更新为 `desktopApp`。
7. `desktopApp/build.gradle.kts` 调整为纯桌面应用模块，而不是继续作为单目标 KMP 应用模块。
8. `shared/build.gradle.kts` 调整为使用 `jvm()` 以及 `jvmMain` / `jvmTest` 命名。
9. 所有因这次重命名而失效的项目内引用必须同步修复。

### 可以修改

1. 根 `build.gradle.kts` 中与模块命名强绑定的配置。
2. README、AGENTS 和当前主线文档中的路径说明。
3. 必要的 IDE 配置或测试路径引用，如果它们因为目录变更而失效。

### 不应修改

1. 与本轮结构迁移无关的业务逻辑。
2. 与本轮结构迁移无关的 UI 表现。
3. 与本轮结构迁移无关的 provider、Koog 或工具执行语义。

## Gradle 对齐原则

### 根工程

根工程继续承担统一插件版本和仓库声明职责，不要求本轮强制引入版本目录系统。

### desktopApp

`desktopApp` 的 Gradle 目标形态应与 `demo1` 的桌面模块保持一致：

1. 这是一个纯 JVM 应用模块。
2. 它依赖 `shared`。
3. 它负责 Compose Desktop 打包配置。

这意味着本轮应从“单目标 multiplatform 应用模块”收敛为“桌面应用模块”，避免继续让 `composeApp` 在语义上假装自己是共享模块。

### shared

`shared` 继续是 Kotlin Multiplatform 模块，但只保留当前仓库实际使用的目标：

1. `commonMain`
2. `commonTest`
3. `jvmMain`
4. `jvmTest`

本轮不因为官方新结构就补出 `js`、`wasmJs` 或 `webMain`。

## 文档与路径同步规则

本轮不要求重写全部历史文档，但以下内容必须同步：

1. 当前入口文档中对主线模块名称的说明。
2. 当前仍被视为有效主线参考的 spec/plan 中涉及 `composeApp` 的核心路径描述。
3. 当前测试、资源或日志路径中因模块名变化而失效的直接引用。

对于只记录历史过程、不会误导当前主线的旧文档，可保留原文，不做全面改写。

## 风险

### 1. 源码集绑定风险

`desktopMain` / `desktopTest` 迁移到 `jvmMain` / `jvmTest` 后，最容易出问题的是：

1. Gradle 源码集绑定。
2. JVM 侧依赖声明位置。
3. 测试发现与执行。

### 2. 模块重命名风险

`composeApp` 改名为 `desktopApp` 后，最容易遗漏的是：

1. `project(...)` 依赖。
2. 文档中的路径引用。
3. 本地日志与资源相关路径。
4. IDEA 运行配置或测试路径。

### 3. 活跃桌面功能回归风险

最近多次提交集中在桌面 UI、工作区选择、工具交互和聊天体验上，因此本轮迁移的原则必须是：

1. 结构变更优先。
2. 行为保持不变。
3. 不借机改写业务逻辑。

## 验收标准

本轮完成后，至少满足以下条件：

1. 工程主线结构变为 `shared + desktopApp`。
2. `shared` 使用 `commonMain` / `commonTest` / `jvmMain` / `jvmTest`。
3. `desktopApp` 使用 `src/main` 与 `src/test`。
4. `desktopApp` 成为纯桌面应用模块，继续负责桌面打包与入口。
5. 项目能够完成一次完整构建。
6. 项目测试能够通过。
7. 改动后的关键文件在 IDEA 检查中没有明显错误。
8. 不新增 `webApp`，不引入新的产品行为变化。

## 实施策略

建议的实施顺序如下：

1. 先修改模块声明与 Gradle 结构。
2. 再移动目录与源码集路径。
3. 随后修复所有直接受影响的项目内引用。
4. 最后执行构建、测试与 IDEA 问题检查。

这样做的原因是：先让构建系统知道新的模块与源码集名字，再去迁移文件，能减少中间态歧义。

## 说明

根据当前仓库协作规则，本设计文档可以先写入仓库，但本轮不包含自动提交步骤；只有在用户明确允许时才提交。
