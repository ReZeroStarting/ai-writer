import client from './client'
import type { LoginRequest, RegisterRequest, AuthResponse } from '@/types'

export const authApi = {
  login: (data: LoginRequest) => client.post<AuthResponse>('/auth/login', data),
  register: (data: RegisterRequest) => client.post<AuthResponse>('/auth/register', data),
  refresh: (refreshToken: string) => client.post<AuthResponse>('/auth/refresh', { refreshToken }),
}
