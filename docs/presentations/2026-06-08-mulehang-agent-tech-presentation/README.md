# mulehang-agent 技术实现汇报

这套材料用于小组作业展示，包含：

1. `01-cover.html` 到 `09-video-placeholder.html`
2. `theme.css` 统一样式
3. `generate-pptx.cjs` 导出脚本

## 页面结构

1. 封面
2. 项目背景与目标
3. 总体技术路线
4. 系统架构设计
5. Provider 与模型发现
6. Agent 与能力接入
7. CLI 流式输出
8. 当前成果与验证方式
9. 演示视频占位页

## 需要你补的内容

封面页右侧仍保留了以下占位：

- 课程名称
- 小组名称
- 成员与学号

## 导出命令

在 PowerShell 中执行：

```powershell
node .\docs\presentations\2026-06-08-mulehang-agent-tech-presentation\generate-pptx.cjs
```

导出结果默认写到：

`docs/presentations/2026-06-08-mulehang-agent-tech-presentation/output/mulehang-agent-technical-presentation.pptx`
