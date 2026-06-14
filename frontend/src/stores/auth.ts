import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, devLogin as devLoginApi, type LoginResponse } from '@/api/auth'

const TOKEN_KEY = 'salary.accessToken'

// 새로고침 후에도 로그인 유지를 위해 JWT만 localStorage에 둔다(민감 식별정보 저장 금지 — 규칙 6).
function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

function writeToken(token: string | null) {
  try {
    if (token) {
      localStorage.setItem(TOKEN_KEY, token)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  } catch {
    // localStorage 불가 환경(시크릿 모드 등)은 메모리 토큰으로만 동작.
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(readToken())
  // 첫 로그인 직후 온보딩(SCR-02) 분기용. 새로고침으로 잃어도 무방한 1회성 신호.
  const isNewUser = ref(false)

  const isAuthenticated = computed(() => token.value !== null)

  function setSession(session: LoginResponse) {
    token.value = session.accessToken
    isNewUser.value = session.isNewUser
    writeToken(session.accessToken)
  }

  function logout() {
    token.value = null
    isNewUser.value = false
    writeToken(null)
  }

  // 공급자 콜백에서 받은 code를 서버로 교환해 세션을 세운다.
  async function loginWithCode(provider: string, code: string): Promise<LoginResponse> {
    const session = await loginApi(provider, code)
    setSession(session)
    return session
  }

  // 로컬 전용 dev 로그인 — 노션 시드 사용자로 세션을 세운다(VERIFY-notion-match).
  async function loginAsDev(): Promise<LoginResponse> {
    const session = await devLoginApi()
    setSession(session)
    return session
  }

  return { token, isNewUser, isAuthenticated, setSession, logout, loginWithCode, loginAsDev }
})
