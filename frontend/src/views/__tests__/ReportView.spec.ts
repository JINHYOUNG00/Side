import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import ReportView from '../ReportView.vue'
import { ApiError } from '@/api/client'
import * as reportsApi from '@/api/reports'
import * as checkinApi from '@/api/checkin'
import * as cycleApi from '@/api/cycle'
import type { ReportSummary, TrendPoint } from '@/api/reports'
import type { CurrentCycle } from '@/api/cycle'

// reports·checkin·cycle 모듈은 함수·타입만 export(상수 없음)라 전체 모킹이 안전하다([[vimock-breaks-const-exports]] 비해당).
vi.mock('@/api/reports')
vi.mock('@/api/checkin')
vi.mock('@/api/cycle')

// 요약 — 저축률 60.7%(투자 포함)·만기 수령 누적 3,000,000(2건 중 1건)·봉투 집행 80,000.
const SUMMARY: ReportSummary = {
  savingsRate: { value: 60.7, includesInvestment: true },
  maturity: { archivedCount: 2, recordedCount: 1, totalReceivedAmount: 3000000 },
  envelopeSpentTotal: 80000,
}

// 추이 — 체크인된 사이클 2개(달성/초과) + 결측 1개(체크인 미수행).
const TREND: TrendPoint[] = [
  { label: '2026-04', planned: 375000, actual: 360000, checkedIn: true },
  { label: '2026-05', planned: 375000, actual: 387000, checkedIn: true },
  { label: '2026-06', planned: 375000, actual: null, checkedIn: false },
]

const CYCLE: CurrentCycle = {
  id: 12,
  label: '2026-06',
  cycleStart: '2026-06-25',
  cycleEnd: '2026-07-24',
  income: 2473110,
  incomeConfirmed: true,
  checklist: [],
  progress: { done: 0, total: 0 },
}

function mountView() {
  return mount(ReportView, {
    global: { plugins: [router, i18n], stubs: { teleport: true } },
  })
}

function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((b) => b.text() === text)
}

