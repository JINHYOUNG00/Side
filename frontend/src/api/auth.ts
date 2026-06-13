import api from './client'

// POST /api/v1/auth/{provider} {code} → {accessToken, isNewUser} (API명세 2장).
export interface LoginResponse {
  accessToken: string
  isNewUser: boolean
}

export async function login(provider: string, code: string): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>(`/auth/${provider}`, { code })
  return data
}
