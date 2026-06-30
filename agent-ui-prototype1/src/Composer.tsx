import { useMemo, useState, type ChangeEvent } from 'react'
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
  // 切换 task 时按新的初始 props 同步跟随任务的字段（provider/model/thinking），
  // 保留草稿文本与权限选择等本地状态，避免整组件 remount 造成输入丢失
  const [prevInitial, setPrevInitial] = useState({
    provider: initialProvider,
    model: initialModel,
    thinking: initialThinking,
  })
  if (
    initialProvider !== prevInitial.provider ||
    initialModel !== prevInitial.model ||
    initialThinking !== prevInitial.thinking
  ) {
    setPrevInitial({
      provider: initialProvider,
      model: initialModel,
      thinking: initialThinking,
    })
    const foundModel = models.find((m) => m.key === initialModel) ?? models[0]
    setProvider(providers.find((p) => p.label === initialProvider) ?? providers[0])
    setModel(foundModel)
    setThinkingLevel(
      initialThinking ??
        foundModel.thinking ??
        foundModel.thinkingLevels?.[0] ??
        '',
    )
  }

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

  const thinkingLevels = model.thinkingLevels ?? []
  const selectedThinking = useMemo(
    () => ({
      key: thinkingLevel,
      label: thinkingLevel,
      type: 'item' as const,
    }),
    [thinkingLevel],
  )

  return (
    <div className="composer-stack">
      <Input
        className="composer-textarea"
        multiline
        borderless
        placeholder={placeholder}
        value={text}
        onChange={(e: ChangeEvent<HTMLTextAreaElement>) => setText(e.target.value)}
      />

      <div className="composer-bar">
        <div className="composer-bar-left">
          <Button icon={addIcon} inline aria-label="Add" />

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

          <Select
            className="composer-select composer-select-model"
            buttonClassName="composer-select-trigger"
            popupClassName="composer-select-popup ring-ui-theme-dark"
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
            onChange={(item: { key: string; label: string } | null) => {
              if (!item) return
              const found = models.find((m) => m.key === item.key)
              if (found) {
                setModel(found)
                setThinkingLevel(found.thinking ?? found.thinkingLevels?.[0] ?? '')
              }
            }}
          />

          {thinkingLevels.length > 0 && (
            <Select
              className="composer-select composer-select-reasoning"
              buttonClassName="composer-select-trigger"
              popupClassName="composer-select-popup ring-ui-theme-dark"
              type={Select.Type.BUTTON}
              selected={selectedThinking}
              data={thinkingLevels.map((level) => ({
                key: level,
                label: level,
                type: 'item' as const,
              }))}
              minWidth={120}
              onChange={(item: { key: string; label: string } | null) => {
                if (item) setThinkingLevel(item.key)
              }}
            />
          )}
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
    </div>
  )
}
