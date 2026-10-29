import client from './client'

export interface AiSetting {
  provider: string | null
  maskedKey: string | null
  model: string | null
  baseUrl: string | null
  hasKey: boolean
  defaultProvider: string
  updatedAt: string | null
}

export interface AiSettingPayload {
  provider?: string | null
  /** 留空表示不改动已保存的 Key */
  apiKey?: string
  model?: string
  baseUrl?: string
}

export const aiSettingsApi = {
  get: () => client.get<AiSetting>('/ai/settings'),
  save: (data: AiSettingPayload) => client.put<AiSetting>('/ai/settings', data),
  clear: () => client.delete<AiSetting>('/ai/settings'),
  test: () => client.post<{ ok: boolean; provider: string; reply?: string; message?: string }>('/ai/settings/test'),
}
