import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../auth'

vi.mock('@/api/auth', () => ({
  login: vi.fn<(provider: string, code: string) => Promise<{ accessToken: string; isNewUser: boolean }>>(
    async () => ({ accessToken: 'jwt-token', isNewUser: true }),
  ),
}))

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('초기 상태는 비로그인이다', () => {
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.token).toBeNull()
  })

  it('setSession이 토큰을 localStorage에 저장하고 인증 상태로 만든다', () => {
    const auth = useAuthStore()
    auth.setSession({ accessToken: 'abc', isNewUser: false })
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.token).toBe('abc')
    expect(localStorage.getItem('salary.accessToken')).toBe('abc')
  })

  it('logout이 토큰과 localStorage를 비운다', () => {
    const auth = useAuthStore()
    auth.setSession({ accessToken: 'abc', isNewUser: true })
    auth.logout()
    expect(auth.isAuthenticated).toBe(false)
    expect(localStorage.getItem('salary.accessToken')).toBeNull()
    expect(auth.isNewUser).toBe(false)
  })

  it('생성 시 localStorage의 토큰을 복원한다', () => {
    localStorage.setItem('salary.accessToken', 'restored')
    setActivePinia(createPinia())
    const auth = useAuthStore()
    expect(auth.token).toBe('restored')
    expect(auth.isAuthenticated).toBe(true)
  })

  it('loginWithCode가 code를 교환해 세션과 isNewUser를 세운다', async () => {
    const auth = useAuthStore()
    const session = await auth.loginWithCode('kakao', 'oauth-code')
    expect(session.accessToken).toBe('jwt-token')
    expect(auth.token).toBe('jwt-token')
    expect(auth.isNewUser).toBe(true)
  })
})
