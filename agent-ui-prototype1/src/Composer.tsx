import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import Button from '@jetbrains/ring-ui-built/components/button/button.js'
import Input from '@jetbrains/ring-ui-built/components/input/input.js'
import { Directions } from '@jetbrains/ring-ui-built/components/popup/popup.consts.js'
import Select from '@jetbrains/ring-ui-built/components/select/select.js'
import addIcon from '@jetbrains/icons/add'
import { models, permissionOptions, providers } from './data'

interface ComposerProps {
  initialProvider?: string
  initialModel?: string
  initialThinking?: string
  placeholder?: string
  onSend?: (text: string) => void
}

export default function Composer({
  initialProvider = 'JetBrains',
  initialModel = 'claude-opus-4-8',
  initialThinking,
  placeholder = 'Ask anything...',
  onSend,
}: ComposerProps) {
  const [text, setText] = useState('')
  const [provider, setProvider] = useState(
    providers.find((p) => p.label === initialProvider) ?? providers[0],
  )
  const [model, setModel] = useState(
    models.find((m) => m.key === initialModel) ?? models[0],
  )
  const [thinkingLevel, setThinkingLevel] = useState(
    initialThinking ??
      models.find((m) => m.key === initialModel)?.thinking ??
      models.find((m) => m.key === initialModel)?.thinkingLevels?.[0] ??
      '',
  )
  const [permission, setPermission] = useState(
    permissionOptions.find((item) => item.key === 'default') ?? permissionOptions[0],
  )
  const [modelMenuOpen, setModelMenuOpen] = useState(false)
  const [hoveredModelKey, setHoveredModelKey] = useState<string | null>(null)
  const [hoveredModelPanelTop, setHoveredModelPanelTop] = useState<number | null>(null)
  const [modelPopupElement, setModelPopupElement] = useState<HTMLElement | null>(null)
  const thinkingPanelHideTimerRef = useRef<number | null>(null)

  const cancelThinkingPanelHide = () => {
    if (thinkingPanelHideTimerRef.current !== null) {
      window.clearTimeout(thinkingPanelHideTimerRef.current)
      thinkingPanelHideTimerRef.current = null
    }
  }

  const hideThinkingPanel = () => {
    cancelThinkingPanelHide()
    setHoveredModelKey(null)
    setHoveredModelPanelTop(null)
  }

  const scheduleThinkingPanelHide = () => {
    cancelThinkingPanelHide()
    thinkingPanelHideTimerRef.current = window.setTimeout(() => {
      setHoveredModelKey(null)
      setHoveredModelPanelTop(null)
      thinkingPanelHideTimerRef.current = null
    }, 120)
  }

  useEffect(() => {
    if (!modelMenuOpen) {
      setModelPopupElement(null)
      hideThinkingPanel()
      return
    }

    const popup = document.querySelector('.composer-model-popup') as HTMLElement | null
    if (!popup) return

    setModelPopupElement(popup)

    const showThinkingPanelForItem = (item: HTMLElement, hoveredModelIndex: number) => {
      const hoveredModel = models[hoveredModelIndex]
      if (!hoveredModel) return

      const popupRect = popup.getBoundingClientRect()
      const itemRect = item.getBoundingClientRect()

      cancelThinkingPanelHide()
      setHoveredModelKey(hoveredModel.key)
      setHoveredModelPanelTop(itemRect.top - popupRect.top + itemRect.height / 2)
    }

    const handlePointerOver = (event: Event) => {
      const target = event.target as HTMLElement | null
      const item = target?.closest('.ring-list-item') as HTMLElement | null
      if (!item || !popup.contains(item)) return

      const items = Array.from(popup.querySelectorAll('.ring-list-item')) as HTMLElement[]
      const hoveredIndex = items.indexOf(item)
      if (hoveredIndex < 0) return

      showThinkingPanelForItem(item, hoveredIndex)
    }

    const handlePopupLeave = () => {
      scheduleThinkingPanelHide()
    }

    popup.addEventListener('mouseover', handlePointerOver)
    popup.addEventListener('focusin', handlePointerOver)
    popup.addEventListener('mouseleave', handlePopupLeave)

    return () => {
      popup.removeEventListener('mouseover', handlePointerOver)
      popup.removeEventListener('focusin', handlePointerOver)
      popup.removeEventListener('mouseleave', handlePopupLeave)
    }
  }, [modelMenuOpen])

  useEffect(
    () => () => {
      cancelThinkingPanelHide()
    },
    [],
  )

  const handleSend = () => {
    if (!text.trim()) return
    onSend?.(text)
    setText('')
  }

  const selectedProvider = useMemo(
    () => ({ key: provider.key, label: provider.label, type: 'item' as const }),
    [provider],
  )

  const selectedModel = useMemo(
    () => ({
      key: model.key,
      label: model.label,
      type: 'item' as const,
    }),
    [model],
  )

  const selectedPermission = useMemo(
    () => ({
      key: permission.key,
      label: permission.label,
      type: 'item' as const,
    }),
    [permission],
  )

  const hoveredModel =
    models.find((item) => item.key === hoveredModelKey) ??
    models.find((item) => item.key === model.key) ??
    null

  return (
    <div className="composer-stack">
      <Input
        className="composer-textarea"
        multiline
        borderless
        placeholder={placeholder}
        value={text}
        onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setText(e.target.value)}
      />

      <div className="composer-bar">
        <div className="composer-bar-left">
          <Button icon={addIcon} inline />

          <Select
            className="composer-select composer-select-provider"
            buttonClassName="composer-select-trigger"
            popupClassName="composer-select-popup ring-ui-theme-dark"
            type={Select.Type.BUTTON}
            selected={selectedProvider}
            data={providers}
            minWidth={120}
            onChange={(item: { key: string; label: string } | null) => {
              if (!item) return
              const found = providers.find((p) => p.key === item.key)
              if (found) setProvider(found)
            }}
          />

          <div className="composer-model-hover-zone">
            <Select
              className="composer-select composer-select-model"
              buttonClassName="composer-select-trigger"
              popupClassName="composer-select-popup composer-model-popup ring-ui-theme-dark"
              type={Select.Type.BUTTON}
              selected={selectedModel}
              data={models.map((m) => ({ key: m.key, label: m.label, type: 'item' as const }))}
              minWidth={152}
              directions={[
                Directions.TOP_LEFT,
                Directions.TOP_RIGHT,
                Directions.BOTTOM_LEFT,
                Directions.BOTTOM_RIGHT,
              ]}
              onOpen={() => {
                setModelMenuOpen(true)
                hideThinkingPanel()
              }}
              onClose={() => {
                setModelMenuOpen(false)
                hideThinkingPanel()
              }}
              onChange={(item: { key: string; label: string } | null) => {
                if (!item) return
                const found = models.find((m) => m.key === item.key)
                if (found) {
                  setModel(found)
                  setThinkingLevel(found.thinking ?? found.thinkingLevels?.[0] ?? '')
                }
              }}
            />
          </div>
        </div>

        <div className="composer-bar-right">
          <Select
            className="composer-select composer-select-permission"
            buttonClassName={`composer-select-trigger composer-select-trigger-permission composer-select-trigger-permission-${permission.tone}`}
            popupClassName="composer-select-popup ring-ui-theme-dark"
            type={Select.Type.BUTTON}
            selected={selectedPermission}
            data={permissionOptions.map((item) => ({
              key: item.key,
              label: item.label,
              type: 'item' as const,
            }))}
            minWidth={126}
            onChange={(item: { key: string; label: string } | null) => {
              if (!item) return
              const found = permissionOptions.find((option) => option.key === item.key)
              if (found) setPermission(found)
            }}
          />

          <Button primary className="composer-send-button" onClick={handleSend}>
            Send
          </Button>
        </div>
      </div>

      {modelMenuOpen &&
        modelPopupElement &&
        hoveredModel &&
        hoveredModelPanelTop !== null &&
        hoveredModel.thinkingLevels?.length &&
        createPortal(
          <div
            className="composer-thinking-panel composer-thinking-panel-portal"
            style={{ top: hoveredModelPanelTop }}
            onMouseEnter={cancelThinkingPanelHide}
            onMouseLeave={scheduleThinkingPanelHide}
          >
            <div className="composer-thinking-title">Thinking</div>
            <div className="composer-thinking-options">
              {hoveredModel.thinkingLevels.map((level) => (
                <button
                  key={`${hoveredModel.key}-${level}`}
                  type="button"
                  className={`composer-thinking-option ${hoveredModel.key === model.key && thinkingLevel === level ? 'composer-thinking-option-active' : ''}`}
                  onClick={() => {
                    setModel(hoveredModel)
                    setThinkingLevel(level)
                    setModelMenuOpen(false)
                    hideThinkingPanel()
                  }}
                >
                  {level}
                </button>
              ))}
            </div>
          </div>,
          modelPopupElement,
        )}
    </div>
  )
}
