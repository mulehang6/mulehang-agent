export interface PlanItem {
  number: number
  text: string
  active?: boolean
}

export interface Task {
  id: string
  group: 'running' | 'done'
  title: string
  subtitle: string
  status: 'running' | 'done'
  stats: string
  answerTitle: string
  answerParagraphs: string[]
  secondaryStatus?: string
  plan: PlanItem[]
  composerPlaceholder: string
  provider: string
  model: string
  thinking?: string
}

export interface PermissionOption {
  key: string
  label: string
  tone: 'muted' | 'neutral' | 'accent' | 'warning' | 'danger'
}

export const tasks: Task[] = [
  {
    id: 't1',
    group: 'running',
    title: 'Implement user auth flow',
    subtitle: 'Add OAuth2 login and token refresh',
    status: 'running',
    stats: '+142 / −23 lines',
    answerTitle: 'Updating plan...',
    answerParagraphs: [
      'I will refactor the authentication service to support OAuth2. First, I will extract the token management into a dedicated module, then wire the refresh logic into the request interceptor.',
    ],
    secondaryStatus: 'Reading project files...',
    plan: [
      { number: 1, text: 'Analyze current auth module structure' },
      { number: 2, text: 'Create token storage and refresh logic', active: true },
      { number: 3, text: 'Update login UI to trigger OAuth flow' },
      { number: 4, text: 'Add tests for token refresh edge cases' },
    ],
    composerPlaceholder: 'Ask anything about this task...',
    provider: 'JetBrains',
    model: 'claude-opus-4-8',
    thinking: 'Max',
  },
  {
    id: 't2',
    group: 'running',
    title: 'Fix flaky unit tests',
    subtitle: 'Stabilize CI failures in payment service',
    status: 'running',
    stats: '+18 / −7 lines',
    answerTitle: 'Identifying root cause...',
    answerParagraphs: [
      'The payment tests are flaky because they rely on a shared database state. I will isolate each test case with a fresh transaction rollback and mock the external gateway.',
    ],
    plan: [
      { number: 1, text: 'Reproduce flaky test locally', active: true },
      { number: 2, text: 'Wrap tests in database transactions' },
      { number: 3, text: 'Mock external payment gateway' },
    ],
    composerPlaceholder: 'Add more context...',
    provider: 'OpenAI',
    model: 'gpt-4o',
    thinking: 'High',
  },
  {
    id: 't3',
    group: 'done',
    title: 'Update API documentation',
    subtitle: 'Document new endpoints for v2 release',
    status: 'done',
    stats: '+56 / −12 lines',
    answerTitle: 'Documentation updated',
    answerParagraphs: [
      'I have updated the OpenAPI spec and added examples for the new v2 endpoints. The changes are ready for review.',
    ],
    plan: [
      { number: 1, text: 'List new v2 endpoints' },
      { number: 2, text: 'Add request/response examples' },
      { number: 3, text: 'Publish docs to the portal' },
    ],
    composerPlaceholder: 'Follow up on this task...',
    provider: 'Anthropic',
    model: 'claude-sonnet-4-6',
  },
  {
    id: 't4',
    group: 'done',
    title: 'Refactor dashboard widgets',
    subtitle: 'Extract reusable chart components',
    status: 'done',
    stats: '+203 / −89 lines',
    answerTitle: 'Refactoring complete',
    answerParagraphs: [
      'The dashboard widgets have been refactored into smaller, reusable chart components. Performance improved by reducing re-renders.',
    ],
    plan: [
      { number: 1, text: 'Audit current widget code' },
      { number: 2, text: 'Design shared chart component API' },
      { number: 3, text: 'Migrate existing widgets' },
      { number: 4, text: 'Measure render performance' },
    ],
    composerPlaceholder: 'Ask a follow-up...',
    provider: 'JetBrains',
    model: 'claude-haiku-4-5',
  },
]

export const providers = [
  { key: 'jetbrains', label: 'JetBrains', type: 'item' as const },
  { key: 'openai', label: 'OpenAI', type: 'item' as const },
  { key: 'anthropic', label: 'Anthropic', type: 'item' as const },
]
export interface ModelOption {
  key: string
  label: string
  thinking?: string
  thinkingLevels?: string[]
}

export const models: ModelOption[] = [
  {
    key: 'claude-opus-4-8',
    label: 'Claude Opus 4.8',
    thinking: 'Max',
    thinkingLevels: ['Medium', 'High', 'Max'],
  },
  {
    key: 'claude-sonnet-4-6',
    label: 'Claude Sonnet 4.6',
    thinking: 'High',
    thinkingLevels: ['Low', 'Medium', 'High'],
  },
  {
    key: 'claude-haiku-4-5',
    label: 'Claude Haiku 4.5',
    thinking: 'Low',
    thinkingLevels: ['Low'],
  },
  {
    key: 'gpt-4o',
    label: 'GPT-4o',
    thinking: 'Medium',
    thinkingLevels: ['Low', 'Medium', 'High'],
  },
  {
    key: 'gpt-4o-mini',
    label: 'GPT-4o mini',
    thinking: 'Low',
    thinkingLevels: ['Low', 'Medium'],
  },
]

export const permissionOptions: PermissionOption[] = [
  { key: 'auto', label: 'Auto', tone: 'accent' },
  { key: 'default', label: 'Ask permission', tone: 'neutral' },
  { key: 'edit-allow', label: 'Edit allow', tone: 'warning' },
  { key: 'plan', label: 'Plan', tone: 'muted' },
  { key: 'brave', label: 'Brave', tone: 'danger' },
]
