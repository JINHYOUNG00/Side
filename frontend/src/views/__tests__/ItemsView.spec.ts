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

// 함수만 모킹하고 CATEGORIES 상수·타입은 실제 값을 유지(ItemFormSheet가 칩 렌더에 사용).
vi.mock('@/api/budgetItems', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/budgetItems')>()
  return {
    ...actual,
    listBudgetItems: vi.fn<typeof actual.listBudgetItems>(),
    createBudgetItem: vi.fn<typeof actual.createBudgetItem>(),
    deleteBudgetItem: vi.fn<typeof actual.deleteBudgetItem>(),
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
  memo: null,
  sortOrder: 0,
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
    vi.mocked(itemsApi.deleteBudgetItem).mockReset()
    vi.mocked(accountsApi.listAccounts).mockReset()
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

    expect(itemsApi.createBudgetItem).toHaveBeenCalledWith({
      category: 'SAVING',
      name: 'OO은행 정기적금',
      amount: 300000,
      accountId: 1,
      startDate: '2026-07-01',
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

  it('행을 누르면 관리 모드로 열려 삭제 2단 확인 후 deleteBudgetItem을 호출한다', async () => {
    vi.mocked(itemsApi.listBudgetItems).mockResolvedValue([SAVINGS])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(itemsApi.deleteBudgetItem).mockResolvedValue()
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.row').trigger('click')
    await wrapper.vm.$nextTick()
    // 관리 모드는 항목 요약을 보여준다(수정 입력란 없음 — ITEM-07 미구현).
    expect(wrapper.find('#item-name').exists()).toBe(false)

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
})
