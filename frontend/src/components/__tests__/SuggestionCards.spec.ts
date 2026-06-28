import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import SuggestionCards from '../SuggestionCards.vue'
import { ApiError } from '@/api/client'
import * as suggestionsApi from '@/api/suggestions'
import type { Suggestion } from '@/api/suggestions'

// suggestions 모듈은 함수·타입만 export(상수 없음)라 전체 모킹이 안전하다([[vimock-breaks-const-exports]] 비해당).
vi.mock('@/api/suggestions')

const RAISE_LIVING: Suggestion = {
  id: 1,
  type: 'RAISE_LIVING',
  status: 'PENDING',
  payload: { suggestedIncrease: 20000, avgOverspend: 15000, streak: 3 },
}
const RAISE_SAVING: Suggestion = {
  id: 2,
  type: 'RAISE_SAVING',
  status: 'PENDING',
  payload: { suggestedIncrease: 40000, avgSurplus: 40000, streak: 3 },
}
const MATURITY: Suggestion = {
  id: 3,
  type: 'REBALANCE_MATURITY',
  status: 'PENDING',
  payload: {
    itemId: 5,
    itemName: 'OO적금',
    monthlyAmount: 300000,
    maturityDate: '2026-07-31',
    expectedMaturityAmount: 3731976,
  },
}

const WINDFALL: Suggestion = {
  id: 4,
  type: 'WINDFALL',
  status: 'PENDING',
  payload: { cycleId: 7, difference: 50000, baseIncome: 2000000, confirmedIncome: 2050000 },
}
const SHORTFALL: Suggestion = {
  id: 5,
  type: 'SHORTFALL',
  status: 'PENDING',
  payload: { cycleId: 7, difference: 40000, baseIncome: 2000000, confirmedIncome: 1960000 },
}

function mountCards() {
  return mount(SuggestionCards, {
    global: { plugins: [router, i18n], stubs: { teleport: true } },
  })
}

function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((b) => b.text() === text)
}

describe('SuggestionCards (MOD-06 제안 카드)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'ko'
    vi.mocked(suggestionsApi.listSuggestions).mockReset()
    vi.mocked(suggestionsApi.applySuggestion).mockReset()
    vi.mocked(suggestionsApi.dismissSuggestion).mockReset()
  })

  it('생활비 증액 제안을 천단위 문구로 보여준다', async () => {
    vi.mocked(suggestionsApi.listSuggestions).mockResolvedValue([RAISE_LIVING])
    const wrapper = mountCards()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('suggestion.raiseLiving.title'))
    expect(wrapper.text()).toContain('20,000')
    expect(wrapper.text()).toContain('15,000')
  })

  it('저축 증액 제안을 보여준다', async () => {
    vi.mocked(suggestionsApi.listSuggestions).mockResolvedValue([RAISE_SAVING])
    const wrapper = mountCards()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('suggestion.raiseSaving.title'))
    expect(wrapper.text()).toContain('40,000')
  })

  it('만기 리밸런싱 제안은 항목명·예상 수령액을 보여준다', async () => {
    vi.mocked(suggestionsApi.listSuggestions).mockResolvedValue([MATURITY])
    const wrapper = mountCards()
    await flushPromises()

    expect(wrapper.text()).toContain('OO적금')
    expect(wrapper.text()).toContain('300,000')
    expect(wrapper.text()).toContain('3,731,976') // 예상 수령액
  })

  it('예상 만기금액이 없으면 예상 수령 줄을 생략한다', async () => {
    const noExpected: Suggestion = {
      ...MATURITY,
      payload: { itemId: 5, itemName: 'OO적금', monthlyAmount: 300000, maturityDate: '2026-07-31' },
    }
    vi.mocked(suggestionsApi.listSuggestions).mockResolvedValue([noExpected])
    const wrapper = mountCards()
    await flushPromises()

    expect(wrapper.find('.sg-extra').exists()).toBe(false)
    expect(wrapper.text()).toContain('300,000')
  })

  it('여윳돈(WINDFALL) 제안을 차액과 함께 보여준다', async () => {
    vi.mocked(suggestionsApi.listSuggestions).mockResolvedValue([WINDFALL])
    const wrapper = mountCards()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('suggestion.windfall.title'))
    expect(wrapper.text()).toContain('50,000')
  })

  it('부족(SHORTFALL) 제안을 차액과 함께 보여준다', async () => {
    vi.mocked(suggestionsApi.listSuggestions).mockResolvedValue([SHORTFALL])
    const wrapper = mountCards()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('suggestion.shortfall.title'))
    expect(wrapper.text()).toContain('40,000')
  })

  it('제안이 없으면 아무것도 그리지 않는다', async () => {
    vi.mocked(suggestionsApi.listSuggestions).mockResolvedValue([])
    const wrapper = mountCards()
    await flushPromises()

    expect(wrapper.find('.suggestions').exists()).toBe(false)
  })

  it('조회 실패 시 조용히 숨긴다', async () => {
    vi.mocked(suggestionsApi.listSuggestions).mockRejectedValue(new ApiError('INTERNAL_ERROR', {}, 500))
    const wrapper = mountCards()
    await flushPromises()

    expect(wrapper.find('.suggestions').exists()).toBe(false)
  })

  it('반영하면 서버를 호출하고 카드를 제거한다', async () => {
    vi.mocked(suggestionsApi.listSuggestions).mockResolvedValue([RAISE_LIVING, RAISE_SAVING])
    vi.mocked(suggestionsApi.applySuggestion).mockResolvedValue({ ...RAISE_LIVING, status: 'APPLIED' })
    const wrapper = mountCards()
    await flushPromises()

    expect(wrapper.findAll('.sg-card').length).toBe(2)
    // 첫 카드(생활비 증액) 반영.
    await wrapper.get('.sg-card').get('.sg-btn.apply').trigger('click')
    await flushPromises()

    expect(suggestionsApi.applySuggestion).toHaveBeenCalledWith(1)
    expect(wrapper.findAll('.sg-card').length).toBe(1)
    expect(wrapper.text()).not.toContain(i18n.global.t('suggestion.raiseLiving.title'))
  })

  it('닫으면 서버를 호출하고 카드를 제거한다', async () => {
    vi.mocked(suggestionsApi.listSuggestions).mockResolvedValue([RAISE_LIVING])
    vi.mocked(suggestionsApi.dismissSuggestion).mockResolvedValue({ ...RAISE_LIVING, status: 'DISMISSED' })
    const wrapper = mountCards()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('suggestion.dismiss'))!.trigger('click')
    await flushPromises()

    expect(suggestionsApi.dismissSuggestion).toHaveBeenCalledWith(1)
    expect(wrapper.find('.suggestions').exists()).toBe(false)
  })

  it('반영 실패 시 에러 코드를 노출하고 카드를 유지한다', async () => {
    vi.mocked(suggestionsApi.listSuggestions).mockResolvedValue([RAISE_LIVING])
    vi.mocked(suggestionsApi.applySuggestion).mockRejectedValue(
      new ApiError('SUGGESTION_ALREADY_RESOLVED', {}, 409),
    )
    const wrapper = mountCards()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('suggestion.apply'))!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(
      i18n.global.t('errors.SUGGESTION_ALREADY_RESOLVED'),
    )
    expect(wrapper.findAll('.sg-card').length).toBe(1)
  })
})
