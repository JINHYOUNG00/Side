import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import HomeView from '../HomeView.vue'
import { ApiError } from '@/api/client'
import * as waterfallApi from '@/api/waterfall'
import * as suggestionsApi from '@/api/suggestions'
import type { Waterfall } from '@/api/waterfall'

vi.mock('@/api/waterfall')
// 체크리스트 카드(SCR-03b)는 자체적으로 GET /cycles/current를 호출한다. 폭포 테스트에선 미노출이면
// 충분하므로 자동 모킹으로 조회가 빈 응답(undefined)을 주게 막아둔다(카드 숨김). 동작은 ChecklistCard.spec.ts.
vi.mock('@/api/cycle')
// 제안 카드(MOD-06)도 자체적으로 GET /suggestions를 호출한다 — 폭포 테스트에선 빈 제안으로 막아 미노출.
vi.mock('@/api/suggestions')

// 평상시 폭포(과배분 아님) — API명세 3장 예시에 맞춘 일관 fixture.
// remaining = income − Σ소계 − envelope = 2,500,000 − 1,780,000 − 0 = 720,000.
// EMERGENCY 항목은 groups에서 빠지고 split.emergency로만 집계 → living = 720,000 − 200,000 = 520,000.
const NORMAL: Waterfall = {
  income: 2500000,
  groups: [
    {
      category: 'SAVING',
      subtotal: 700000,
      items: [
        { id: 1, name: '청년도약계좌', amount: 700000, accountId: 3, accountName: '국민', endDate: '2029-11-07', expectedMaturityAmount: null },
      ],
    },
    {
      category: 'INVESTMENT',
      subtotal: 800000,
      items: [
        { id: 2, name: 'ISA', amount: 500000, accountId: 4, accountName: '증권', endDate: null, expectedMaturityAmount: null },
        { id: 3, name: '주식모으기', amount: 300000, accountId: 4, accountName: '증권', endDate: null, expectedMaturityAmount: null },
      ],
    },
    {
      category: 'FIXED',
      subtotal: 280000,
      items: [
        { id: 4, name: '월세', amount: 200000, accountId: 5, accountName: '케이뱅크', endDate: null, expectedMaturityAmount: null },
        { id: 5, name: '통신', amount: 50000, accountId: 5, accountName: '케이뱅크', endDate: null, expectedMaturityAmount: null },
        { id: 6, name: '관리비', amount: 30000, accountId: 5, accountName: '케이뱅크', endDate: null, expectedMaturityAmount: null },
      ],
    },
  ],
  envelopeContribution: 0,
  remaining: 720000,
  split: { emergency: 200000, living: 520000 },
  overAllocated: false,
  // 저축률(SET-02): (700,000 + 800,000) / 2,500,000 = 60.0%, 기본은 투자 포함.
  savingsRate: { value: 60.0, includesInvestment: true },
}

// 과배분 fixture — Σ소계(2,200,000) > income(2,000,000) → remaining −200,000, living −300,000.
const OVER: Waterfall = {
  income: 2000000,
  groups: [
    { category: 'SAVING', subtotal: 1500000, items: [{ id: 1, name: '적금', amount: 1500000, accountId: 1, accountName: '국민', endDate: null, expectedMaturityAmount: null }] },
    { category: 'INVESTMENT', subtotal: 400000, items: [{ id: 2, name: 'ISA', amount: 400000, accountId: 2, accountName: '증권', endDate: null, expectedMaturityAmount: null }] },
    { category: 'FIXED', subtotal: 300000, items: [{ id: 3, name: '월세', amount: 300000, accountId: 3, accountName: '케이뱅크', endDate: null, expectedMaturityAmount: null }] },
  ],
  envelopeContribution: 0,
  remaining: -200000,
  split: { emergency: 100000, living: -300000 },
  overAllocated: true,
  savingsRate: { value: 95.0, includesInvestment: true },
}

function mountView() {
  return mount(HomeView, { global: { plugins: [router, i18n] } })
}

