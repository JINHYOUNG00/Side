import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import i18n from '@/i18n'
import ChecklistCard from '../ChecklistCard.vue'
import { ApiError } from '@/api/client'
import * as cycleApi from '@/api/cycle'
import type { CurrentCycle, PlanLineStatus } from '@/api/cycle'
import * as meApi from '@/api/me'

vi.mock('@/api/cycle')
// @/api/me는 상수(PAYDAY_ADJUSTMENTS)+함수 혼합 모듈 — ProfileEditSheet가 상수를 v-for로 쓰므로
// 전체 모킹하면 깨진다. importOriginal로 상수는 살리고 getMe/updateMe만 모킹한다.
vi.mock('@/api/me', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/me')>()
  return {
    ...actual,
    getMe: vi.fn<() => Promise<meApi.Me>>(),
    updateMe: vi.fn<(input: meApi.MeUpdate) => Promise<meApi.Me>>(),
  }
})

// 현재 설정 — 월급일 수정 시트(ProfileEditSheet)를 채운다.
const ME: meApi.Me = {
  id: 1,
  email: null,
  nickname: 'me',
  baseIncome: 2500000,
  payday: 25,
  paydayAdjustment: 'PREV_BUSINESS_DAY',
  includeInvestmentInSavingsRate: true,
  locale: 'ko',
  livingAccountId: 7,
}

// 노션류 실데이터 — 지급일 2026-06-25, 통장 3그룹(국민 700,000 / 케이뱅크 300,000[월세 DONE·통신 PENDING] /
// 생활비통장 LIVING 356,107). 처리됨(PENDING 아님) 라인 = 월세 1건 → 진행도 1/4.
function fixture(): CurrentCycle {
  return {
    id: 12,
    label: '2026-06',
    cycleStart: '2026-06-25',
    cycleEnd: '2026-07-24',
    income: 2500000,
    incomeConfirmed: false,
    checklist: [
      { accountId: 3, accountName: '국민', total: 700000, lines: [{ id: 101, name: '청년도약', plannedAmount: 700000, status: 'PENDING' }] },
      {
        accountId: 5,
        accountName: '케이뱅크',
        total: 300000,
        lines: [
          { id: 102, name: '월세', plannedAmount: 200000, status: 'DONE' },
          { id: 103, name: '통신', plannedAmount: 100000, status: 'PENDING' },
        ],
      },
      { accountId: 7, accountName: '생활비통장', total: 356107, lines: [{ id: 104, name: 'LIVING', plannedAmount: 356107, status: 'PENDING' }] },
    ],
    progress: { done: 1, total: 4 },
  }
}

function mountCard() {
  return mount(ChecklistCard, { global: { plugins: [i18n] } })
}

// 라인 상태 전이 mock — 컴포넌트는 응답의 status만 읽어 로컬 라인에 반영한다.
function stubChangeStatus() {
  vi.mocked(cycleApi.changeLineStatus).mockImplementation((id: number, status: PlanLineStatus) =>
    Promise.resolve({ id, name: '', plannedAmount: 0, status }),
  )
}

