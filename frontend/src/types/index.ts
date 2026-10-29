export interface User {
  id: number
  username: string
  email: string
  phone?: string
  createdAt: string
}

export interface Project {
  id: number
  name: string
  description?: string
  docCount?: number
  createdAt: string
  updatedAt: string
}

export interface Document {
  id: number
  projectId: number
  title: string
  content: string
  createdAt: string
  updatedAt: string
}

export interface StyleTemplate {
  id: number
  name: string
  description?: string
  systemPrompt: string
  createdAt: string
}

export interface LearningJob {
  id: number
  templateId: number
  materialId: number
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  resultPrompt?: string
  errorMsg?: string
  createdAt: string
  updatedAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  user: User
}

export interface AiRequest {
  content: string
  styleTemplateId?: number
  instruction?: string
}

export interface LoginRequest {
  account: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  phone?: string
  password: string
}

// === Framework Types ===

export interface Framework {
  id: number
  projectId: number
  status: 'ACTIVE' | 'FINISHED'
  elementCount: number
  chapterCount: number
  unresolvedConflictCount: number
  createdAt: string
  updatedAt: string
}

export interface FrameworkElement {
  id: string
  version: number
  type: string
  name: string
  content: string
  isDeprecated: boolean
  createdAt: string
}

export interface Chapter {
  id: number
  documentId: number
  chapterIndex: number
  status: 'DRAFT' | 'AI_GENERATING' | 'AI_GENERATED' | 'USER_EDITING' | 'COMPLETED'
  summary: string
  createdAt: string
  updatedAt: string
}

export interface WritingEvent {
  id: number
  eventType: string
  payload: string
  timestamp: string
}

export interface Conflict {
  id: number
  elementId: string
  chapterId: number
  chapterRefVersion: number
  currentVersion: number
  severity: 'MAJOR' | 'MINOR'
  description: string
  status: 'PENDING' | 'IGNORED' | 'PATCHED' | 'REWRITTEN'
  createdAt: string
}

export const ELEMENT_TYPES = [
  { value: 'CHARACTER', label: '角色' },
  { value: 'LOCATION', label: '地点' },
  { value: 'ITEM', label: '物品/道具' },
  { value: 'ORGANIZATION', label: '组织/势力' },
  { value: 'EVENT', label: '事件/时间线' },
  { value: 'WORLD_SETTING', label: '世界观设定' },
] as const

/** 后端返回的是 CHARACTER/LOCATION 这类枚举，页面上要显示中文。 */
export function elementTypeLabel(type: string): string {
  return ELEMENT_TYPES.find(t => t.value === type)?.label ?? type
}
