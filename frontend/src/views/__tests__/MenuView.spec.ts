import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import MenuView from '../MenuView.vue'
import ImportSheet from '@/components/ImportSheet.vue'
import * as accountsApi from '@/api/accounts'
import * as meApi from '@/api/me'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/accounts')
vi.mock('@/api/me')

const BASE_ME: meApi.Me = {
  id: 1,
  email: null,
  nickname: 'me',
  baseIncome: 2_500_000,
  payday: 25,
  paydayAdjustment: 'PREV_BUSINESS_DAY',
  includeInvestmentInSavingsRate: true,
  locale: 'ko',
  livingAccountId: null,
}

// Teleport를 인라인 렌더해 탈퇴 확인 시트(BottomSheet) 내부 버튼을 wrapper.find로 찾을 수 있게 한다.
function mountView() {
  return mount(MenuView, { global: { plugins: [router, i18n], stubs: { teleport: true } } })
}

// 행 버튼은 라벨 + 셰브론(›)을 함께 담으므로 부분 일치로 집는다.
function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((b) => b.text().includes(text))
}

describe('MenuView (SCR-07 전체 허브)', () => {
  beforeEach(() => {
    localStorage.clear()
    // 언어 싱글톤은 테스트 간 공유되므로 매번 기본(ko)으로 되돌린다.
    i18n.global.locale.value = 'ko'
    setActivePinia(createPinia())
    // 라우트 이동 자체는 spy로만 검증(하위 화면 마운트·API 호출 방지).
    vi.spyOn(router, 'push').mockResolvedValue(undefined)
    // 허브 진입 시 임포트 시트용 통장을 읽는다(MOD-07) — 네트워크 대신 mock.
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([])
    // 언어 토글(SET-03)용 현재 설정 — getMe로 읽고 updateMe로 전체 설정을 되돌려 보낸다.
    vi.mocked(meApi.getMe).mockResolvedValue({ ...BASE_ME })
    vi.mocked(meApi.updateMe).mockImplementation(async (input) => ({ ...BASE_ME, ...input }) as meApi.Me)
    // 회원 탈퇴(AUTH-04) — 기본은 성공(204). 실패 케이스는 개별 테스트에서 재정의.
    vi.mocked(meApi.deleteMe).mockResolvedValue(undefined)
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

  it('보관함 행을 누르면 /archive로 이동한다', async () => {
    const wrapper = mountView()
    await buttonByText(wrapper, i18n.global.t('menu.archive'))!.trigger('click')
    expect(router.push).toHaveBeenCalledWith('/archive')
  })

  it('노션 표 가져오기 행을 누르면 임포트 시트를 연다', async () => {
    const wrapper = mountView()
    await flushPromises() // onMounted 통장 로드

    const sheet = wrapper.findComponent(ImportSheet)
    expect(sheet.props('open')).toBe(false)

    await buttonByText(wrapper, i18n.global.t('menu.import'))!.trigger('click')
    expect(sheet.props('open')).toBe(true)
  })

  it('현재 언어(ko)를 선택 상태로 표시한다', async () => {
    const wrapper = mountView()
    await flushPromises() // onMounted me 로드

    const koBtn = buttonByText(wrapper, i18n.global.t('menu.lang.ko'))!
    expect(koBtn.classes()).toContain('on')
    expect(koBtn.attributes('aria-pressed')).toBe('true')
  })

  it('English를 누르면 전체 설정과 함께 locale을 PATCH하고 UI 언어를 전환한다', async () => {
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('menu.lang.en'))!.trigger('click')
    await flushPromises()

    // 온보딩 필수값(실수령액·월급일·조정 규칙)을 보존한 채 locale만 바꿔 보낸다.
    expect(meApi.updateMe).toHaveBeenCalledWith({
      baseIncome: 2_500_000,
      payday: 25,
      paydayAdjustment: 'PREV_BUSINESS_DAY',
      livingAccountId: null,
      locale: 'en',
    })
    expect(i18n.global.locale.value).toBe('en')
    expect(localStorage.getItem('salary.locale')).toBe('en')
  })

  it('언어 변경에 실패하면 에러를 보이고 UI 언어를 바꾸지 않는다', async () => {
    vi.mocked(meApi.updateMe).mockRejectedValue(new ApiError('INTERNAL_ERROR', {}, 500))
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('menu.lang.en'))!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('menu.languageError'))
    expect(i18n.global.locale.value).toBe('ko')
  })

  it('현재 투자 포함 설정(포함)을 선택 상태로 표시한다', async () => {
    const wrapper = mountView()
    await flushPromises() // onMounted me 로드

    const includeBtn = buttonByText(wrapper, i18n.global.t('menu.savingsInclude'))!
    expect(includeBtn.classes()).toContain('on')
    expect(includeBtn.attributes('aria-pressed')).toBe('true')
  })

  it('제외를 누르면 전체 설정과 함께 투자 포함 토글을 false로 PATCH한다', async () => {
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('menu.savingsExclude'))!.trigger('click')
    await flushPromises()

    // 온보딩 필수값을 보존한 채 투자 포함만 끄고, locale은 건드리지 않는다(보존).
    expect(meApi.updateMe).toHaveBeenCalledWith({
      baseIncome: 2_500_000,
      payday: 25,
      paydayAdjustment: 'PREV_BUSINESS_DAY',
      livingAccountId: null,
      includeInvestmentInSavingsRate: false,
    })
    const excludeBtn = buttonByText(wrapper, i18n.global.t('menu.savingsExclude'))!
    expect(excludeBtn.classes()).toContain('on')
  })

  it('투자 포함 변경에 실패하면 에러를 보이고 설정을 바꾸지 않는다', async () => {
    vi.mocked(meApi.updateMe).mockRejectedValue(new ApiError('INTERNAL_ERROR', {}, 500))
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('menu.savingsExclude'))!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('menu.savingsInvestmentError'))
    // 포함(true)이 그대로 선택 상태.
    const includeBtn = buttonByText(wrapper, i18n.global.t('menu.savingsInclude'))!
    expect(includeBtn.classes()).toContain('on')
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

  it('회원 탈퇴를 누르면 확인 시트를 연다(즉시 삭제하지 않는다)', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).not.toContain(i18n.global.t('menu.withdrawConfirmTitle'))

    await buttonByText(wrapper, i18n.global.t('menu.withdraw'))!.trigger('click')

    expect(wrapper.text()).toContain(i18n.global.t('menu.withdrawConfirmTitle'))
    // 확인 전에는 삭제 API를 부르지 않는다.
    expect(meApi.deleteMe).not.toHaveBeenCalled()
  })

  it('확인 시트에서 취소하면 삭제하지 않고 시트를 닫는다', async () => {
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('menu.withdraw'))!.trigger('click')
    await buttonByText(wrapper, i18n.global.t('menu.withdrawCancel'))!.trigger('click')

    expect(meApi.deleteMe).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain(i18n.global.t('menu.withdrawConfirmTitle'))
  })

  it('탈퇴를 확정하면 전체 삭제 후 세션을 비우고 로그인으로 보낸다', async () => {
    const auth = useAuthStore()
    auth.setSession({ accessToken: 't', isNewUser: false })

    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('menu.withdraw'))!.trigger('click')
    await buttonByText(wrapper, i18n.global.t('menu.withdrawConfirm'))!.trigger('click')
    await flushPromises()

    expect(meApi.deleteMe).toHaveBeenCalledOnce()
    expect(auth.isAuthenticated).toBe(false)
    expect(router.push).toHaveBeenCalledWith('/login')
  })

  it('탈퇴에 실패하면 에러를 보이고 세션을 유지한다', async () => {
    vi.mocked(meApi.deleteMe).mockRejectedValue(new ApiError('INTERNAL_ERROR', {}, 500))
    const auth = useAuthStore()
    auth.setSession({ accessToken: 't', isNewUser: false })

    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('menu.withdraw'))!.trigger('click')
    await buttonByText(wrapper, i18n.global.t('menu.withdrawConfirm'))!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('menu.withdrawError'))
    // 삭제 실패 — 세션 유지, 로그인으로 보내지 않는다.
    expect(auth.isAuthenticated).toBe(true)
    expect(router.push).not.toHaveBeenCalledWith('/login')
  })
})