describe('ChecklistCard (SCR-03b 월급날 체크리스트)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    // Date만 고정 — 타이머·마이크로태스크는 실제 유지(@vue/test-utils 안정).
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.mocked(cycleApi.getCurrentCycle).mockReset()
    vi.mocked(cycleApi.confirmIncome).mockReset()
    vi.mocked(cycleApi.changeLineStatus).mockReset()
    vi.mocked(cycleApi.recalibrateCurrentCycle).mockReset()
    vi.mocked(meApi.getMe).mockReset()
    vi.mocked(meApi.updateMe).mockReset()
    // 기본: 현재 설정 로드 성공(월급일 보정 버튼 노출 조건). 개별 테스트에서 필요 시 재정의.
    vi.mocked(meApi.getMe).mockResolvedValue({ ...ME })
    vi.mocked(meApi.updateMe).mockImplementation(async (input) => ({ ...ME, ...input }) as meApi.Me)
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('지급일 당일이면 카드를 노출하고 실수령액·통장별 라인·진행도를 표시한다', async () => {
    vi.setSystemTime(new Date('2026-06-25T10:00:00+09:00'))
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(fixture())
    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.find('.checklist-card').exists()).toBe(true)
    // 실수령액
    expect(wrapper.find('.income-amt').text()).toContain('2,500,000')
    // 라인 4개(국민 1 + 케이뱅크 2 + 생활비 1), LIVING은 i18n 라벨로.
    const lines = wrapper.findAll('.line')
    expect(lines).toHaveLength(4)
    expect(wrapper.text()).toContain('청년도약')
    expect(wrapper.text()).toContain('월세')
    expect(wrapper.text()).toContain(i18n.global.t('home.living'))
    // 진행도: 처리됨(월세 DONE) 1 / 전체 4.
    expect(wrapper.find('.progress-caption').text()).toBe(i18n.global.t('checklist.progress', { done: 1, total: 4 }))
    // 통장 그룹 헤더 3개.
    expect(wrapper.findAll('.group')).toHaveLength(3)
  })

  it('지급일~D+3 구간을 벗어나면 카드를 숨긴다', async () => {
    vi.setSystemTime(new Date('2026-06-30T10:00:00+09:00')) // D+5
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(fixture())
    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.find('.checklist-card').exists()).toBe(false)
  })

  it('forceOpen이면 지급일 구간 밖에도 카드를 노출하고 닫기를 보여준다', async () => {
    vi.setSystemTime(new Date('2026-06-30T10:00:00+09:00')) // D+5, 구간 밖
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(fixture())
    const wrapper = mount(ChecklistCard, { props: { forceOpen: true }, global: { plugins: [i18n] } })
    await flushPromises()

    expect(wrapper.find('.checklist-card').exists()).toBe(true)
    expect(wrapper.find('.close-forced').exists()).toBe(true)
  })

  it('forceOpen 닫기를 누르면 close 이벤트를 낸다', async () => {
    vi.setSystemTime(new Date('2026-06-30T10:00:00+09:00'))
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(fixture())
    const wrapper = mount(ChecklistCard, { props: { forceOpen: true }, global: { plugins: [i18n] } })
    await flushPromises()

    await wrapper.find('.close-forced').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('스냅샷이 없으면(404) 조용히 숨긴다', async () => {
    vi.setSystemTime(new Date('2026-06-25T10:00:00+09:00'))
    vi.mocked(cycleApi.getCurrentCycle).mockRejectedValue(new ApiError('NOT_FOUND', {}, 404))
    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.find('.checklist-card').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('라인 체크를 누르면 DONE으로 전이하고 진행도가 갱신된다', async () => {
    vi.setSystemTime(new Date('2026-06-25T10:00:00+09:00'))
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(fixture())
    stubChangeStatus()
    const wrapper = mountCard()
    await flushPromises()

    // 첫 라인(청년도약, PENDING) 체크.
    await wrapper.findAll('.line')[0]!.find('.check').trigger('click')
    await flushPromises()

    expect(cycleApi.changeLineStatus).toHaveBeenCalledWith(101, 'DONE')
    expect(wrapper.findAll('.line')[0]!.classes()).toContain('done')
    // 처리됨 2 / 4.
    expect(wrapper.find('.progress-caption').text()).toBe(i18n.global.t('checklist.progress', { done: 2, total: 4 }))
  })

  it('건너뛰기를 누르면 SKIPPED로 전이한다', async () => {
    vi.setSystemTime(new Date('2026-06-25T10:00:00+09:00'))
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(fixture())
    stubChangeStatus()
    const wrapper = mountCard()
    await flushPromises()

    // 첫 라인의 액션 버튼은 PENDING이라 '건너뛰기'.
    const action = wrapper.findAll('.line')[0]!.find('.line-action')
    expect(action.text()).toBe(i18n.global.t('checklist.skip'))
    await action.trigger('click')
    await flushPromises()

    expect(cycleApi.changeLineStatus).toHaveBeenCalledWith(101, 'SKIPPED')
    expect(wrapper.findAll('.line')[0]!.classes()).toContain('skipped')
  })

  it('실수령액 수정 시 confirmIncome 호출 후 체크리스트를 재조회한다', async () => {
    vi.setSystemTime(new Date('2026-06-25T10:00:00+09:00'))
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(fixture())
    vi.mocked(cycleApi.confirmIncome).mockResolvedValue({
      id: 12,
      label: '2026-06',
      cycleStart: '2026-06-25',
      cycleEnd: '2026-07-24',
      income: 3000000,
      incomeConfirmed: true,
    })
    const wrapper = mountCard()
    await flushPromises()

    await wrapper.find('.edit').trigger('click')
    // 시트는 body로 teleport된다 — document에서 입력·저장 버튼을 찾는다.
    const input = document.body.querySelector('input[aria-label="income"]') as HTMLInputElement
    expect(input).not.toBeNull()
    input.value = '3,000,000'
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    const saveBtn = Array.from(document.body.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === i18n.global.t('checklist.save'),
    ) as HTMLButtonElement
    saveBtn.click()
    await flushPromises()

    expect(cycleApi.confirmIncome).toHaveBeenCalledWith(12, 3000000)
    // 재조회(마운트 1회 + 확인 후 1회).
    expect(cycleApi.getCurrentCycle).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('월급일을 수정하면 현재 사이클을 재보정하고 체크리스트를 다시 받는다', async () => {
    vi.setSystemTime(new Date('2026-06-25T10:00:00+09:00'))
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(fixture())
    vi.mocked(cycleApi.recalibrateCurrentCycle).mockResolvedValue({
      id: 12,
      label: '2026-06',
      cycleStart: '2026-06-05',
      cycleEnd: '2026-07-04',
      income: 2500000,
      incomeConfirmed: false,
    })
    const wrapper = mountCard()
    await flushPromises()

    // '월급날이 틀렸나요? 다시 만들기' → 월급일 수정 시트(ProfileEditSheet, teleport)에서 저장.
    await wrapper.find('.wrong-payday').trigger('click')
    const saveBtn = Array.from(document.body.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === i18n.global.t('menu.profileSave'),
    ) as HTMLButtonElement
    expect(saveBtn).not.toBeUndefined()
    saveBtn.click()
    await flushPromises()

    expect(meApi.updateMe).toHaveBeenCalled()
    expect(cycleApi.recalibrateCurrentCycle).toHaveBeenCalledOnce()
    // 재보정 후 체크리스트 재조회(마운트 1회 + 재보정 후 1회).
    expect(cycleApi.getCurrentCycle).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('이미 이체를 시작했으면(409) 재보정 잠금 안내를 노출한다', async () => {
    vi.setSystemTime(new Date('2026-06-25T10:00:00+09:00'))
    vi.mocked(cycleApi.getCurrentCycle).mockResolvedValue(fixture())
    vi.mocked(cycleApi.recalibrateCurrentCycle).mockRejectedValue(new ApiError('CYCLE_LOCKED', {}, 409))
    const wrapper = mountCard()
    await flushPromises()

    await wrapper.find('.wrong-payday').trigger('click')
    const saveBtn = Array.from(document.body.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === i18n.global.t('menu.profileSave'),
    ) as HTMLButtonElement
    saveBtn.click()
    await flushPromises()

    expect(cycleApi.recalibrateCurrentCycle).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain(i18n.global.t('checklist.recalLocked'))

    wrapper.unmount()
  })
})