describe('ReportView (SCR-06 리포트)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'ko'
    vi.mocked(reportsApi.getSummary).mockReset()
    vi.mocked(reportsApi.getTrend).mockReset()
    vi.mocked(checkinApi.recordCheckIn).mockReset()
    vi.mocked(cycleApi.getCurrentCycle).mockReset()
  })

  it('마운트 시 요약·추이를 불러와 메트릭과 추이 차트를 표시한다', async () => {
    vi.mocked(reportsApi.getSummary).mockResolvedValue(SUMMARY)
    vi.mocked(reportsApi.getTrend).mockResolvedValue(TREND)
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(CYCLE)
    const wrapper = mountView()
    await flushPromises()

    expect(reportsApi.getSummary).toHaveBeenCalledOnce()
    expect(reportsApi.getTrend).toHaveBeenCalledOnce()
    // 저축률(투자 포함)·만기 수령 누적·봉투 집행.
    expect(wrapper.text()).toContain('60.7%')
    expect(wrapper.text()).toContain(i18n.global.t('report.withInvestment'))
    expect(wrapper.text()).toContain('3,000,000')
    expect(wrapper.text()).toContain('80,000')
    // 추이 차트 — 범례 + 사이클 월 라벨.
    expect(wrapper.text()).toContain(i18n.global.t('report.trendTitle'))
    expect(wrapper.text()).toContain('04')
    expect(wrapper.text()).toContain('05')
  })

  it('결측 사이클(체크인 미수행)은 미체크인으로 구분 표시한다', async () => {
    vi.mocked(reportsApi.getSummary).mockResolvedValue(SUMMARY)
    vi.mocked(reportsApi.getTrend).mockResolvedValue(TREND)
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(CYCLE)
    const wrapper = mountView()
    await flushPromises()

    // 결측은 actual 바 대신 dashed placeholder(.bar.missing) + dim 컬럼.
    expect(wrapper.find('.bar.missing').exists()).toBe(true)
    expect(wrapper.findAll('.col.dim').length).toBe(1)
  })

  it('추이가 비면 빈 상태(RPT-03)를 보여준다', async () => {
    vi.mocked(reportsApi.getSummary).mockResolvedValue(SUMMARY)
    vi.mocked(reportsApi.getTrend).mockResolvedValue([])
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(CYCLE)
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('report.emptyTitle'))
    expect(wrapper.text()).toContain(i18n.global.t('report.emptyBody'))
    // 요약 메트릭은 빈 상태와 무관하게 계속 노출.
    expect(wrapper.text()).toContain('60.7%')
  })

  it('현재 사이클이 있으면 체크인 진입을 보이고, 기록(초과) 후 추이를 다시 읽는다', async () => {
    vi.mocked(reportsApi.getSummary).mockResolvedValue(SUMMARY)
    vi.mocked(reportsApi.getTrend).mockResolvedValue(TREND)
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(CYCLE)
    // overspend 12,000(초과). topped 30,000 − living 18,000.
    vi.mocked(checkinApi.recordCheckIn).mockResolvedValue({
      id: 1,
      cycleId: 12,
      livingRemaining: 18000,
      toppedUp: 30000,
      overspend: 12000,
      note: null,
    })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('report.checkInCta'))!.trigger('click')
    await wrapper.vm.$nextTick()

    await wrapper.find('#checkin-living').setValue('18000')
    await wrapper.find('#checkin-topped').setValue('30000')
    await buttonByText(wrapper, i18n.global.t('checkin.submit'))!.trigger('click')
    await flushPromises()

    expect(checkinApi.recordCheckIn).toHaveBeenCalledWith({
      cycleId: 12,
      livingRemaining: 18000,
      toppedUp: 30000,
    })
    // 초과 피드백(절댓값 12,000).
    expect(wrapper.find('[role="status"]').text()).toBe(i18n.global.t('checkin.over', { amount: '12,000' }))

    // 확인 → 추이·요약 재조회.
    await buttonByText(wrapper, i18n.global.t('checkin.confirm'))!.trigger('click')
    await flushPromises()
    expect(reportsApi.getTrend).toHaveBeenCalledTimes(2)
  })

  it('현재 사이클이 없으면(404) 체크인 진입을 숨긴다', async () => {
    vi.mocked(reportsApi.getSummary).mockResolvedValue(SUMMARY)
    vi.mocked(reportsApi.getTrend).mockResolvedValue(TREND)
    vi.mocked(cycleApi.getCurrentCycle).mockRejectedValue(new ApiError('NOT_FOUND', {}, 404))
    const wrapper = mountView()
    await flushPromises()

    // 리포트 자체는 정상 노출(차트), 체크인 CTA만 없음.
    expect(wrapper.text()).toContain(i18n.global.t('report.trendTitle'))
    expect(buttonByText(wrapper, i18n.global.t('report.checkInCta'))).toBeUndefined()
  })

  it('이미 체크인한 사이클이면 CHECK_IN_ALREADY_EXISTS를 노출한다', async () => {
    vi.mocked(reportsApi.getSummary).mockResolvedValue(SUMMARY)
    vi.mocked(reportsApi.getTrend).mockResolvedValue(TREND)
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(CYCLE)
    vi.mocked(checkinApi.recordCheckIn).mockRejectedValue(
      new ApiError('CHECK_IN_ALREADY_EXISTS', {}, 409),
    )
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('report.checkInCta'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#checkin-living').setValue('41000')
    await buttonByText(wrapper, i18n.global.t('checkin.submit'))!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(
      i18n.global.t('errors.CHECK_IN_ALREADY_EXISTS'),
    )
  })

  it('생활비 잔액이 비어 있으면 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(reportsApi.getSummary).mockResolvedValue(SUMMARY)
    vi.mocked(reportsApi.getTrend).mockResolvedValue(TREND)
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(CYCLE)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('report.checkInCta'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await buttonByText(wrapper, i18n.global.t('checkin.submit'))!.trigger('click')
    await flushPromises()

    expect(checkinApi.recordCheckIn).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('요약 조회 실패 시 에러 코드를 표시한다', async () => {
    vi.mocked(reportsApi.getSummary).mockRejectedValue(new ApiError('INTERNAL_ERROR', {}, 500))
    vi.mocked(reportsApi.getTrend).mockResolvedValue(TREND)
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(CYCLE)
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.INTERNAL_ERROR'))
  })
})
