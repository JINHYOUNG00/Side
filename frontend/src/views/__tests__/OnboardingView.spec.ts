import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import OnboardingView from '../OnboardingView.vue'
import * as meApi from '@/api/me'
import type { Me } from '@/api/me'

// 부분 모킹 — PAYDAY_ADJUSTMENTS 상수/타입은 유지(full automock하면 조정 규칙 칩 v-for가 깨짐),
// getMe/updateMe만 스텁(ItemFormSheet의 CATEGORIES 함정과 동일).
vi.mock('@/api/me', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/me')>()
  return { ...actual, getMe: vi.fn<typeof actual.getMe>(), updateMe: vi.fn<typeof actual.updateMe>() }
})

// 신규 사용자 플레이스홀더(AUTH-01: 실수령액 0·월급일 1·NONE).
const NEW_USER: Me = {
  id: 1,
  email: 'a@b.com',
  nickname: '김진형',
  baseIncome: 0,
  payday: 1,
  paydayAdjustment: 'NONE',
  includeInvestmentInSavingsRate: true,
  locale: 'ko',
  livingAccountId: null,
}

let replaceSpy: ReturnType<typeof vi.spyOn>

function mountView() {
  return mount(OnboardingView, { global: { plugins: [router, i18n] } })
}

// ko 로케일 텍스트로 버튼(칩 포함)을 집는다.
function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((b) => b.text() === text)
}

describe('OnboardingView (SCR-02 온보딩 스텝1)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(meApi.getMe).mockReset()
    vi.mocked(meApi.updateMe).mockReset()
    replaceSpy = vi.spyOn(router, 'replace').mockResolvedValue(undefined)
  })

  afterEach(() => {
    replaceSpy.mockRestore()
  })

  it('신규 사용자는 GET /me 후 실수령액을 빈 칸으로 둔다(플레이스홀더 0 미표시)', async () => {
    vi.mocked(meApi.getMe).mockResolvedValue(NEW_USER)
    const wrapper = mountView()
    await flushPromises()

    expect(meApi.getMe).toHaveBeenCalledOnce()
    expect((wrapper.find('#onboarding-income').element as HTMLInputElement).value).toBe('')
  })

  it('기존 설정이 있으면 실수령액·월급일·조정 규칙을 채운다', async () => {
    vi.mocked(meApi.getMe).mockResolvedValue({
      ...NEW_USER,
      baseIncome: 2_500_000,
      payday: 25,
      paydayAdjustment: 'PREV_BUSINESS_DAY',
    })
    const wrapper = mountView()
    await flushPromises()

    expect((wrapper.find('#onboarding-income').element as HTMLInputElement).value).toBe('2,500,000')
    // 프리셋 25일 칩이 선택 상태.
    expect(buttonByText(wrapper, '25일')!.attributes('aria-pressed')).toBe('true')
  })

  it('입력 후 다음을 누르면 updateMe 호출(천 단위 파싱) 후 홈으로 이동한다', async () => {
    vi.mocked(meApi.getMe).mockResolvedValue(NEW_USER)
    vi.mocked(meApi.updateMe).mockResolvedValue(NEW_USER)
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('#onboarding-income').setValue('2500000')
    await buttonByText(wrapper, '25일')!.trigger('click')
    // 조정 규칙은 기본값 PREV_BUSINESS_DAY 그대로 제출(별도 선택 테스트는 아래).
    await buttonByText(wrapper, i18n.global.t('onboarding.step1.next'))!.trigger('click')
    await flushPromises()

    expect(meApi.updateMe).toHaveBeenCalledWith({
      baseIncome: 2_500_000,
      payday: 25,
      paydayAdjustment: 'PREV_BUSINESS_DAY',
      livingAccountId: null,
    })
    expect(replaceSpy).toHaveBeenCalledWith({ name: 'home' })
  })

  it('조정 규칙 칩을 고르면 선택값이 payload에 반영된다', async () => {
    vi.mocked(meApi.getMe).mockResolvedValue(NEW_USER)
    vi.mocked(meApi.updateMe).mockResolvedValue(NEW_USER)
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('#onboarding-income').setValue('2500000')
    await buttonByText(wrapper, '25일')!.trigger('click')
    await buttonByText(wrapper, i18n.global.t('onboarding.adjustment.NONE'))!.trigger('click')
    await buttonByText(wrapper, i18n.global.t('onboarding.step1.next'))!.trigger('click')
    await flushPromises()

    expect(meApi.updateMe).toHaveBeenCalledWith(
      expect.objectContaining({ paydayAdjustment: 'NONE' }),
    )
  })

  it('기존 생활비 통장 지정을 step1 갱신에서 보존한다', async () => {
    vi.mocked(meApi.getMe).mockResolvedValue({ ...NEW_USER, baseIncome: 3_000_000, payday: 10, livingAccountId: 7 })
    vi.mocked(meApi.updateMe).mockResolvedValue(NEW_USER)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('onboarding.step1.next'))!.trigger('click')
    await flushPromises()

    expect(meApi.updateMe).toHaveBeenCalledWith(
      expect.objectContaining({ baseIncome: 3_000_000, payday: 10, livingAccountId: 7 }),
    )
  })

  it('실수령액이 비면 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(meApi.getMe).mockResolvedValue(NEW_USER)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, '25일')!.trigger('click')
    await buttonByText(wrapper, i18n.global.t('onboarding.step1.next'))!.trigger('click')
    await flushPromises()

    expect(meApi.updateMe).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('월급일을 고르지 않으면 VALIDATION_FAILED를 보인다', async () => {
    vi.mocked(meApi.getMe).mockResolvedValue(NEW_USER)
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('#onboarding-income').setValue('2500000')
    await buttonByText(wrapper, i18n.global.t('onboarding.step1.next'))!.trigger('click')
    await flushPromises()

    expect(meApi.updateMe).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('직접 입력 월급일이 1~31을 벗어나면(32) VALIDATION_FAILED를 보인다', async () => {
    vi.mocked(meApi.getMe).mockResolvedValue(NEW_USER)
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('#onboarding-income').setValue('2500000')
    await buttonByText(wrapper, i18n.global.t('onboarding.step1.custom'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#onboarding-payday').setValue('32')
    await buttonByText(wrapper, i18n.global.t('onboarding.step1.next'))!.trigger('click')
    await flushPromises()

    expect(meApi.updateMe).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })
})
