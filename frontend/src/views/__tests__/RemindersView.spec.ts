import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import RemindersView from '../RemindersView.vue'
import { ApiError } from '@/api/client'
import * as remindersApi from '@/api/reminders'
import type { Reminder } from '@/api/reminders'

vi.mock('@/api/reminders')

const FX: Reminder = { id: 1, label: '외화 예수금 점검', intervalMonths: 3, nextRemindDate: '2026-07-01' }
const LEASE: Reminder = { id: 2, label: '전세 만기 점검', intervalMonths: 12, nextRemindDate: '2026-09-01' }

// Teleport를 인라인 렌더해 시트 내부 요소를 wrapper.find로 찾을 수 있게 한다.
function mountView() {
  return mount(RemindersView, {
    global: { plugins: [router, i18n], stubs: { teleport: true } },
  })
}

function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((b) => b.text() === text)
}

describe('RemindersView (NOTI-06 점검 리마인더 설정)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(remindersApi.listReminders).mockReset()
    vi.mocked(remindersApi.createReminder).mockReset()
    vi.mocked(remindersApi.updateReminder).mockReset()
    vi.mocked(remindersApi.deleteReminder).mockReset()
  })

  it('마운트 시 목록을 불러와 리마인더를 표시한다', async () => {
    vi.mocked(remindersApi.listReminders).mockResolvedValue([FX, LEASE])
    const wrapper = mountView()
    await flushPromises()

    expect(remindersApi.listReminders).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('외화 예수금 점검')
    expect(wrapper.text()).toContain('전세 만기 점검')
  })

  it('분기 외화 점검 자동 안내(fxNote)를 늘 보여준다', async () => {
    vi.mocked(remindersApi.listReminders).mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('reminders.fxNote'))
  })

  it('리마인더가 없으면 빈 상태를 보여준다', async () => {
    vi.mocked(remindersApi.listReminders).mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('reminders.emptyTitle'))
  })

  it('추가 → 폼 작성 → 저장하면 createReminder 호출 후 목록을 다시 읽어 반영한다', async () => {
    vi.mocked(remindersApi.listReminders).mockResolvedValueOnce([]).mockResolvedValueOnce([FX])
    vi.mocked(remindersApi.createReminder).mockResolvedValue(FX)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('reminders.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#reminder-label').setValue('외화 예수금 점검')
    await wrapper.find('#reminder-date').setValue('2026-07-01')
    await buttonByText(wrapper, i18n.global.t('reminders.form.save'))!.trigger('click')
    await flushPromises()

    expect(remindersApi.createReminder).toHaveBeenCalledWith({
      label: '외화 예수금 점검',
      intervalMonths: 3,
      nextRemindDate: '2026-07-01',
    })
    expect(remindersApi.listReminders).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('외화 예수금 점검')
  })

  it('메모가 비면 저장 시 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(remindersApi.listReminders).mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('reminders.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#reminder-date').setValue('2026-07-01')
    await buttonByText(wrapper, i18n.global.t('reminders.form.save'))!.trigger('click')
    await flushPromises()

    expect(remindersApi.createReminder).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('행을 누르면 수정 모드로 열려 기존 값을 채우고 updateReminder를 호출한다', async () => {
    vi.mocked(remindersApi.listReminders).mockResolvedValue([FX])
    vi.mocked(remindersApi.updateReminder).mockResolvedValue({ ...FX, label: '외화 점검(수정)' })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.row').trigger('click')
    await wrapper.vm.$nextTick()
    expect((wrapper.find('#reminder-label').element as HTMLInputElement).value).toBe('외화 예수금 점검')

    await wrapper.find('#reminder-label').setValue('외화 점검(수정)')
    await buttonByText(wrapper, i18n.global.t('reminders.form.save'))!.trigger('click')
    await flushPromises()

    expect(remindersApi.updateReminder).toHaveBeenCalledWith(1, {
      label: '외화 점검(수정)',
      intervalMonths: 3,
      nextRemindDate: '2026-07-01',
    })
  })

  it('수정 모드에서 삭제 확인 후 deleteReminder를 호출한다', async () => {
    vi.mocked(remindersApi.listReminders).mockResolvedValue([FX])
    vi.mocked(remindersApi.deleteReminder).mockResolvedValue()
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.row').trigger('click')
    await wrapper.vm.$nextTick()
    // 첫 삭제 클릭은 확인 단계로 전환만 한다(즉시 삭제 안 함).
    await buttonByText(wrapper, i18n.global.t('reminders.form.delete'))!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(remindersApi.deleteReminder).not.toHaveBeenCalled()

    await buttonByText(wrapper, i18n.global.t('reminders.form.delete'))!.trigger('click')
    await flushPromises()

    expect(remindersApi.deleteReminder).toHaveBeenCalledWith(1)
  })

  it('50개 상한 초과 시 서버의 REMINDER_LIMIT_EXCEEDED를 표시한다', async () => {
    vi.mocked(remindersApi.listReminders).mockResolvedValue([])
    vi.mocked(remindersApi.createReminder).mockRejectedValue(new ApiError('REMINDER_LIMIT_EXCEEDED', {}, 409))
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('reminders.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#reminder-label').setValue('쉰한번째')
    await wrapper.find('#reminder-date').setValue('2026-07-01')
    await buttonByText(wrapper, i18n.global.t('reminders.form.save'))!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.REMINDER_LIMIT_EXCEEDED'))
  })
})
