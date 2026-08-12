import { StrictMode, useMemo, useState, type ChangeEvent } from 'react'
import { createRoot } from 'react-dom/client'
import Button from '@jetbrains/ring-ui-built/components/button/button.js'
import Icon from '@jetbrains/ring-ui-built/components/icon/icon.js'
import Input from '@jetbrains/ring-ui-built/components/input/input.js'
import ScrollableSection from '@jetbrains/ring-ui-built/components/scrollable-section/scrollable-section.js'
import Select from '@jetbrains/ring-ui-built/components/select/select.js'
import { Tab, Tabs } from '@jetbrains/ring-ui-built/components/tabs/tabs.js'
import Toggle from '@jetbrains/ring-ui-built/components/toggle/toggle.js'
import addIcon from '@jetbrains/icons/add'
import closeIcon from '@jetbrains/icons/close'
import searchIcon from '@jetbrains/icons/search'
import settingsIcon from '@jetbrains/icons/settings'
import './settings.css'

type SettingsSection = 'theme' | 'providers'
type ThemeMode = 'system' | 'dark' | 'light'
type Accent = 'blue' | 'teal' | 'green' | 'orange' | 'purple'

interface ProviderDraft {
  id: string
  label: string
  baseUrl: string
  enabled: boolean
}

const themeModes: Array<{ key: ThemeMode; label: string }> = [
  { key: 'system', label: '跟随系统' },
  { key: 'dark', label: '深色' },
  { key: 'light', label: '浅色' },
]

const accentOptions: Array<{ key: Accent; label: string }> = [
  { key: 'blue', label: '蓝色' },
  { key: 'teal', label: '青色' },
  { key: 'green', label: '绿色' },
  { key: 'orange', label: '橙色' },
  { key: 'purple', label: '紫色' },
]

const providerTypeOptions = [
  { key: 'openai', label: 'OpenAI Responses', type: 'item' as const },
  { key: 'anthropic', label: 'Anthropic Messages', type: 'item' as const },
]

