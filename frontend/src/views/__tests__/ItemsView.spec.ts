import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import ItemsView from '../ItemsView.vue'
import { ApiError } from '@/api/client'
import * as itemsApi from '@/api/budgetItems'
import * as accountsApi from '@/api/accounts'
import type { BudgetItem } from '@/api/budgetItems'
import type { Account } from '@/api/accounts'

// 함수만 모킹하고 CATEGORIES·TAX_TYPES 상수·타입은 실제 값을 유지(ItemFormSheet가 칩 렌더에 사용).
vi.mock('@/api/budgetItems', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/budgetItems')>()
  return {
    ...actual,
    listBudgetItems: vi.fn<typeof actual.listBudgetItems>(),
    createBudgetItem: vi.fn<typeof actual.createBudgetItem>(),
    updateBudgetItem: vi.fn<typeof actual.updateBudgetItem>(),
    deleteBudgetItem: vi.fn<typeof actual.deleteBudgetItem>(),
    previewMaturity: vi.fn<typeof actual.previewMaturity>(),
    previewFx: vi.fn<typeof actual.previewFx>(),
    previewDaily: vi.fn<typeof actual.previewDaily>(),
  }
})
vi.mock('@/api/accounts')

const KAKAO: Account = { id: 1, name: '카카오페이', purpose: '생활비', bankDeepLink: null, sortOrder: 0 }
const TOSS: Account = { id: 2, name: '토스', purpose: null, bankDeepLink: null, sortOrder: 1 }

const SAVINGS: BudgetItem = {
  id: 10,
  category: 'SAVING',
  name: 'OO은행 정기적금',
  amount: 300000,
  accountId: 1,
  startDate: '2026-07-01',
  endDate: null,
  interestRate: null,
  taxType: null,
  expectedMaturityAmount: null,
  memo: null,
  sortOrder: 0,
  inputCycle: 'MONTHLY',
  inputMeta: null,
}

// Teleport를 인라인 렌더해 시트 내부 요소를 wrapper.find로 찾을 수 있게 한다(MOD-03 동일 패턴).
function mountView() {
  return mount(ItemsView, {
    global: { plugins: [router, i18n], stubs: { teleport: true } },
  })
}

// ko 로케일 버튼 텍스트로 시트 버튼을 집는다(폼·목록에 .btn이 여럿이라 텍스트로 식별).
function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((b) => b.text() === text)
}

