import { useState, type ReactNode } from 'react'
import Icon from '@jetbrains/ring-ui-built/components/icon/icon.js'
import Select from '@jetbrains/ring-ui-built/components/select/select.js'
import sparkleIcon from '@jetbrains/icons/sparkle'
import searchIcon from '@jetbrains/icons/search'
import terminalIcon from '@jetbrains/icons/terminal'
import pencilIcon from '@jetbrains/icons/pencil'
import fileTextIcon from '@jetbrains/icons/file-text'
import checkmarkIcon from '@jetbrains/icons/checkmark'

/**
 * Agent 执行时间线 · 设计稿（轮次式，参考 JetBrains ACP / Air）
 *
 * 结构：思考收在轮次开头（一行可折叠）→ 正文输出 → 工具调用按组收起。
 *
 * 简约原则：
 * - 全部小字体系（12–13.5px），灰阶 + 少量强调色，无卡片背景。
 * - 思考中：sparkle 图标与 "Thinking" 文案之间放圆圈加载动画。
 * - 工具组：组内有执行中的工具时默认展开，执行完毕自动收起（无论成败）。
 * - 无状态圆点；进行中 = accent 色文字 + 圆圈加载，失败 = 红色文字。
 * - 展开/收起箭头仅在鼠标悬浮到该行时显示；名称不长时紧跟名称，
 *   名称过长（如 run_powershell）时贴最右侧。
 * - 动效：展开 180ms ease-out，其余 ≤160ms。
 */

type ToolKind = 'search' | 'terminal' | 'edit' | 'read'

interface ToolEvent {
  kind: ToolKind
  name: string
  input: string
  status: 'running' | 'done' | 'failed'
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
  read: fileTextIcon,
}

/** 名称超过该长度时，展开箭头从"名称旁"移到行最右侧。 */
const LONG_TOOL_NAME_CHARS = 12

/** 一轮已完成 + 第二轮执行中的演示数据。 */
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
      text: 'The user reports recurring 401s. Possible causes: 1) no retry queue after refresh token expiry; 2) concurrent requests trigger refresh simultaneously, causing a token race; 3) the Authorization header is not updated after refresh. Inspect AuthService first, then the request interceptor.',
    },
  },
  {
    type: 'tool',
    id: 't1',
    data: { kind: 'search', name: 'search_in_files', input: '"token refresh"', status: 'done' },
  },
  {
    type: 'tool',
    id: 't2',
    data: { kind: 'terminal', name: 'run_powershell', input: 'Get-Content src/auth/AuthService.kt', status: 'done' },
  },
  {
    type: 'tool',
    id: 't3',
    data: { kind: 'read', name: 'read_file', input: 'src/auth/AuthService.kt', status: 'done' },
  },
  {
    type: 'tool',
    id: 't5',
    data: {
      kind: 'edit',
      name: 'edit_file',
      input: 'src/auth/HttpClient.kt',
      status: 'failed',
      output: 'Error: no writable file handle, file is read-only (chmod 444)',
    },
  },
  {
    type: 'text',
    id: 'm1',
    content: 'Found it. AuthService.refresh() only updates the stored token, not the in-memory Authorization header, so the first request after refresh still carries the old token and gets a 401.\n\nThe interceptor also has a concurrency gap: two simultaneously expired requests both trigger refresh, and the second overwrites the first, invalidating one side.',
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
    content: 'Applying the fix: sync the header after a successful refresh and add single-flight protection to the refresh flow…',
    live: true,
  },
]

const PLAN_OPTIONS = [
  { key: 'air', label: '轮次式（Air）', type: 'item' as const },
  { key: 'kilo', label: '交替式（kilo）', type: 'item' as const },
]

/** 思考耗时英文文案，支持分钟档。 */
function formatThoughtDuration(durationSec?: number): string {
  if (durationSec == null) return 'Thought'
  if (durationSec < 60) return `Thought for ${durationSec}s`
  const minutes = Math.floor(durationSec / 60)
  const seconds = durationSec % 60
  return seconds > 0 ? `Thought for ${minutes}m ${seconds}s` : `Thought for ${minutes}m`
}

/** 思考收起态摘要：取每一段最前面的部分，拼接为单行，过长由 ellipsis 截断。 */
function buildReasoningSummary(text: string): string {
  const SUMMARY_PER_PART_CHARS = 40
  return text
    .split(/\n+/)
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => part.slice(0, SUMMARY_PER_PART_CHARS))
    .join(' · ')
}

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


/** 展开/收起箭头；鼠标悬浮到所在行时才显示。 */
function Chevron({ open, position }: { open: boolean; position: 'inline' | 'end' }) {
  return (
    <span
      className={`tl-chevron tl-chevron-${position} ${open ? 'tl-chevron-open' : ''}`}
      aria-hidden="true"
    />
  )
}

/** 思考行：一行可折叠摘要；进行中时"图标 + 圆圈 + Thinking"。 */
function ThinkingLine({ event }: { event: ReasoningEvent }) {
  const [open, setOpen] = useState(false)
  if (event.live) {
    return (
      <div className="tl-row tl-thinking">
        <Icon glyph={sparkleIcon} size={14} />
        <span className="tl-label">Thinking</span>
      </div>
    )
  }
  return (
    <div className="tl-thinking-block">
      <div
        className="tl-row tl-collapsible"
        onClick={() => setOpen((v) => !v)}
        role="button"
        tabIndex={0}
      >
        <Icon glyph={sparkleIcon} size={14} />
        <span className="tl-label">{formatThoughtDuration(event.durationSec)}</span>
        <span className="tl-thinking-summary">{buildReasoningSummary(event.text)}</span>
        <Chevron open={open} position="inline" />
      </div>
      <Collapse open={open}>
        <p className="tl-reasoning-text">{event.text}</p>
      </Collapse>
    </div>
  )
}

