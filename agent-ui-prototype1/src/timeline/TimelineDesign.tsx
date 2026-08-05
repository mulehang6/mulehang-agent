import { useState, type ReactNode } from 'react'
import Icon from '@jetbrains/ring-ui-built/components/icon/icon.js'
import sparkleIcon from '@jetbrains/icons/sparkle'
import searchIcon from '@jetbrains/icons/search'
import terminalIcon from '@jetbrains/icons/terminal'
import pencilIcon from '@jetbrains/icons/pencil'
import fileIcon from '@jetbrains/icons/file'

/**
 * Agent 执行时间线 · 设计稿
 *
 * 两个变体：
 * - 变体 A「轮次式」：思考收在轮次开头（可折叠一行），工具调用按组收起，
 *   正文正常输出 —— 参考 JetBrains ACP / Air 的执行区。
 * - 变体 B「交替式」：思考与工具交替出现，工具不分组直接展示 —— 参考 kilo。
 *
 * 设计原则（简约）：
 * - 全部小字体系（12–13.5px），灰阶 + 少量强调色，无卡片背景。
 * - "正在进行的事"是唯一醒目的：蓝色脉冲点 + 三点动画。
 * - "已完成的事"安静缩起：思考一行、工具一组，点击才展开。
 * - 展开/收起 180ms ease-out，其余动效 ≤160ms。
 */

type ToolKind = 'search' | 'terminal' | 'edit' | 'read'

interface ToolEvent {
  kind: ToolKind
  name: string
  input: string
  status: 'running' | 'done' | 'failed'
  durationSec?: number
  output?: string
}

interface ReasoningEvent {
  live: boolean
  durationSec?: number
  text: string
}

type Entry =
  | { type: 'user'; id: string; content: string }
  | { type: 'reasoning'; id: string; data: ReasoningEvent }
  | { type: 'tool'; id: string; data: ToolEvent }
  | { type: 'text'; id: string; content: string; live?: boolean }

const TOOL_ICONS: Record<ToolKind, string> = {
  search: searchIcon,
  terminal: terminalIcon,
  edit: pencilIcon,
  read: fileIcon,
}

/** 一轮已完成 + 第二轮进行中的演示数据。 */
const DEMO_ENTRIES: Entry[] = [
  {
    type: 'user',
    id: 'u1',
    content: '帮我看一下登录模块的 token 刷新逻辑，为什么 401 会反复出现？',
  },
  {
    type: 'reasoning',
    id: 'r1',
    data: {
      live: false,
      durationSec: 18,
      text: '用户反馈 401 反复出现，可能的原因：1) refresh token 过期后没有重试队列；2) 并发请求同时触发刷新，产生 token 竞态；3) 刷新后 Authorization header 没有同步更新。先看 AuthService 的刷新实现，再确认请求拦截器的行为。',
    },
  },
  {
    type: 'tool',
    id: 't1',
    data: { kind: 'search', name: 'search_in_files', input: '"token refresh"', status: 'done', durationSec: 0.4 },
  },
  {
    type: 'tool',
    id: 't2',
    data: { kind: 'terminal', name: 'run_powershell', input: 'Get-Content src/auth/AuthService.kt', status: 'done', durationSec: 2.1 },
  },
  {
    type: 'tool',
    id: 't3',
    data: { kind: 'read', name: 'read_file', input: 'src/auth/AuthService.kt', status: 'done', durationSec: 0.3 },
  },
  {
    type: 'tool',
    id: 't5',
    data: {
      kind: 'edit',
      name: 'edit_file',
      input: 'src/auth/HttpClient.kt',
      status: 'failed',
      durationSec: 0.2,
      output: 'Error: no writable file handle, file is read-only (chmod 444)',
    },
  },
  {
    type: 'text',
    id: 'm1',
    content: '找到原因了。AuthService.refresh() 刷新后只更新了存储里的 token，没有同步更新内存中的 Authorization header，所以刷新后的第一个请求仍然带着旧 token 发出，服务端返回 401。\n\n另外请求拦截器里有一个并发缺口：两个请求同时过期时都会触发刷新，第二次刷新会覆盖第一次的 token，导致其中一边失效。',
  },
  {
    type: 'reasoning',
    id: 'r2',
    data: { live: true, text: '' },
  },
  {
    type: 'tool',
    id: 't4',
    data: { kind: 'edit', name: 'edit_file', input: 'src/auth/AuthService.kt', status: 'running' },
  },
  {
    type: 'text',
    id: 'm2',
    content: '正在应用修复：refresh 成功后同步更新 header，并给刷新流程加单飞（single-flight）保护……',
    live: true,
  },
]

