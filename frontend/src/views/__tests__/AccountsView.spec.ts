import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import AccountsView from '../AccountsView.vue'
import { ApiError } from '@/api/client'
import * as accountsApi from '@/api/accounts'
import type { Account } from '@/api/accounts'

vi.mock('@/api/accounts')

const KAKAO: Account = { id: 1, name: '카카오페이', purpose: '생활비', bankDeepLink: null, sortOrder: 0 }
const TOSS: Account = { id: 2, name: '토스', purpose: null, bankDeepLink: null, sortOrder: 1 }

// Teleport를 인라인 렌더해 시트 내부 요소를 wrapper.find로 찾을 수 있게 한다.
function mountView() {
  return mount(AccountsView, {
    global: { plugins: [router, i18n], stubs: { teleport: true } },
  })
}

// ko 로케일 버튼 텍스트로 시트 버튼을 집는다(폼·목록에 .btn이 여럿이라 텍스트로 식별).
function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((b) => b.text() === text)
}

describe('AccountsView (MOD-03 통장 관리)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(accountsApi.listAccounts).mockReset()
    vi.mocked(accountsApi.createAccount).mockReset()
    vi.mocked(accountsApi.updateAccount).mockReset()
    vi.mocked(accountsApi.deleteAccount).mockReset()
  })

  it('마운트 시 목록을 불러와 통장을 표시한다', async () => {
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO, TOSS])
    const wrapper = mountView()
    await flushPromises()

    expect(accountsApi.listAccounts).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('카카오페이')
    expect(wrapper.text()).toContain('토스')
  })

  it('통장이 없으면 빈 상태를 보여준다', async () => {
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('accounts.emptyTitle'))
  })

  it('추가 → 폼 작성 → 저장하면 createAccount 호출 후 목록을 다시 읽어 반영한다', async () => {
    vi.mocked(accountsApi.listAccounts).mockResolvedValueOnce([]).mockResolvedValueOnce([KAKAO])
    vi.mocked(accountsApi.createAccount).mockResolvedValue(KAKAO)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('accounts.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#account-name').setValue('카카오페이')
    await wrapper.find('#account-purpose').setValue('생활비')
    await buttonByText(wrapper, i18n.global.t('accounts.form.save'))!.trigger('click')
    await flushPromises()

    expect(accountsApi.createAccount).toHaveBeenCalledWith({
      name: '카카오페이',
      purpose: '생활비',
      bankDeepLink: null,
    })
    expect(accountsApi.listAccounts).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('카카오페이')
  })

  it('이름이 비면 저장 시 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('accounts.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await buttonByText(wrapper, i18n.global.t('accounts.form.save'))!.trigger('click')
    await flushPromises()

    expect(accountsApi.createAccount).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('행을 누르면 수정 모드로 열려 기존 값을 채우고 updateAccount를 호출한다', async () => {
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(accountsApi.updateAccount).mockResolvedValue({ ...KAKAO, name: '카카오뱅크' })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.row').trigger('click')
    await wrapper.vm.$nextTick()
    expect((wrapper.find('#account-name').element as HTMLInputElement).value).toBe('카카오페이')

    await wrapper.find('#account-name').setValue('카카오뱅크')
    await buttonByText(wrapper, i18n.global.t('accounts.form.save'))!.trigger('click')
    await flushPromises()

    expect(accountsApi.updateAccount).toHaveBeenCalledWith(1, {
      name: '카카오뱅크',
      purpose: '생활비',
      bankDeepLink: null,
    })
  })

  it('수정 모드에서 삭제 확인 후 deleteAccount를 호출한다', async () => {
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(accountsApi.deleteAccount).mockResolvedValue()
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.row').trigger('click')
    await wrapper.vm.$nextTick()
    // 첫 삭제 클릭은 확인 단계로 전환만 한다(즉시 삭제 안 함).
    await buttonByText(wrapper, i18n.global.t('accounts.form.delete'))!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(accountsApi.deleteAccount).not.toHaveBeenCalled()

    await buttonByText(wrapper, i18n.global.t('accounts.form.delete'))!.trigger('click')
    await flushPromises()

    expect(accountsApi.deleteAccount).toHaveBeenCalledWith(1)
  })

  it('20개 상한 초과 시 서버의 ACCOUNT_LIMIT_EXCEEDED를 표시한다', async () => {
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([])
    vi.mocked(accountsApi.createAccount).mockRejectedValue(new ApiError('ACCOUNT_LIMIT_EXCEEDED', {}, 409))
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('accounts.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#account-name').setValue('새 통장')
    await buttonByText(wrapper, i18n.global.t('accounts.form.save'))!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.ACCOUNT_LIMIT_EXCEEDED'))
  })
})
