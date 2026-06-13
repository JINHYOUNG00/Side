import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import App from '@/App.vue'
import { useAuthStore } from '@/stores/auth'

// 하단 탭 내비게이션 E2E(SCR-07) — App 셸 + 실 라우터로 탭 전환·활성 표시를 검증.
function mountApp() {
  return mount(App, { global: { plugins: [router, i18n] } })
}

function navButton(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('button.nav-item').find((b) => b.text() === label)
}

describe('하단 탭 내비게이션 (SCR-07)', () => {
  beforeEach(async () => {
    localStorage.clear()
    setActivePinia(createPinia())
    useAuthStore().setSession({ accessToken: 't', isNewUser: false })
    await router.replace('/')
    await router.isReady()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('홈에서 전체 탭을 누르면 /menu로 이동한다', async () => {
    // push는 spy로 두되 원 동작은 유지 — 반환된 내비게이션 promise를 await해
    // lazy 라우트(MenuView) 로딩까지 완료시킨다.
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mountApp()
    await flushPromises()

    await navButton(wrapper, i18n.global.t('nav.all'))!.trigger('click')
    await pushSpy.mock.results[pushSpy.mock.results.length - 1]!.value
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('menu')
  })

  it('전체 허브에서 홈 탭을 누르면 /로 돌아온다', async () => {
    await router.push('/menu')
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mountApp()
    await flushPromises()

    await navButton(wrapper, i18n.global.t('nav.home'))!.trigger('click')
    await pushSpy.mock.results[pushSpy.mock.results.length - 1]!.value
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('전체 허브에서 전체 탭이 활성(on) 표시된다', async () => {
    await router.push('/menu')
    const wrapper = mountApp()
    await flushPromises()

    expect(navButton(wrapper, i18n.global.t('nav.all'))!.classes()).toContain('on')
  })

  it('통장 관리 하위 화면에서도 전체 탭이 활성 유지된다', async () => {
    await router.push('/accounts')
    const wrapper = mountApp()
    await flushPromises()

    expect(navButton(wrapper, i18n.global.t('nav.all'))!.classes()).toContain('on')
  })
})