/** Ring UI 设置 Island 的独立视觉原型；所有数据仅存于浏览器内存。 */
export function SettingsPrototypePage() {
  const [section, setSection] = useState<SettingsSection>('theme')
  const [scope, setScope] = useState('global')
  const [search, setSearch] = useState('')
  const [themeMode, setThemeMode] = useState<ThemeMode>('dark')
  const [accent, setAccent] = useState<Accent>('blue')
  const [expandedProvider, setExpandedProvider] = useState<string | null>('mulehang')
  const [providers, setProviders] = useState<ProviderDraft[]>([
    { id: 'mulehang', label: 'Mulehang', baseUrl: 'https://api.openai.com/v1', enabled: true },
    { id: 'team-gateway', label: '团队网关', baseUrl: 'https://gateway.example.com/v1', enabled: false },
  ])
  const selectedMode = useMemo(
    () => themeModes.find((item) => item.key === themeMode) ?? themeModes[0],
    [themeMode],
  )
  const filteredProviders = providers.filter((provider) =>
    `${provider.id} ${provider.label}`.toLowerCase().includes(search.toLowerCase()),
  )

  const updateProvider = (id: string, patch: Partial<ProviderDraft>) => {
    setProviders((current) => current.map((provider) => provider.id === id ? { ...provider, ...patch } : provider))
  }

  return (
    <div className={`ring-ui-theme-${themeMode === 'light' ? 'light' : 'dark'} settings-prototype theme-${accent}`}>
      <div className="settings-prototype-chat" aria-hidden="true">
        <div className="prototype-chat-title">chat / 设置页视觉原型</div>
        <div className="prototype-chat-copy">
          <p>设置以右侧 Island 的形式出现，不接管对话工作区。</p>
          <p>这里保留留白以验证抽屉的视觉比例、焦点以及终端共存状态。</p>
        </div>
        <div className="prototype-composer">Follow-up on this task…</div>
      </div>

      <main className="settings-island-shell">
        <header className="settings-title-row">
          <div className="settings-tab is-focused">
            <Icon glyph={settingsIcon} />
            <span>设置</span>
            <Button icon={closeIcon} inline aria-label="关闭设置原型" onClick={() => window.location.assign('./')} />
          </div>
        </header>

        <Input
          className="settings-search"
          placeholder="搜索设置"
          icon={searchIcon}
          value={search}
          onChange={(event: ChangeEvent<HTMLInputElement>) => setSearch(event.target.value)}
        />

        <Tabs selected={scope} onSelect={setScope} className="settings-scope-tabs">
          <Tab id="global" title="全局" />
          <Tab id="project" title="当前项目" />
        </Tabs>

        <div className="settings-body">
          <nav className="settings-navigation" aria-label="设置分类">
            <button className={section === 'theme' ? 'is-selected' : ''} onClick={() => setSection('theme')}>
              主题
            </button>
            <button className={section === 'providers' ? 'is-selected' : ''} onClick={() => setSection('providers')}>
              AI 服务
            </button>
          </nav>

          <ScrollableSection className="settings-content">
            {section === 'theme' ? (
              <section className="settings-section">
                <h1>主题</h1>
                <div className="settings-group">
                  <div className="setting-row setting-row-top">
                    <span>主题模式</span>
                    <Select
                      className="settings-select"
                      type={Select.Type.BUTTON}
                      data={themeModes.map((item) => ({ ...item, type: 'item' as const }))}
                      selected={{ ...selectedMode, type: 'item' as const }}
                      onChange={(item: { key: ThemeMode } | null) => item && setThemeMode(item.key)}
                    />
                  </div>
                  <div className="setting-row setting-row-colors">
                    <span>强调色</span>
                    <div className="accent-options">
                      {accentOptions.map((option) => (
                        <button
                          key={option.key}
                          className={`accent-swatch accent-${option.key} ${accent === option.key ? 'is-selected' : ''}`}
                          aria-label={option.label}
                          title={option.label}
                          onClick={() => setAccent(option.key)}
                        />
                      ))}
                    </div>
                  </div>
                  <p className="settings-hint">此页面仅演示主题 token 的即时视觉反馈，不会写入桌面配置。</p>
                </div>
              </section>
            ) : (
              <section className="settings-section">
                <div className="settings-section-heading">
                  <div>
                    <h1>AI 服务</h1>
                    <p>服务在原型中可展开编辑，真实配置不会变更。</p>
                  </div>
                  <Button
                    icon={addIcon}
                    onClick={() => {
                      const id = `provider-${providers.length + 1}`
                      setProviders((current) => [...current, { id, label: '新服务', baseUrl: '', enabled: true }])
                      setExpandedProvider(id)
                    }}
                  >
                    新增服务
                  </Button>
                </div>

                <div className="provider-list">
                  {filteredProviders.map((provider) => {
                    const expanded = expandedProvider === provider.id
                    return (
                      <article key={provider.id} className={`provider-card ${expanded ? 'is-expanded' : ''}`}>
                        <button className="provider-summary" onClick={() => setExpandedProvider(expanded ? null : provider.id)}>
                          <span>
                            <strong>{provider.label}</strong>
                            <small>{provider.id} · {provider.enabled ? '已启用' : '已停用'}</small>
                          </span>
                          <span>{expanded ? '收起' : '编辑'}</span>
                        </button>
                        {expanded && (
                          <div className="provider-editor">
                            <Input
                              label="显示名称"
                              value={provider.label}
                              onChange={(event: ChangeEvent<HTMLInputElement>) => updateProvider(provider.id, { label: event.target.value })}
                            />
                            <Input
                              label="Base URL"
                              value={provider.baseUrl}
                              onChange={(event: ChangeEvent<HTMLInputElement>) => updateProvider(provider.id, { baseUrl: event.target.value })}
                            />
                            <div className="setting-row">
                              <span>协议</span>
                              <Select type={Select.Type.BUTTON} data={providerTypeOptions} selected={providerTypeOptions[0]} />
                            </div>
                            <Toggle
                              checked={provider.enabled}
                              leftLabel="启用服务"
                              onChange={(event: ChangeEvent<HTMLInputElement>) => updateProvider(provider.id, { enabled: event.target.checked })}
                            />
                          </div>
                        )}
                      </article>
                    )
                  })}
                </div>
              </section>
            )}
          </ScrollableSection>
        </div>
      </main>
    </div>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SettingsPrototypePage />
  </StrictMode>,
)
