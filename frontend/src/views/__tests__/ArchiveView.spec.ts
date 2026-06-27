import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import ArchiveView from '../ArchiveView.vue'
import { ApiError } from '@/api/client'
import * as budgetItemsApi from '@/api/budgetItems'
import * as accountsApi from '@/api/accounts'
import type { ArchivedItem, ArchiveResponse } from '@/api/budgetItems'
import type { Account } from '@/api/accounts'

// 함수만 모킹하고 CATEGORIES 상수·타입은 실제 값을 유지(부분 모킹 — vi.mock이 상수 export를 깨지 않게).
vi.mock('@/api/budgetItems', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/budgetItems')>()
  return {
    ...actual,
    listArchive: vi.fn<typeof actual.listArchive>(),
    recordMaturity: vi.fn<typeof actual.recordMaturity>(),
  }
})
vi.mock('@/api/accounts')

const KAKAO: Account = { id: 1, name: '카카오페이', purpose: '저축', bankDeepLink: null, sortOrder: 0 }

// 실수령액이 기록된 보관 항목(자연 만기) — 골든 적금 만기 3,731,976원 재현.
const RECORDED: ArchivedItem = {
  id: 10,
  category: 'SAVING',
  name: '신한 적금',
  amount: 300000,
  accountId: 1,
  startDate: '2024-01-25',
  endDate: '2026-01-25',
  expectedMaturityAmount: null,
  maturityActualAmount: 3731976,
  memo: null,
  sortOrder: 0,
}

// 아직 실수령액 미기록인 보관 항목 — 기록 시트로 입력 대상.
const UNRECORDED: ArchivedItem = {
  id: 20,
  category: 'SAVING',
  name: '국민 적금',
  amount: 200000,
  accountId: 1,
  startDate: '2024-03-25',
  endDate: '2026-03-25',
  expectedMaturityAmount: null,
  maturityActualAmount: null,
  memo: null,
  sortOrder: 1,
}

const ARCHIVE: ArchiveResponse = {
  items: [RECORDED, UNRECORDED],
  stats: { archivedCount: 2, recordedCount: 1, totalReceivedAmount: 3731976 },
}

function mountView() {
  return mount(ArchiveView, {
    global: { plugins: [router, i18n], stubs: { teleport: true } },
  })
}

// 정확 일치 버튼(시트 저장 등)은 텍스트 동일로, 행 버튼은 이름 포함으로 집는다.
function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((b) => b.text() === text)
}
function rowByName(wrapper: VueWrapper, name: string) {
  return wrapper.findAll('button').find((b) => b.text().includes(name))
}

