import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import i18n from '@/i18n'
import ImportSheet from '../ImportSheet.vue'
import { ApiError } from '@/api/client'
import * as itemsApi from '@/api/budgetItems'
import type { Account } from '@/api/accounts'
import type { BudgetItem } from '@/api/budgetItems'

// CATEGORIES 상수·타입은 실제 값 유지(칩 렌더), 생성 함수만 모킹.
vi.mock('@/api/budgetItems', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/budgetItems')>()
  return { ...actual, createBudgetItem: vi.fn<typeof actual.createBudgetItem>() }
})

const KAKAO: Account = { id: 1, name: '카카오페이', purpose: '생활비', bankDeepLink: null, sortOrder: 0 }

// createBudgetItem 응답값(테스트는 호출 인자만 검증하므로 형태만 맞춘다).
function fakeItem(name: string): BudgetItem {
  return {
    id: 1,
    category: 'FIXED',
    name,
    amount: 1000,
    accountId: 1,
    startDate: '2026-06-16',
    endDate: null,
    interestRate: null,
    taxType: null,
    expectedMaturityAmount: null,
    memo: null,
    sortOrder: 0,
    inputCycle: 'MONTHLY',
    inputMeta: null,
  }
}

const TABLE = ['| 도시락 | 22,000원 |', '| 기름값 | 80,000원 |', '| 헬스장 | 39,000원 |'].join('\n')

function mountSheet(accounts: Account[] = [KAKAO]) {
  return mount(ImportSheet, {
    props: { open: true, accounts },
    global: { plugins: [i18n], stubs: { teleport: true } },
  })
}

function registerButton(wrapper: VueWrapper) {
  return wrapper.find('button.btn')
}

describe('ImportSheet (MOD-07 노션 임포트, DATA-01)', () => {
  beforeEach(() => {
    vi.mocked(itemsApi.createBudgetItem).mockReset()
  })

  it('표를 붙여넣으면 후보와 인식 건수 배너를 보여준다', async () => {
    const wrapper = mountSheet()
    await wrapper.find('#import-text').setValue(TABLE)
    await flushPromises()

    expect(wrapper.find('.banner').text()).toBe(i18n.global.t('import.recognized', { n: 3 }))
    expect(wrapper.findAll('.cand')).toHaveLength(3)
    expect(wrapper.text()).toContain('도시락')
    expect(wrapper.text()).toContain('22,000')
  })

  it('등록하면 포함 후보마다 createBudgetItem을 호출하고 imported를 emit한다', async () => {
    vi.mocked(itemsApi.createBudgetItem).mockImplementation((input) =>
      Promise.resolve(fakeItem(input.name)),
    )
    const wrapper = mountSheet()
    await wrapper.find('#import-text').setValue(TABLE)
    await wrapper.find('#import-account').setValue('1')
    await flushPromises()

    await registerButton(wrapper).trigger('click')
    await flushPromises()

    expect(itemsApi.createBudgetItem).toHaveBeenCalledTimes(3)
    // 분류는 기본 FIXED, 대상 통장·시작일은 일괄 적용된다.
    expect(itemsApi.createBudgetItem).toHaveBeenNthCalledWith(1, {
      category: 'FIXED',
      name: '도시락',
      amount: 22000,
      accountId: 1,
      startDate: expect.any(String),
    })
    expect(wrapper.emitted('imported')).toEqual([[3]])
  })

  it('후보 체크를 해제하면 그 항목은 등록에서 제외된다', async () => {
    vi.mocked(itemsApi.createBudgetItem).mockImplementation((input) =>
      Promise.resolve(fakeItem(input.name)),
    )
    const wrapper = mountSheet()
    await wrapper.find('#import-text').setValue(TABLE)
    await wrapper.find('#import-account').setValue('1')
    await flushPromises()

    // 두 번째 후보(기름값) 해제
    await wrapper.findAll('input.check')[1]!.trigger('change')
    await registerButton(wrapper).trigger('click')
    await flushPromises()

    expect(itemsApi.createBudgetItem).toHaveBeenCalledTimes(2)
    // 해제한 기름값은 빠지고 도시락·헬스장만 순서대로 등록된다.
    expect(itemsApi.createBudgetItem).toHaveBeenNthCalledWith(1, expect.objectContaining({ name: '도시락' }))
    expect(itemsApi.createBudgetItem).toHaveBeenNthCalledWith(2, expect.objectContaining({ name: '헬스장' }))
    expect(wrapper.emitted('imported')).toEqual([[2]])
  })

  it('일괄 분류 칩을 바꾸면 생성 요청에 반영된다', async () => {
    vi.mocked(itemsApi.createBudgetItem).mockImplementation((input) =>
      Promise.resolve(fakeItem(input.name)),
    )
    const wrapper = mountSheet()
    await wrapper.find('#import-text').setValue('정기적금\t300000')
    await wrapper.find('#import-account').setValue('1')
    await flushPromises()

    const chips = wrapper.findAll('button.chip')
    const saving = chips.find((c) => c.text() === i18n.global.t('items.category.SAVING'))!
    await saving.trigger('click')
    await registerButton(wrapper).trigger('click')
    await flushPromises()

    expect(itemsApi.createBudgetItem).toHaveBeenCalledWith(
      expect.objectContaining({ category: 'SAVING', name: '정기적금', amount: 300000 }),
    )
  })

  it('대상 통장을 고르지 않으면 등록 버튼이 비활성이다', async () => {
    const wrapper = mountSheet()
    await wrapper.find('#import-text').setValue(TABLE)
    await flushPromises()

    expect((registerButton(wrapper).element as HTMLButtonElement).disabled).toBe(true)
  })

  it('인식된 후보가 없으면 등록 버튼·후보 영역을 띄우지 않는다', async () => {
    const wrapper = mountSheet()
    await wrapper.find('#import-text').setValue('그냥 안내 문구일 뿐')
    await flushPromises()

    expect(wrapper.find('button.btn').exists()).toBe(false)
    expect(wrapper.text()).toContain(i18n.global.t('import.none'))
    expect(itemsApi.createBudgetItem).not.toHaveBeenCalled()
  })

  it('통장이 하나도 없으면 안내를 띄우고 등록을 막는다', async () => {
    const wrapper = mountSheet([])
    await wrapper.find('#import-text').setValue(TABLE)
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('import.noAccounts'))
    expect((registerButton(wrapper).element as HTMLButtonElement).disabled).toBe(true)
  })

  it('서버 상한 초과(409)면 에러를 표시하고 imported를 emit하지 않는다', async () => {
    vi.mocked(itemsApi.createBudgetItem).mockRejectedValue(
      new ApiError('ITEM_LIMIT_EXCEEDED', {}, 409),
    )
    const wrapper = mountSheet()
    await wrapper.find('#import-text').setValue('정기적금\t300000')
    await wrapper.find('#import-account').setValue('1')
    await flushPromises()

    await registerButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.ITEM_LIMIT_EXCEEDED'))
    expect(wrapper.emitted('imported')).toBeUndefined()
  })
})
