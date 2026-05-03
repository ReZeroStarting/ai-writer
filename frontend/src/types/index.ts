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