/**
 * 单个工具行：类型图标（执行中本体动画）+ 名称 + 输入摘要。
 * 编辑类工具的具体文件路径默认隐藏，展开后才显示；失败工具展开显示错误输出。
 */
function ToolLine({ tool }: { tool: ToolEvent }) {
  const [open, setOpen] = useState(false)
  const hasOutput = tool.output != null && tool.output.length > 0
  const isEdit = tool.kind === 'edit'
  const clickable = isEdit || hasOutput
  const longName = tool.name.length > LONG_TOOL_NAME_CHARS
  const running = tool.status === 'running'
  return (
    <div className="tl-tool-line">
      <div
        className={`tl-row tl-collapsible ${running ? 'tl-tool-running' : ''} ${
          tool.status === 'failed' ? 'tl-tool-failed' : ''
        }`}
        onClick={() => clickable && setOpen((v) => !v)}
        role="button"
        tabIndex={clickable ? 0 : -1}
      >
        <Icon glyph={TOOL_ICONS[tool.kind]} size={13} />
        <span className="tl-tool-name">{tool.name}</span>
        {!longName && clickable && <Chevron open={open} position="inline" />}
        {!isEdit && <span className="tl-tool-input">{tool.input}</span>}
        {longName && clickable && <Chevron open={open} position="end" />}
      </div>
      <Collapse open={open && clickable}>
        {isEdit && <p className="tl-tool-edit-target">{tool.input}</p>}
        {hasOutput && <pre className="tl-tool-output">{tool.output}</pre>}
      </Collapse>
    </div>
  )
}

/**
 * 工具组状态图标：执行中为灰色加载圈，执行完毕为绿色勾，两者交叉淡化过渡。
 */
function GroupStatusIcon({ running }: { running: boolean }) {
  return (
    <span className="tl-group-status" aria-hidden="true">
      <span className={`tl-group-status-icon ${running ? 'tl-status-visible' : ''}`}>
        <span className="tl-group-spinner" />
      </span>
      <span className={`tl-group-status-icon ${running ? '' : 'tl-status-visible'}`}>
        <Icon glyph={checkmarkIcon} size={13} />
      </span>
    </span>
  )
}

/**
 * 工具组：一行"已执行 N 个工具"。
 * 组内有执行中的工具时默认展开，执行完毕自动收起；用户手动点击可随时展开/收起。
 */
function ToolGroup({ tools }: { tools: { id: string; data: ToolEvent }[] }) {
  const hasRunning = tools.some((tool) => tool.data.status === 'running')
  // 默认跟随组状态：组内有执行中的工具时展开，执行完毕自动收起；
  // 用户手动点击后以手动选择为准。
  const [userOpen, setUserOpen] = useState<boolean | null>(null)
  const open = userOpen ?? hasRunning
  return (
    <div className="tl-tool-group">
      <div
        className="tl-row tl-collapsible"
        onClick={() => setUserOpen((v) => (v === null ? !hasRunning : !v))}
        role="button"
        tabIndex={0}
      >
        <GroupStatusIcon running={hasRunning} />
        <span className="tl-label">
          {hasRunning ? 'Executing tools' : `Executed ${tools.length} tool${tools.length > 1 ? 's' : ''}`}
        </span>
        <Chevron open={open} position="inline" />
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

/** 轮次式时间线：思考 → 正文 → 工具组；进行中的工具并入组内（组默认展开）。 */
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
    } else {
      pendingTools.push({ id: entry.id, data: entry.data })
    }
  })
  rows.push(flushTools('tail'))
  return <div className="tl-chat">{rows.filter(Boolean)}</div>
}

/** 设计稿页面：标题条（含 ring-ui Select 选中样式参考）+ 聊天区画布。 */
export default function TimelineDesign() {
  const [plan, setPlan] = useState(PLAN_OPTIONS[0])
  return (
    <div className="tl-page">
      <header className="tl-header">
        <div className="tl-header-inner">
          <div className="tl-title-row">
            <span className="tl-title">Agent 执行时间线</span>
            <span className="tl-badge">设计稿</span>
            <div className="tl-header-right">
              <Select
                className="tl-select"
                buttonClassName="tl-select-trigger"
                popupClassName="tl-select-popup ring-ui-theme-dark"
                type={Select.Type.BUTTON}
                selected={plan}
                data={PLAN_OPTIONS}
                minWidth={150}
                onChange={(item: { key: string; label: string } | null) => {
                  const found = PLAN_OPTIONS.find((option) => option.key === item?.key)
                  if (found) setPlan(found)
                }}
              />
            </div>
          </div>
          <p className="tl-note">
            思考收在轮次开头、工具调用按组收起；思考中与执行中的工具显示圆圈加载动画（accent 色），失败的工具名显示红色。
          </p>
          <p className="tl-note">
            展开箭头仅在悬浮该行时显示；名称过长时箭头移到最右侧。右上角下拉框的选中样式（勾选图标 + hover
            高亮）取自 ring-ui Select 源码，实现时参考。
          </p>
        </div>
      </header>
      <main className="tl-canvas">
        <VariantA entries={DEMO_ENTRIES} />
      </main>
    </div>
  )
}
