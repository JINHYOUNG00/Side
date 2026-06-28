import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import HomeView from '../HomeView.vue'
import { ApiError } from '@/api/client'
import * as waterfallApi from '@/api/waterfall'
import * as suggestionsApi from '@/api/suggestions'
import * as envelopesApi from '@/api/envelopes'
import * as cycleApi from '@/api/cycle'
import type { Waterfall } from '@/api/waterfall'

vi.mock('@/api/waterfall')
// 체크리스트 카드(SCR-03b)·홈 사이클 요약 위젯이 GET /cycles/current를 호출한다. 폭포 테스트에선
// 자동 모킹으로 빈 응답을 줘 둘 다 미노출(동작은 ChecklistCard.spec.ts / 아래 사이클 위젯 테스트).
vi.mock('@/api/cycle')
// 제안 카드(MOD-06)도 자체적으로 GET /suggestions를 호출한다 — 폭포 테스트에선 빈 제안으로 막아 미노출.
vi.mock('@/api/suggestions')
// 봉투 요약 위젯이 GET /envelopes를 호출한다 — 기본은 빈 목록으로 막아 미노출(아래 봉투 위젯 테스트에서 주입).
vi.mock('@/api/envelopes')

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
    // 보조 위젯은 기본 빈 값 — 폭포 테스트에선 미노출. 각 위젯 테스트에서만 데이터 주입.
    vi.mocked(envelopesApi.listEnvelopes).mockReset()
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([])
    vi.mocked(cycleApi.getCurrentCycle).mockReset()
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

  it('봉투가 있으면 봉투 요약 위젯(총 적립/목표·가장 가까운 지출)을 보여준다', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockResolvedValue(NORMAL)
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([
      { id: 1, accountId: 1, name: '자동차세', targetAmount: 600000, savedAmount: 200000, nextDueDate: '2026-09-01', cycleMonths: 12, memo: null, status: 'ACTIVE', progressPercent: 33, dDay: 65, monthlyAmount: 50000 },
      { id: 2, accountId: 1, name: '명절비', targetAmount: 400000, savedAmount: 100000, nextDueDate: '2026-07-10', cycleMonths: 6, memo: null, status: 'ACTIVE', progressPercent: 25, dDay: 12, monthlyAmount: 50000 },
    ])
    const wrapper = mountView()
    await flushPromises()

    const env = wrapper.find('.env-summary')
    expect(env.exists()).toBe(true)
    expect(env.text()).toContain('300,000') // 총 적립 = 200,000 + 100,000
    expect(env.text()).toContain('1,000,000') // 총 목표 = 600,000 + 400,000
    // 가장 가까운 지출 = dDay 최소(명절비, D-12).
    expect(env.text()).toContain('명절비')
    expect(env.text()).toContain(i18n.global.t('home.ddayFuture', { n: 12 }))
  })

  it('이번 사이클이 있으면 사이클 요약 위젯(기간·이체 진행)을 보여준다', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockResolvedValue(NORMAL)
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue({
      id: 1,
      label: '2026-06',
      cycleStart: '2026-06-05',
      cycleEnd: '2026-07-02',
      income: 2500000,
      incomeConfirmed: true,
      checklist: [],
      progress: { done: 2, total: 7 },
    })
    const wrapper = mountView()
    await flushPromises()

    const cyc = wrapper.find('.cycle-summary')
    expect(cyc.exists()).toBe(true)
    expect(cyc.text()).toContain(i18n.global.t('home.transfers', { done: 2, total: 7 }))
    expect(cyc.text()).toContain('2026-06-05')
  })

  it('사이클 위젯을 누르면 이체 체크리스트가 (지급일 구간 밖에도) 열린다', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockResolvedValue(NORMAL)
    // 구간 밖(과거 지급일)이라 체크리스트는 처음엔 숨김 — 위젯 클릭으로 강제로 연다.
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue({
      id: 1,
      label: '2020-01',
      cycleStart: '2020-01-01',
      cycleEnd: '2020-02-01',
      income: 2500000,
      incomeConfirmed: true,
      checklist: [],
      progress: { done: 0, total: 0 },
    })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('.checklist-card').exists()).toBe(false)
    await wrapper.find('.cycle-summary').trigger('click')
    await flushPromises()
    expect(wrapper.find('.checklist-card').exists()).toBe(true)
  })

  it('조회 실패 시 에러 코드를 표시한다', async () => {
    vi.mocked(waterfallApi.getWaterfall).mockRejectedValue(new ApiError('INTERNAL_ERROR', {}, 500))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.INTERNAL_ERROR'))
  })
})
