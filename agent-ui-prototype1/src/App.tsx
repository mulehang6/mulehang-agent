import { useMemo, useState } from 'react'
import Button from '@jetbrains/ring-ui-built/components/button/button.js'
import Header from '@jetbrains/ring-ui-built/components/header/header.js'
import Input from '@jetbrains/ring-ui-built/components/input/input.js'
import Island from '@jetbrains/ring-ui-built/components/island/island.js'
import IslandHeader from '@jetbrains/ring-ui-built/components/island/header.js'
import IslandContent from '@jetbrains/ring-ui-built/components/island/content.js'
import Icon from '@jetbrains/ring-ui-built/components/icon/icon.js'
import Tooltip from '@jetbrains/ring-ui-built/components/tooltip/tooltip.js'
import ScrollableSection from '@jetbrains/ring-ui-built/components/scrollable-section/scrollable-section.js'
import addIcon from '@jetbrains/icons/add'
import searchIcon from '@jetbrains/icons/search'
import settingsIcon from '@jetbrains/icons/settings'
import helpIcon from '@jetbrains/icons/help'
import shareIcon from '@jetbrains/icons/export'
import menuIcon from '@jetbrains/icons/menu'
import codeIcon from '@jetbrains/icons/code'
import terminalIcon from '@jetbrains/icons/terminal'
import downloadIcon from '@jetbrains/icons/download'
import uploadIcon from '@jetbrains/icons/import'
import historyIcon from '@jetbrains/icons/history'
import copyIcon from '@jetbrains/icons/copy'
import filterIcon from '@jetbrains/icons/filter'
import { tasks, type Task } from './data'
import Composer from './Composer'

const toolItems = [
  { icon: codeIcon, title: 'Code', active: true },
  { icon: terminalIcon, title: 'Terminal' },
  { icon: downloadIcon, title: 'Download' },
  { separator: true, key: 'sep-1' },
  { icon: uploadIcon, title: 'Upload' },
  { icon: historyIcon, title: 'History' },
  { separator: true, key: 'sep-2' },
  { icon: copyIcon, title: 'Copy' },
  { icon: filterIcon, title: 'Filter' },
]

function HeaderContent({ title, breadcrumb }: { title: string; breadcrumb: string }) {
  return (
    <Header className="app-header">
      <div className="header-inner">
        <div className="header-left">
          <Button icon={menuIcon} inline />
          <span className="header-title">Air</span>
        </div>
        <div className="header-center">
          <span className="header-breadcrumb">{breadcrumb}</span>
          <span className="header-title">{title}</span>
        </div>
        <div className="header-right">
          <Tooltip title="Share">
            <Button icon={shareIcon} inline />
          </Tooltip>
          <Tooltip title="Settings">
            <Button icon={settingsIcon} inline />
          </Tooltip>
          <Tooltip title="Help">
            <Button icon={helpIcon} inline />
          </Tooltip>
        </div>
      </div>
    </Header>
  )
}

function TaskItem({
  task,
  selected,
  onClick,
}: {
  task: Task
  selected: boolean
  onClick: () => void
}) {
  return (
    <div
      className={`task-item ${selected ? 'task-item-active' : ''}`}
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') onClick()
      }}
    >
      <div className="task-item-title">
        <span
          className={`task-status-dot ${task.status === 'done' ? 'task-status-dot-done' : ''}`}
        />
        {task.title}
      </div>
      <div className="task-item-subtitle">{task.subtitle}</div>
      <div className="task-item-meta">
        <span>{task.stats}</span>
      </div>
    </div>
  )
}

function PlanCard({ plan }: { plan: Task['plan'] }) {
  return (
    <Island className="plan-card">
      <IslandHeader>Plan</IslandHeader>
      <IslandContent>
        <div className="plan-list">
          {plan.map((item) => (
            <div key={item.number} className={`plan-item ${item.active ? 'plan-item-active' : ''}`}>
              <span className="plan-item-number">{item.number}</span>
              <span>{item.text}</span>
            </div>
          ))}
        </div>
      </IslandContent>
    </Island>
  )
}

function ToolButton({
  icon,
  title,
  active = false,
}: {
  icon: string
  title: string
  active?: boolean
}) {
  return (
    <Tooltip title={title}>
      <div
        className={`tool-button ${active ? 'tool-button-active' : ''}`}
        role="button"
        tabIndex={0}
      >
        <Icon glyph={icon} />
      </div>
    </Tooltip>
  )
}

export default function App() {
  const [selectedId, setSelectedId] = useState(tasks[0].id)
  const current = useMemo(() => tasks.find((t) => t.id === selectedId) ?? tasks[0], [selectedId])

  const running = tasks.filter((t) => t.group === 'running')
  const done = tasks.filter((t) => t.group === 'done')

  return (
    <div className="app-shell">
      <HeaderContent title={current.title} breadcrumb="workspace / agent-ui-prototype1" />

      <div className="app-body">
        <aside className="task-sidebar">
          <div className="task-sidebar-header">
            <Input
              className="task-search-input"
              placeholder="Search tasks"
              icon={searchIcon}
              borderless
            />
            <Button icon={addIcon} inline />
          </div>

          <Button primary>New Task</Button>

          <ScrollableSection className="task-list-scroll">
            <div className="task-section-title">Running</div>
            <div className="task-list">
              {running.map((task) => (
                <TaskItem
                  key={task.id}
                  task={task}
                  selected={task.id === selectedId}
                  onClick={() => setSelectedId(task.id)}
                />
              ))}
            </div>

            <div className="task-section-title">Done</div>
            <div className="task-list">
              {done.map((task) => (
                <TaskItem
                  key={task.id}
                  task={task}
                  selected={task.id === selectedId}
                  onClick={() => setSelectedId(task.id)}
                />
              ))}
            </div>
          </ScrollableSection>
        </aside>

        <main className="main-area">
          <Island className="workspace-panel">
            <IslandContent className="workspace-content">
              <div className="chat-container">
                <Island className="user-card">
                  <IslandContent>{current.subtitle}</IslandContent>
                </Island>

                <div className="answer-block">
                  <div className="answer-title">
                    <Icon glyph={codeIcon} />
                    {current.answerTitle}
                  </div>
                  {current.answerParagraphs.map((p, i) => (
                    <p key={i} className="ring-text-text">
                      {p}
                    </p>
                  ))}
                  {current.secondaryStatus && (
                    <div className="secondary-status">{current.secondaryStatus}</div>
                  )}
                </div>
              </div>
            </IslandContent>

            <div className="workspace-footer">
              <div className="composer-section">
                <PlanCard plan={current.plan} />
                <Composer
                  initialProvider={current.provider}
                  initialModel={current.model}
                  initialThinking={current.thinking}
                  placeholder={current.composerPlaceholder}
                  onSend={(text) => {
                    console.log('send', text)
                  }}
                />
              </div>
            </div>
          </Island>
        </main>

        <nav className="right-rail">
          {toolItems.map((item) =>
            'separator' in item ? (
              <div key={item.key} className="tool-rail-separator" />
            ) : (
              <ToolButton
                key={item.title}
                icon={item.icon}
                title={item.title}
                active={item.active}
              />
            ),
          )}
        </nav>
      </div>
    </div>
  )
}