describe('HomeView (SCR-03 홈 폭포)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    // prefers-reduced-motion=true로 보고 → 카운트업 생략, 남는 돈이 최종값으로 즉시 렌더.
    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    vi.mocked(waterfallApi.getWaterfall).mockReset()
    vi.mocked(suggestionsApi.listSuggestions).mockReset()
    vi.mocked(suggestionsApi.listSuggestions).mockResolvedValue([])
  })

  it('폭포를 불러와 남는 돈·비상금·생활비 분배를 표시한다', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockResolvedValue(NORMAL)
    const wrapper = mountView()
    await flushPromises()

    expect(waterfallApi.getWaterfall).toHaveBeenCalledOnce()
    // 카운트업이 최종값까지 도달(reduced-motion).
    expect(wrapper.find('.head-card .amount').text()).toContain('720,000')
    expect(wrapper.text()).toContain('200,000') // 비상금
    expect(wrapper.text()).toContain('520,000') // 생활비
    // 헤더 캡션: 월급 대비 비율(720,000 / 2,500,000 ≈ 29%).
    expect(wrapper.find('.caption').text()).toContain('29')
  })

  it('카테고리 그룹 행과 소계를 표시하고, 2건 이상이면 건수를 보여준다', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockResolvedValue(NORMAL)
    const wrapper = mountView()
    await flushPromises()

    const rows = wrapper.findAll('.waterfall .row')
    // 그룹 3 + 남는 돈 1 (봉투 0이라 행 없음).
    expect(rows).toHaveLength(4)
    expect(wrapper.text()).toContain(i18n.global.t('items.category.SAVING'))
    expect(wrapper.text()).toContain('700,000')
    expect(wrapper.text()).toContain('800,000')
    // INVESTMENT 2건 · FIXED 3건은 건수 표기, SAVING 1건은 미표기.
    expect(wrapper.text()).toContain(i18n.global.t('home.count', { n: 2 }))
    expect(wrapper.text()).toContain(i18n.global.t('home.count', { n: 3 }))
    // 스택 바 세그먼트: 그룹 3 + 남는 돈 1.
    expect(wrapper.findAll('.stack i')).toHaveLength(4)
  })

  it('배분 항목이 없으면 빈 상태를 보여준다', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockResolvedValue({
      income: 0,
      groups: [],
      envelopeContribution: 0,
      remaining: 0,
      split: { emergency: 0, living: 0 },
      overAllocated: false,
      savingsRate: { value: 0.0, includesInvestment: true },
    })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('home.emptyTitle'))
    expect(wrapper.find('.waterfall').exists()).toBe(false)
  })

  it('과배분이면 경고 배너와 부족액을 보여준다', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockResolvedValue(OVER)
    const wrapper = mountView()
    await flushPromises()

    const banner = wrapper.find('.warn-banner')
    expect(banner.exists()).toBe(true)
    // 부족액 = 생활비 음수의 절댓값 = 300,000.
    expect(banner.text()).toContain('300,000')
    // 남는 돈·생활비는 음수라 빨강(over) 클래스.
    expect(wrapper.find('.head-card .amount').classes()).toContain('over')
  })

  it('조정 후보를 유연성 순으로 정렬한다(LIVING·EMERGENCY > INVESTMENT > 나머지)', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockResolvedValue(OVER)
    const wrapper = mountView()
    await flushPromises()

    const names = wrapper.findAll('.candidate .cand-name').map((n) => n.text())
    // living<0이라 후보 제외. emergency(유연) → INVESTMENT(중간) → SAVING·FIXED(고정, 금액 큰 순).
    expect(names).toEqual([
      i18n.global.t('home.emergency'),
      i18n.global.t('items.category.INVESTMENT'),
      i18n.global.t('items.category.SAVING'),
      i18n.global.t('items.category.FIXED'),
    ])
    // 첫 후보는 유연 태그.
    expect(wrapper.find('.candidate .flex-tag').text()).toBe(i18n.global.t('home.flex.0'))
  })

  it('저축률을 표시한다 — 투자 포함이면 포함 표시(SET-02)', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockResolvedValue(NORMAL)
    const wrapper = mountView()
    await flushPromises()

    const savings = wrapper.find('.savings')
    expect(savings.exists()).toBe(true)
    expect(savings.text()).toContain(i18n.global.t('home.savingsRate', { percent: '60.0' }))
    expect(savings.text()).toContain(i18n.global.t('home.savingsWithInvestment'))
  })

  it('투자 제외로 산정된 저축률은 제외 표시를 붙인다(SET-02)', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockResolvedValue({
      ...NORMAL,
      savingsRate: { value: 28.3, includesInvestment: false },
    })
    const wrapper = mountView()
    await flushPromises()

    const savings = wrapper.find('.savings')
    expect(savings.text()).toContain(i18n.global.t('home.savingsRate', { percent: '28.3' }))
    expect(savings.text()).toContain(i18n.global.t('home.savingsWithoutInvestment'))
  })

  it('조회 실패 시 에러 코드를 표시한다', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockRejectedValue(new ApiError('INTERNAL_ERROR', {}, 500))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.INTERNAL_ERROR'))
  })
})
