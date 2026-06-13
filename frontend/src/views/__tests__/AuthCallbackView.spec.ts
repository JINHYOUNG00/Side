import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import AuthCallbackView from '../AuthCallbackView.vue'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/client'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth')

let pinia: Pinia
let replaceSpy: ReturnType<typeof vi.spyOn>

async function mountAt(path: string) {
  await router.replace(path)
  await router.isReady()
  // 컴포넌트가 onMounted에서 호출하는 최종 내비게이션 의도를 관찰한다(fire-and-forget라 완료 시점 비결정적).
  replaceSpy = vi.spyOn(router, 'replace')
  const wrapper = mount(AuthCallbackView, { global: { plugins: [pinia, router, i18n] } })
  await flushPromises()
  return wrapper
}

describe('AuthCallbackView (로그인 → 대시보드 라우팅)', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(authApi.login).mockReset()
    pinia = createPinia()
    setActivePinia(pinia)
  })

  afterEach(() => {
    replaceSpy?.mockRestore()
  })

  it('code 교환 성공 시 세션을 세우고 홈으로 이동한다', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'jwt', isNewUser: false })
    await mountAt('/login/callback/kakao?code=xyz')

    expect(authApi.login).toHaveBeenCalledWith('kakao', 'xyz')
    expect(useAuthStore(pinia).token).toBe('jwt')
    expect(replaceSpy).toHaveBeenCalledWith({ name: 'home' })
  })

  it('교환 실패(ApiError) 시 로그인으로 복귀하며 error 코드를 전달한다', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new ApiError('OAUTH_EXCHANGE_FAILED', {}, 502))
    await mountAt('/login/callback/kakao?code=xyz')

    expect(replaceSpy).toHaveBeenCalledWith({ name: 'login', query: { error: 'OAUTH_EXCHANGE_FAILED' } })
    expect(useAuthStore(pinia).isAuthenticated).toBe(false)
  })

  it('동의 거부(code 없음) 시 교환을 시도하지 않고 로그인으로 복귀한다', async () => {
    await mountAt('/login/callback/kakao')

    expect(authApi.login).not.toHaveBeenCalled()
    expect(replaceSpy).toHaveBeenCalledWith({ name: 'login', query: { error: 'OAUTH_EXCHANGE_FAILED' } })
  })

  it('미지원 공급자면 PROVIDER_NOT_SUPPORTED로 복귀한다', async () => {
    await mountAt('/login/callback/naver?code=xyz')

    expect(authApi.login).not.toHaveBeenCalled()
    expect(replaceSpy).toHaveBeenCalledWith({ name: 'login', query: { error: 'PROVIDER_NOT_SUPPORTED' } })
  })
})