/** 高度过渡折叠容器：0fr→1fr，180ms ease-out。 */
function Collapse({ open, children }: { open: boolean; children: ReactNode }) {
  return (
    <div className={`tl-collapse ${open ? 'tl-collapse-open' : ''}`}>
      <div className="tl-collapse-inner">{children}</div>
    </div>
  )
}

/** 用户消息气泡。 */
function UserBubble({ content }: { content: string }) {
  return <div className="tl-user">{content}</div>
}

/** 正文段落；流式输出时带闪烁光标。 */
function TextBlock({ content, live }: { content: string; live?: boolean }) {
  return (
    <div className="tl-text">
      <p>{content}</p>
      {live && <span className="tl-caret" aria-hidden="true" />}
    </div>
  )
}

/** 进行中三点脉冲（accent 色）。 */
function RunningDots() {
  return (
    <span className="tl-dots" aria-hidden="true">
      <i />
      <i />
      <i />
    </span>
  )
}

/** 思考行：一行可折叠摘要；进行中时显示"正在思考 + 三点"。 */
function ThinkingLine({ event }: { event: ReasoningEvent }) {
  const [open, setOpen] = useState(false)
  if (event.live) {
    return (
      <div className="tl-row tl-thinking">
        <Icon glyph={sparkleIcon} size={14} />
        <span className="tl-label">正在思考</span>
        <RunningDots />
      </div>
    )
  }
  return (
    <div className="tl-thinking-block">
      <div className="tl-row tl-collapsible" onClick={() => setOpen((v) => !v)} role="button" tabIndex={0}>
        <Icon glyph={sparkleIcon} size={14} />
        <span className="tl-label">思考 {event.durationSec} 秒</span>
        <span className={`tl-chevron ${open ? 'tl-chevron-open' : ''}`} aria-hidden="true" />
      </div>
      <Collapse open={open}>
        <p className="tl-reasoning-text">{event.text}</p>
      </Collapse>
    </div>
  )
}

/** 单个工具行：类型图标 + 名称 + 输入摘要 + 状态点/耗时；失败可展开输出。 */
function ToolLine({ tool }: { tool: ToolEvent }) {
  const [open, setOpen] = useState(false)
  const hasOutput = tool.output != null && tool.output.length > 0
  const clickable = hasOutput || tool.status === 'failed'
  return (
    <div className="tl-tool-line">
      <div
        className={`tl-row tl-collapsible ${tool.status === 'running' ? 'tl-tool-running' : ''} ${
          tool.status === 'failed' ? 'tl-tool-failed' : ''
        }`}
        onClick={() => clickable && setOpen((v) => !v)}
        role="button"
        tabIndex={clickable ? 0 : -1}
      >
        <Icon glyph={TOOL_ICONS[tool.kind]} size={13} />
        <span className="tl-tool-name">{tool.name}</span>
        <span className="tl-tool-input">{tool.input}</span>
        <span className="tl-tool-meta">
          {tool.status === 'running' ? (
            <RunningDots />
          ) : (
            <>
              {tool.durationSec != null && <span className="tl-tool-duration">{tool.durationSec}s</span>}
              <span className={`tl-dot tl-dot-${tool.status}`} aria-hidden="true" />
            </>
          )}
        </span>
      </div>
      <Collapse open={open && clickable}>
        <pre className="tl-tool-output">{tool.output}</pre>
      </Collapse>
    </div>
  )
}

/** 工具组（变体 A）：一行"已执行 N 个工具"，展开为紧凑列表。 */
function ToolGroup({ tools }: { tools: { id: string; data: ToolEvent }[] }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="tl-tool-group">
      <div className="tl-row tl-collapsible" onClick={() => setOpen((v) => !v)} role="button" tabIndex={0}>
        <Icon glyph={terminalIcon} size={13} />
        <span className="tl-label">已执行 {tools.length} 个工具</span>
        <span className={`tl-chevron ${open ? 'tl-chevron-open' : ''}`} aria-hidden="true" />
      </div>
      <Collapse open={open}>
        <div className="tl-tool-list">
          {tools.map((tool) => (
            <ToolLine key={tool.id} tool={tool.data} />
          ))}
        </div>
      </Collapse>
    </div>
  )
}