describe('ItemsView (MOD-01 항목 관리)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(itemsApi.listBudgetItems).mockReset()
    vi.mocked(itemsApi.createBudgetItem).mockReset()
    vi.mocked(itemsApi.updateBudgetItem).mockReset()
    vi.mocked(itemsApi.deleteBudgetItem).mockReset()
    vi.mocked(itemsApi.previewMaturity).mockReset()
    vi.mocked(itemsApi.previewFx).mockReset()
    vi.mocked(itemsApi.previewDaily).mockReset()
    vi.mocked(accountsApi.listAccounts).mockReset()
    i18n.global.locale.value = 'ko'
  })

  it('마운트 시 항목과 통장을 불러와 표시한다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([SAVINGS])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO, TOSS])
    const wrapper = mountView()
    await flushPromises()

    expect(itemsApi.listBudgetItems).toHaveBeenCalledOnce()
    expect(accountsApi.listAccounts).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('OO은행 정기적금')
    // 카테고리 라벨 + 대상 통장명이 메타로 표시된다.
    expect(wrapper.text()).toContain(i18n.global.t('items.category.SAVING'))
    expect(wrapper.text()).toContain('카카오페이')
  })

  it('항목이 없으면 빈 상태를 보여준다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('items.emptyTitle'))
  })

  it('추가 → 폼 작성 → 저장하면 createBudgetItem 호출 후 목록을 다시 읽어 반영한다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValueOnce([]).mockResolvedValueOnce([SAVINGS])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.createBudgetItem).mockResolvedValue(SAVINGS)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-name').setValue('OO은행 정기적금')
    await wrapper.find('#item-amount').setValue('300000')
    await wrapper.find('#item-account').setValue('1')
    await wrapper.find('#item-start').setValue('2026-07-01')
    await buttonByText(wrapper, i18n.global.t('items.form.save'))!.trigger('click')
    await flushPromises()

    // 저축이지만 만기일·이율을 비워두면 endDate만 null로 실리고 이율·세금은 생략된다(ITEM-05 선택).
    expect(itemsApi.createBudgetItem).toHaveBeenCalledWith({
      category: 'SAVING',
      name: 'OO은행 정기적금',
      amount: 300000,
      accountId: 1,
      startDate: '2026-07-01',
      endDate: null,
    })
    expect(itemsApi.listBudgetItems).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('OO은행 정기적금')
  })

  it('카테고리 칩을 바꾸면 선택값이 생성 요청에 반영된다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.createBudgetItem).mockResolvedValue(SAVINGS)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-name').setValue('ISA 계좌')
    await wrapper.find('#item-amount').setValue('500000')
    await wrapper.find('#item-account').setValue('1')
    await buttonByText(wrapper, i18n.global.t('items.category.INVESTMENT'))!.trigger('click')
    await buttonByText(wrapper, i18n.global.t('items.form.save'))!.trigger('click')
    await flushPromises()

    expect(itemsApi.createBudgetItem).toHaveBeenCalledWith(
      expect.objectContaining({ category: 'INVESTMENT', name: 'ISA 계좌', amount: 500000 }),
    )
  })

  it('이름이 비면 저장 시 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-amount').setValue('300000')
    await wrapper.find('#item-account').setValue('1')
    await buttonByText(wrapper, i18n.global.t('items.form.save'))!.trigger('click')
    await flushPromises()

    expect(itemsApi.createBudgetItem).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('대상 통장 미선택이면 저장 시 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-name').setValue('적금')
    await wrapper.find('#item-amount').setValue('300000')
    // 통장 선택 안 함
    await buttonByText(wrapper, i18n.global.t('items.form.save'))!.trigger('click')
    await flushPromises()

    expect(itemsApi.createBudgetItem).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('통장이 하나도 없으면 안내를 띄우고 저장 버튼을 막는다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain(i18n.global.t('items.form.noAccounts'))
    const save = buttonByText(wrapper, i18n.global.t('items.form.save'))!
    expect((save.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('행을 누르면 수정 모드로 열려 기존 값을 프리필한다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([SAVINGS])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.row').trigger('click')
    await wrapper.vm.$nextTick()
    // ITEM-07: 수정 모드는 기존 항목 값으로 프리필된 편집 입력란을 보여준다.
    expect((wrapper.find('#item-name').element as HTMLInputElement).value).toBe('OO은행 정기적금')
    expect((wrapper.find('#item-amount').element as HTMLInputElement).value).toBe('300,000')
    expect((wrapper.find('#item-start').element as HTMLInputElement).value).toBe('2026-07-01')
  })

  it('수정 모드에서 값을 바꿔 저장하면 기본은 다음 사이클 적용(applyToCurrentCycle=false)으로 PATCH한다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([SAVINGS])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.updateBudgetItem).mockResolvedValue(SAVINGS)
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.row').trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-amount').setValue('350000')
    await buttonByText(wrapper, i18n.global.t('items.form.save'))!.trigger('click')
    await flushPromises()

    // endDate/memo는 v1 입력란이 없어 원본 값(null)을 그대로 실어 전체 교체한다.
    expect(itemsApi.updateBudgetItem).toHaveBeenCalledWith(
      10,
      {
        category: 'SAVING',
        name: 'OO은행 정기적금',
        amount: 350000,
        accountId: 1,
        startDate: '2026-07-01',
        endDate: null,
        memo: null,
      },
      false,
    )
    expect(itemsApi.listBudgetItems).toHaveBeenCalledTimes(2)
  })

  it("'이번 달에 바로 반영' 토글을 켜면 applyToCurrentCycle=true로 PATCH한다", async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([SAVINGS])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.updateBudgetItem).mockResolvedValue(SAVINGS)
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.row').trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-apply').setValue(true)
    await buttonByText(wrapper, i18n.global.t('items.form.save'))!.trigger('click')
    await flushPromises()

    expect(itemsApi.updateBudgetItem).toHaveBeenCalledWith(10, expect.any(Object), true)
  })

  it('수정 모드에서 삭제 2단 확인 후 deleteBudgetItem을 호출한다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([SAVINGS])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.deleteBudgetItem).mockResolvedValue()
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.row').trigger('click')
    await wrapper.vm.$nextTick()

    // 첫 삭제 클릭은 확인 단계로 전환만 한다(즉시 삭제 안 함).
    await buttonByText(wrapper, i18n.global.t('items.form.delete'))!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(itemsApi.deleteBudgetItem).not.toHaveBeenCalled()

    await buttonByText(wrapper, i18n.global.t('items.form.delete'))!.trigger('click')
    await flushPromises()

    expect(itemsApi.deleteBudgetItem).toHaveBeenCalledWith(10)
  })

  it('100개 상한 초과 시 서버의 ITEM_LIMIT_EXCEEDED를 표시한다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.createBudgetItem).mockRejectedValue(new ApiError('ITEM_LIMIT_EXCEEDED', {}, 409))
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-name').setValue('적금')
    await wrapper.find('#item-amount').setValue('300000')
    await wrapper.find('#item-account').setValue('1')
    await buttonByText(wrapper, i18n.global.t('items.form.save'))!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.ITEM_LIMIT_EXCEEDED'))
  })

  // ── 저축 조건부 필드(ITEM-05/06) ──────────────────────────────────────

  it('저축 선택 시 만기일·이율·세금유형이 보이고, 비저축으로 바꾸면 숨는다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    // 기본 카테고리는 SAVING이라 조건부 필드가 보인다.
    expect(wrapper.find('#item-end').exists()).toBe(true)
    expect(wrapper.find('#item-rate').exists()).toBe(true)
    expect(wrapper.text()).toContain(i18n.global.t('items.taxType.PREFERENTIAL'))

    // 고정지출로 바꾸면 저축 조건부 필드가 사라진다.
    await buttonByText(wrapper, i18n.global.t('items.category.FIXED'))!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('#item-end').exists()).toBe(false)
    expect(wrapper.find('#item-rate').exists()).toBe(false)
  })

  it('금액·만기일·이율이 갖춰지면 previewMaturity로 예상 만기금액 배너를 표시한다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.previewMaturity).mockResolvedValue({
      principal: 3_600_000,
      interest: 156_000,
      tax: 24_024,
      total: 3_731_976,
    })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-start').setValue('2026-07-01')
    await wrapper.find('#item-end').setValue('2027-06-30')
    await wrapper.find('#item-amount').setValue('300000')
    await wrapper.find('#item-rate').setValue('8.0')
    await flushPromises()

    // 개월 수는 시작~만기(end-inclusive)로 12개월, 세금유형 기본 일반과세.
    expect(itemsApi.previewMaturity).toHaveBeenLastCalledWith({
      monthlyAmount: 300000,
      months: 12,
      interestRate: 8,
      taxType: 'NORMAL_15_4',
    })
    const banner = wrapper.find('[data-testid="maturity-preview"]')
    expect(banner.exists()).toBe(true)
    expect(banner.text()).toContain('3,731,976')
  })

  it('저축 항목을 이율·세금유형·만기일과 함께 생성하면 조건부 필드가 페이로드에 실린다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.createBudgetItem).mockResolvedValue(SAVINGS)
    vi.mocked(itemsApi.previewMaturity).mockResolvedValue({
      principal: 0,
      interest: 0,
      tax: 0,
      total: 0,
    })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-name').setValue('OO적금')
    await wrapper.find('#item-amount').setValue('300000')
    await wrapper.find('#item-account').setValue('1')
    await wrapper.find('#item-start').setValue('2026-07-01')
    await wrapper.find('#item-end').setValue('2027-06-30')
    await wrapper.find('#item-rate').setValue('4.5')
    await buttonByText(wrapper, i18n.global.t('items.taxType.PREFERENTIAL'))!.trigger('click')
    await buttonByText(wrapper, i18n.global.t('items.form.save'))!.trigger('click')
    await flushPromises()

    expect(itemsApi.createBudgetItem).toHaveBeenCalledWith({
      category: 'SAVING',
      name: 'OO적금',
      amount: 300000,
      accountId: 1,
      startDate: '2026-07-01',
      endDate: '2027-06-30',
      interestRate: 4.5,
      taxType: 'PREFERENTIAL',
    })
  })

  it('특수 상품 수동 입력을 켜면 예상 만기금액만 실리고 이율·세금유형은 빠진다(ITEM-06)', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.createBudgetItem).mockResolvedValue(SAVINGS)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-name').setValue('청년도약계좌')
    await wrapper.find('#item-amount').setValue('700000')
    await wrapper.find('#item-account').setValue('1')
    await wrapper.find('#item-start').setValue('2026-07-01')
    await wrapper.find('#item-end').setValue('2031-06-30')
    await wrapper.find('#item-manual').setValue(true)
    await wrapper.vm.$nextTick()
    // 수동 모드에선 이율 입력란이 사라지고 예상 만기금액 입력란이 나타난다.
    expect(wrapper.find('#item-rate').exists()).toBe(false)
    await wrapper.find('#item-expected').setValue('50000000')
    await buttonByText(wrapper, i18n.global.t('items.form.save'))!.trigger('click')
    await flushPromises()

    expect(itemsApi.createBudgetItem).toHaveBeenCalledWith({
      category: 'SAVING',
      name: '청년도약계좌',
      amount: 700000,
      accountId: 1,
      startDate: '2026-07-01',
      endDate: '2031-06-30',
      expectedMaturityAmount: 50000000,
    })
  })

  it('수동 예상금액이 있던 항목은 수정 시 수동 입력 모드로 프리필된다', async () => {
    const manualItem: BudgetItem = {
      ...SAVINGS,
      id: 11,
      name: '청년도약계좌',
      expectedMaturityAmount: 50_000_000,
    }
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([manualItem])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.row').trigger('click')
    await wrapper.vm.$nextTick()

    expect((wrapper.find('#item-manual').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('#item-expected').element as HTMLInputElement).value).toBe('50,000,000')
    expect(wrapper.find('#item-rate').exists()).toBe(false)
  })

  // ── 외화 적립 도우미(ITEM-04) ─────────────────────────────────────────

  it('투자 항목에서만 외화 도우미 토글이 보인다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    // 기본 카테고리 SAVING에는 외화 도우미가 없다.
    expect(wrapper.find('#item-fx').exists()).toBe(false)

    await buttonByText(wrapper, i18n.global.t('items.category.INVESTMENT'))!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('#item-fx').exists()).toBe(true)
  })

  it('투자+외화 도우미를 켜고 금액·환율을 넣으면 previewFx로 권장 월 이체액 배너를 표시한다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.previewFx).mockResolvedValue({ recommendedMonthlyKrw: 224_000, bufferRate: 0.05 })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await buttonByText(wrapper, i18n.global.t('items.category.INVESTMENT'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-fx').setValue(true)
    await wrapper.vm.$nextTick()
    // 외화 입력란이 펼쳐진다(빈도 기본 영업일).
    expect(wrapper.find('#item-fx-unit').exists()).toBe(true)
    await wrapper.find('#item-fx-unit').setValue('7')
    await wrapper.find('#item-fx-rate').setValue('1380')
    await flushPromises()

    expect(itemsApi.previewFx).toHaveBeenLastCalledWith({
      currency: 'USD',
      unitAmount: 7,
      frequency: 'BUSINESS_DAYS',
      fxRate: 1380,
    })
    const banner = wrapper.find('[data-testid="fx-preview"]')
    expect(banner.exists()).toBe(true)
    expect(banner.text()).toContain('224,000')
  })

  it("'이 금액으로 채우기'를 누르면 권장 월액이 금액에 채워져 생성 페이로드에 실린다", async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.createBudgetItem).mockResolvedValue(SAVINGS)
    vi.mocked(itemsApi.previewFx).mockResolvedValue({ recommendedMonthlyKrw: 224_000, bufferRate: 0.05 })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await buttonByText(wrapper, i18n.global.t('items.category.INVESTMENT'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-name').setValue('달러 적립')
    await wrapper.find('#item-account').setValue('1')
    await wrapper.find('#item-fx').setValue(true)
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-fx-unit').setValue('7')
    await wrapper.find('#item-fx-rate').setValue('1380')
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.form.fxApply'))!.trigger('click')
    await wrapper.vm.$nextTick()
    // 권장 월액이 금액 입력에 채워진다(저장은 원화 월액 — ITEM-04).
    expect((wrapper.find('#item-amount').element as HTMLInputElement).value).toBe('224,000')

    await buttonByText(wrapper, i18n.global.t('items.form.save'))!.trigger('click')
    await flushPromises()

    expect(itemsApi.createBudgetItem).toHaveBeenCalledWith(
      expect.objectContaining({ category: 'INVESTMENT', name: '달러 적립', amount: 224_000, accountId: 1 }),
    )
  })

  // ── 일 단위 입력(ITEM-03) ─────────────────────────────────────────────

  it('일 단위 입력 토글을 켜면 일 금액·빈도 입력란이 나오고 월 금액 입력란은 숨는다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    // 기본은 월 금액 입력란.
    expect(wrapper.find('#item-amount').exists()).toBe(true)
    expect(wrapper.find('#item-daily-amount').exists()).toBe(false)

    await wrapper.find('#item-daily').setValue(true)
    await wrapper.vm.$nextTick()
    // 일 단위로 바뀌면 월 금액 입력란은 사라지고 일 금액 입력란이 나온다.
    expect(wrapper.find('#item-amount').exists()).toBe(false)
    expect(wrapper.find('#item-daily-amount').exists()).toBe(true)
  })

  it('일 금액·빈도를 넣으면 previewDaily로 월 환산 미리보기 배너를 표시한다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.previewDaily).mockResolvedValue({ monthlyAmount: 300_000 })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-daily').setValue(true)
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-daily-amount').setValue('10000')
    await flushPromises()

    // 빈도 기본 매일(DAILY) — 일 10,000 × 30 = 300,000.
    expect(itemsApi.previewDaily).toHaveBeenLastCalledWith({ dailyAmount: 10000, frequency: 'DAILY' })
    const banner = wrapper.find('[data-testid="daily-preview"]')
    expect(banner.exists()).toBe(true)
    expect(banner.text()).toContain('300,000')
  })

  it('일 단위 항목을 생성하면 inputCycle·inputMeta가 실리고 amount는 보내지 않는다(서버 환산)', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.createBudgetItem).mockResolvedValue(SAVINGS)
    vi.mocked(itemsApi.previewDaily).mockResolvedValue({ monthlyAmount: 220_000 })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await buttonByText(wrapper, i18n.global.t('items.category.INVESTMENT'))!.trigger('click')
    await wrapper.find('#item-name').setValue('매일적립')
    await wrapper.find('#item-account').setValue('1')
    await wrapper.find('#item-daily').setValue(true)
    await wrapper.vm.$nextTick()
    await wrapper.find('#item-daily-amount').setValue('10000')
    // 빈도 영업일로 변경.
    await buttonByText(wrapper, i18n.global.t('items.form.fxFreq.BUSINESS_DAYS'))!.trigger('click')
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('items.form.save'))!.trigger('click')
    await flushPromises()

    const payload = vi.mocked(itemsApi.createBudgetItem).mock.calls[0]![0]
    expect(payload).toMatchObject({
      category: 'INVESTMENT',
      name: '매일적립',
      accountId: 1,
      inputCycle: 'DAILY',
      inputMeta: { dailyAmount: 10000, frequency: 'BUSINESS_DAYS' },
    })
    expect(payload.amount).toBeUndefined()
  })

  it('일 단위 항목은 수정 시 일 단위 모드로 프리필된다', async () => {
    const dailyItem: BudgetItem = {
      ...SAVINGS,
      id: 12,
      name: '매일적립',
      amount: 300_000,
      inputCycle: 'DAILY',
      inputMeta: { dailyAmount: 10_000, frequency: 'DAILY' },
    }
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([dailyItem])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.row').trigger('click')
    await wrapper.vm.$nextTick()

    expect((wrapper.find('#item-daily').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('#item-daily-amount').element as HTMLInputElement).value).toBe('10,000')
    expect(wrapper.find('#item-amount').exists()).toBe(false)
  })
})
