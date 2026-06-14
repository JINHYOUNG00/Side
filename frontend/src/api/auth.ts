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

// 로컬 전용 dev 로그인(VERIFY-notion-match). 백엔드 dev 프로필에서만 존재하는 엔드포인트로,
// OAuth 자격증명 없이 노션 시드 사용자의 JWT를 받는다. 호출부(LoginView)가 import.meta.env.DEV로 게이트.
export async function devLogin(): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/dev/login')
  return data
}
