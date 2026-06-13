import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import router from '../index'
import { useAuthStore } from '@/stores/auth'

describe('라우터 가드', () => {
  beforeEach(async () => {
    localStorage.clear()
    setActivePinia(createPinia())
    await router.replace('/login')
    await router.isReady()
  })

  it('비로그인은 보호 라우트(/) 접근 시 로그인으로 보낸다', async () => {
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('로그인 상태로 /login 진입 시 홈으로 보낸다', async () => {
    useAuthStore().setSession({ accessToken: 't', isNewUser: false })
    await router.push('/') // /login에서 출발하면 동일 경로라 내비게이션이 무시되므로 홈에서 시작
    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('로그인 상태는 보호 라우트(/)에 접근할 수 있다', async () => {
    useAuthStore().setSession({ accessToken: 't', isNewUser: false })
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('콜백 라우트는 비로그인도 접근 가능하다(public)', async () => {
    await router.push('/login/callback/kakao?code=abc')
    expect(router.currentRoute.value.name).toBe('auth-callback')
  })
})
