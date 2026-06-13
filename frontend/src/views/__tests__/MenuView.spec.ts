import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import MenuView from '../MenuView.vue'
import { useAuthStore } from '@/stores/auth'

function mountView() {
  return mount(MenuView, { global: { plugins: [router, i18n] } })
}

// 행 버튼은 라벨 + 셰브론(›)을 함께 담으므로 부분 일치로 집는다.
function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((b) => b.text().includes(text))
}

describe('MenuView (SCR-07 전체 허브)', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    // 라우트 이동 자체는 spy로만 검증(하위 화면 마운트·API 호출 방지).
    vi.spyOn(router, 'push').mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('관리 진입 행과 로그아웃을 표시한다', () => {
    const wrapper = mountView()
    expect(wrapper.text()).toContain(i18n.global.t('menu.accounts'))
    expect(wrapper.text()).toContain(i18n.global.t('menu.items'))
    expect(wrapper.text()).toContain(i18n.global.t('menu.logout'))
  })

  it('통장 관리 행을 누르면 /accounts로 이동한다', async () => {
    const wrapper = mountView()
    await buttonByText(wrapper, i18n.global.t('menu.accounts'))!.trigger('click')
    expect(router.push).toHaveBeenCalledWith('/accounts')
  })

  it('배분 항목 관리 행을 누르면 /items로 이동한다', async () => {
    const wrapper = mountView()
    await buttonByText(wrapper, i18n.global.t('menu.items'))!.trigger('click')
    expect(router.push).toHaveBeenCalledWith('/items')
  })

  it('로그아웃을 누르면 세션을 비우고 로그인으로 보낸다', async () => {
    const auth = useAuthStore()
    auth.setSession({ accessToken: 't', isNewUser: false })
    expect(auth.isAuthenticated).toBe(true)

    const wrapper = mountView()
    await buttonByText(wrapper, i18n.global.t('menu.logout'))!.trigger('click')

    expect(auth.isAuthenticated).toBe(false)
    expect(router.push).toHaveBeenCalledWith('/login')
  })
})
