import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import WindfallAllocateSheet from '../WindfallAllocateSheet.vue'
import * as cycleApi from '@/api/cycle'
import * as suggestionsApi from '@/api/suggestions'
import type { CurrentCycle } from '@/api/cycle'
import type { Suggestion } from '@/api/suggestions'

// cycle·suggestions 모듈은 함수·타입만 export(상수 없음)라 전체 모킹 안전([[vimock-breaks-const-exports]] 비해당).
vi.mock('@/api/cycle')
vi.mock('@/api/suggestions')

const WINDFALL: Suggestion = {
  id: 9,
  type: 'WINDFALL',
  status: 'PENDING',
  payload: { cycleId: 7, difference: 100000, baseIncome: 2000000, confirmedIncome: 2100000 },
}

// 체크리스트: PENDING 비-LIVING(저축) 1건 + LIVING + DONE — 뒤 둘은 대상에서 걸러져야 한다.
const CYCLE: CurrentCycle = {
  id: 7,
  label: '2026-06',
  cycleStart: '2026-06-25',
  cycleEnd: '2026-07-24',
  income: 2100000,
  incomeConfirmed: true,
  checklist: [
    {
      accountId: 1,
      accountName: '국민',
      total: 550000,
      lines: [
        { id: 10, name: '청년도약계좌', plannedAmount: 50000, status: 'PENDING' },
        { id: 11, name: 'LIVING', plannedAmount: 200000, status: 'PENDING' },
        { id: 12, name: '월세', plannedAmount: 300000, status: 'DONE' },
      ],
    },
  ],
  progress: { done: 1, total: 3 },
}

function mountSheet() {
  return mount(WindfallAllocateSheet, {
    props: { open: true, suggestion: WINDFALL },
    global: { plugins: [router, i18n], stubs: { teleport: true } },
  })
}

describe('WindfallAllocateSheet (CYCLE-05 인터랙티브 배분)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'ko'
    vi.mocked(cycleApi.getCurrentCycle).mockReset()
    vi.mocked(suggestionsApi.allocateSuggestion).mockReset()
  })

  it('PENDING 비-LIVING 라인만 대상으로 보여준다', async () => {
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(CYCLE)
    const wrapper = mountSheet()
    await flushPromises()

    expect(wrapper.findAll('.line').length).toBe(1)
    expect(wrapper.text()).toContain('청년도약계좌')
    expect(wrapper.text()).not.toContain('월세') // DONE 제외
  })

  it('금액을 입력해 적용하면 allocateSuggestion을 호출하고 applied를 emit한다', async () => {
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(CYCLE)
    vi.mocked(suggestionsApi.allocateSuggestion).mockResolvedValue({ ...WINDFALL, status: 'APPLIED' })
    const wrapper = mountSheet()
    await flushPromises()

    await wrapper.find('#alloc-10').setValue('30000')
    await wrapper.find('.btn').trigger('click')
    await flushPromises()

    expect(suggestionsApi.allocateSuggestion).toHaveBeenCalledWith(9, [{ planLineId: 10, amount: 30000 }])
    expect(wrapper.emitted('applied')?.[0]).toEqual([9])
  })

  it('차액을 초과하면 적용 버튼이 비활성화된다', async () => {
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(CYCLE)
    const wrapper = mountSheet()
    await flushPromises()

    await wrapper.find('#alloc-10').setValue('150000') // 차액 100,000 초과
    expect(wrapper.find('.btn').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.remaining.over').exists()).toBe(true)
  })

  it('조정할 PENDING 항목이 없으면 빈 안내를 보여준다', async () => {
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue({
      ...CYCLE,
      checklist: [
        {
          accountId: 1,
          accountName: '국민',
          total: 200000,
          lines: [{ id: 11, name: 'LIVING', plannedAmount: 200000, status: 'PENDING' }],
        },
      ],
    })
    const wrapper = mountSheet()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('suggestion.allocate.empty'))
  })
})