describe('ArchiveView (SCR-08 보관함)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(budgetItemsApi.listArchive).mockReset()
    vi.mocked(budgetItemsApi.recordMaturity).mockReset()
    vi.mocked(accountsApi.listAccounts).mockReset()
  })

  it('마운트 시 보관 항목·통장을 불러와 목록과 누적 통계를 표시한다', async () => {
    vi.mocked(budgetItemsApi.listArchive).mockResolvedValue(ARCHIVE)
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    expect(budgetItemsApi.listArchive).toHaveBeenCalledOnce()
    expect(accountsApi.listAccounts).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('신한 적금')
    expect(wrapper.text()).toContain('국민 적금')
    // 누적 수령액(골든 만기 3,731,976)과 보관/기록 건수.
    expect(wrapper.text()).toContain('3,731,976')
    expect(wrapper.text()).toContain(i18n.global.t('archive.stats.received'))
    expect(wrapper.text()).toContain(
      i18n.global.t('archive.stats.counts', { archived: 2, recorded: 1 }),
    )
    // 미기록 항목은 '미기록' 배지를 보인다.
    expect(wrapper.text()).toContain(i18n.global.t('archive.notRecorded'))
  })

  it('보관 항목이 없으면 빈 상태를 보여준다', async () => {
    vi.mocked(budgetItemsApi.listArchive).mockResolvedValue({
      items: [],
      stats: { archivedCount: 0, recordedCount: 0, totalReceivedAmount: 0 },
    })
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('archive.emptyTitle'))
  })

  it('미기록 항목을 눌러 실수령액을 기록하면 recordMaturity 호출 후 목록을 다시 읽는다', async () => {
    vi.mocked(budgetItemsApi.listArchive)
      .mockResolvedValueOnce(ARCHIVE)
      .mockResolvedValueOnce({
        ...ARCHIVE,
        stats: { archivedCount: 2, recordedCount: 2, totalReceivedAmount: 5731976 },
      })
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(budgetItemsApi.recordMaturity).mockResolvedValue({
      ...UNRECORDED,
      maturityActualAmount: 2000000,
    })
    const wrapper = mountView()
    await flushPromises()

    await rowByName(wrapper, '국민 적금')!.trigger('click')
    await wrapper.vm.$nextTick()
    // 미기록 항목이라 입력이 비어 있다.
    expect((wrapper.find('#maturity-actual').element as HTMLInputElement).value).toBe('')

    await wrapper.find('#maturity-actual').setValue('2000000')
    await buttonByText(wrapper, i18n.global.t('archive.recordSheet.save'))!.trigger('click')
    await flushPromises()

    expect(budgetItemsApi.recordMaturity).toHaveBeenCalledWith(20, 2000000)
    expect(budgetItemsApi.listArchive).toHaveBeenCalledTimes(2)
  })

  it('이미 기록된 항목을 누르면 기존 실수령액을 프리필해 정정할 수 있다', async () => {
    vi.mocked(budgetItemsApi.listArchive).mockResolvedValue(ARCHIVE)
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(budgetItemsApi.recordMaturity).mockResolvedValue({
      ...RECORDED,
      maturityActualAmount: 3700000,
    })
    const wrapper = mountView()
    await flushPromises()

    await rowByName(wrapper, '신한 적금')!.trigger('click')
    await wrapper.vm.$nextTick()
    // 기존 기록값(천 단위 표기)으로 프리필.
    expect((wrapper.find('#maturity-actual').element as HTMLInputElement).value).toBe('3,731,976')

    await wrapper.find('#maturity-actual').setValue('3700000')
    await buttonByText(wrapper, i18n.global.t('archive.recordSheet.save'))!.trigger('click')
    await flushPromises()

    expect(budgetItemsApi.recordMaturity).toHaveBeenCalledWith(10, 3700000)
  })

  it('실수령액이 0이면 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(budgetItemsApi.listArchive).mockResolvedValue(ARCHIVE)
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await rowByName(wrapper, '국민 적금')!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#maturity-actual').setValue('0')
    await buttonByText(wrapper, i18n.global.t('archive.recordSheet.save'))!.trigger('click')
    await flushPromises()

    expect(budgetItemsApi.recordMaturity).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('실수령액이 10억을 넘으면 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(budgetItemsApi.listArchive).mockResolvedValue(ARCHIVE)
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await rowByName(wrapper, '국민 적금')!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#maturity-actual').setValue('1000000001') // 10억 + 1
    await buttonByText(wrapper, i18n.global.t('archive.recordSheet.save'))!.trigger('click')
    await flushPromises()

    expect(budgetItemsApi.recordMaturity).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('기록 중 서버가 NOT_FOUND를 주면 에러 코드를 그대로 노출한다', async () => {
    vi.mocked(budgetItemsApi.listArchive).mockResolvedValue(ARCHIVE)
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(budgetItemsApi.recordMaturity).mockRejectedValue(new ApiError('NOT_FOUND', {}, 404))
    const wrapper = mountView()
    await flushPromises()

    await rowByName(wrapper, '국민 적금')!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#maturity-actual').setValue('2000000')
    await buttonByText(wrapper, i18n.global.t('archive.recordSheet.save'))!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.NOT_FOUND'))
  })

  it('목록 조회 실패 시 에러 코드를 표시한다', async () => {
    vi.mocked(budgetItemsApi.listArchive).mockRejectedValue(new ApiError('INTERNAL_ERROR', {}, 500))
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.INTERNAL_ERROR'))
  })
})