/**
 * 变体 A · 轮次式：思考收在轮次开头、工具按组收起、正文正常输出。
 * 进行中的工具单独高亮一行，不混进已完成组。
 */
function VariantA({ entries }: { entries: Entry[] }) {
  const rows: (ReactNode | null)[] = []
  let pendingTools: { id: string; data: ToolEvent }[] = []
  const flushTools = (key: string): ReactNode | null => {
    if (pendingTools.length === 0) return null
    const group = <ToolGroup key={key} tools={pendingTools} />
    pendingTools = []
    return group
  }
  entries.forEach((entry, index) => {
    const key = `g-${index}`
    if (entry.type === 'user') {
      rows.push(flushTools(key))
      rows.push(<UserBubble key={entry.id} content={entry.content} />)
    } else if (entry.type === 'reasoning') {
      rows.push(flushTools(key))
      rows.push(<ThinkingLine key={entry.id} event={entry.data} />)
    } else if (entry.type === 'text') {
      rows.push(flushTools(key))
      rows.push(<TextBlock key={entry.id} content={entry.content} live={entry.live} />)
    } else if (entry.data.status === 'running') {
      rows.push(flushTools(key))
      rows.push(<ToolLine key={entry.id} tool={entry.data} />)
    } else {
      pendingTools.push({ id: entry.id, data: entry.data })
    }
  })
  rows.push(flushTools('tail'))
  return (
    <div className="tl-chat">
      <p className="tl-variant-note">
        变体 A · 轮次式 — 思考收在轮次开头，工具调用按组收起（JetBrains ACP / Air 风格）
      </p>
      {rows.filter(Boolean)}
    </div>
  )
}

/** 变体 B · 交替式：思考与工具交替出现，工具不分组直接展示。 */
function VariantB({ entries }: { entries: Entry[] }) {
  return (
    <div className="tl-chat">
      <p className="tl-variant-note">
        变体 B · 交替式 — 思考与工具交替出现，工具不分组直接展示（kilo 风格）
      </p>
      {entries.map((entry) => {
        if (entry.type === 'user') {
          return <UserBubble key={entry.id} content={entry.content} />
        }
        if (entry.type === 'reasoning') {
          return <ThinkingLine key={entry.id} event={entry.data} />
        }
        if (entry.type === 'text') {
          return <TextBlock key={entry.id} content={entry.content} live={entry.live} />
        }
        return <ToolLine key={entry.id} tool={entry.data} />
      })}
    </div>
  )
}

type VariantKey = 'A' | 'B'

/** 设计稿页面：标题条 + 变体切换 + 聊天区画布。 */
export default function TimelineDesign() {
  const [variant, setVariant] = useState<VariantKey>('A')
  return (
    <div className="tl-page">
      <header className="tl-header">
        <div className="tl-header-inner">
          <div className="tl-title-row">
            <span className="tl-title">Agent 执行时间线</span>
            <span className="tl-badge">设计稿</span>
          </div>
          <div className="tl-tabs" role="tablist">
            <button
              type="button"
              className={`tl-tab ${variant === 'A' ? 'tl-tab-active' : ''}`}
              onClick={() => setVariant('A')}
              role="tab"
              aria-selected={variant === 'A'}
            >
              变体 A · 轮次式
            </button>
            <button
              type="button"
              className={`tl-tab ${variant === 'B' ? 'tl-tab-active' : ''}`}
              onClick={() => setVariant('B')}
              role="tab"
              aria-selected={variant === 'B'}
            >
              变体 B · 交替式
            </button>
          </div>
          <p className="tl-note">
            进行中的元素带脉冲动画；思考行与工具组可点击展开。实现时二选一，或按各自优点合并。
          </p>
          <div className="tl-legend">
            <span>
              <i className="tl-dot tl-dot-done" aria-hidden="true" />
              完成
            </span>
            <span>
              <i className="tl-dot tl-dot-failed" aria-hidden="true" />
              失败
            </span>
            <span>
              <i className="tl-dot tl-dot-running" aria-hidden="true" />
              进行中
            </span>
          </div>
        </div>
      </header>
      <main className="tl-canvas">{variant === 'A' ? <VariantA entries={DEMO_ENTRIES} /> : <VariantB entries={DEMO_ENTRIES} />}</main>
    </div>
  )
}
