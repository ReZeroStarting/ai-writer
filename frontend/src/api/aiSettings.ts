import client from './client'

export interface AiProviderPreset {
  id: string
  label: string
  protocol: string
  baseUrl: string
  defaultModel: string
  consoleUrl: string
  note: string
}

export interface AiSetting {
  provider: string | null
  providerLabel: string | null
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
  providers: () => client.get<AiProviderPreset[]>('/ai/settings/providers'),
  get: () => client.get<AiSetting>('/ai/settings'),
  save: (data: AiSettingPayload) => client.put<AiSetting>('/ai/settings', data),
  clear: () => client.delete<AiSetting>('/ai/settings'),
  test: () => client.post<{ ok: boolean; provider: string; reply?: string; message?: string }>('/ai/settings/test'),
}
