import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import EnvelopesView from '../EnvelopesView.vue'
import { ApiError } from '@/api/client'
import * as envelopesApi from '@/api/envelopes'
import * as accountsApi from '@/api/accounts'
import type { Envelope } from '@/api/envelopes'
import type { Account } from '@/api/accounts'

// 함수만 모킹하고 SHORTFALL_SOURCES 상수·타입은 실제 값을 유지(EnvelopeSpendSheet가 칩 렌더에 사용).
vi.mock('@/api/envelopes', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/envelopes')>()
  return {
    ...actual,
    listEnvelopes: vi.fn<typeof actual.listEnvelopes>(),
    createEnvelope: vi.fn<typeof actual.createEnvelope>(),
    updateEnvelope: vi.fn<typeof actual.updateEnvelope>(),
    deleteEnvelope: vi.fn<typeof actual.deleteEnvelope>(),
    spendEnvelope: vi.fn<typeof actual.spendEnvelope>(),
  }
})
vi.mock('@/api/accounts')

const KAKAO: Account = { id: 1, name: '카카오페이', purpose: '생활비', bankDeepLink: null, sortOrder: 0 }

// 반복형 봉투(자동차세, 매년) — 적립 90,000 / 목표 220,000, 진행률 40%, 이번 달 18,600.
const CARTAX: Envelope = {
  id: 10,
  accountId: 1,
  name: '자동차세',
  targetAmount: 220000,
  savedAmount: 90000,
  nextDueDate: '2027-01-10',
  cycleMonths: 12,
  memo: null,
  status: 'ACTIVE',
  progressPercent: 40,
  dDay: 199,
  monthlyAmount: 18600,
}

// 일회성 봉투 — 목표=적립=100,000(딱 맞음), 지출 처리 시 종료(CLOSED) 대상.
const ONETIME: Envelope = {
  id: 20,
  accountId: 1,
  name: '결혼 선물',
  targetAmount: 100000,
  savedAmount: 100000,
  nextDueDate: '2026-09-01',
  cycleMonths: null,
  memo: null,
  status: 'ACTIVE',
  progressPercent: 100,
  dDay: 78,
  monthlyAmount: 0,
}

// 검증이 'nextDueDate >= KST today()'를 실제로 통과/거부하도록 상대 날짜를 쓴다(하드코딩 미래일=시한폭탄 회피).
function kstDate(offsetDays: number): string {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(d)
}
const FUTURE = kstDate(30)
const PAST = kstDate(-1)
// 갱신 안내는 ko-KR 로케일 표기(컴포넌트 renewedDate와 동일 포맷).
const RENEWED = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'long', timeZone: 'Asia/Seoul' }).format(
  new Date('2028-01-10T00:00:00+09:00'),
)

// Teleport를 인라인 렌더해 시트 내부 요소를 wrapper.find로 찾을 수 있게 한다(MOD-03 동일 패턴).
function mountView() {
  return mount(EnvelopesView, {
    global: { plugins: [router, i18n], stubs: { teleport: true } },
  })
}

// ko 로케일 버튼 텍스트로 버튼을 집는다(목록·폼·시트에 .btn이 여럿이라 텍스트로 식별).
function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((b) => b.text() === text)
}

describe('EnvelopesView (ENV-06-front 봉투 화면)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(envelopesApi.listEnvelopes).mockReset()
    vi.mocked(envelopesApi.createEnvelope).mockReset()
    vi.mocked(envelopesApi.updateEnvelope).mockReset()
    vi.mocked(envelopesApi.deleteEnvelope).mockReset()
    vi.mocked(envelopesApi.spendEnvelope).mockReset()
    vi.mocked(accountsApi.listAccounts).mockReset()
  })

  it('마운트 시 봉투와 통장을 불러와 진행률·이번 달 적립액을 표시한다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([CARTAX])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    expect(envelopesApi.listEnvelopes).toHaveBeenCalledOnce()
    expect(accountsApi.listAccounts).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('자동차세')
    expect(wrapper.text()).toContain('40%')
    // 이번 달 적립액(18,600)이 카드와 합계 헤더에 표시된다.
    expect(wrapper.text()).toContain('18,600')
    expect(wrapper.text()).toContain(i18n.global.t('envelopes.monthlyTotal'))
  })

  it('봉투가 없으면 빈 상태를 보여준다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain(i18n.global.t('envelopes.emptyTitle'))
  })

  it('추가 → 폼 작성 → 저장하면 createEnvelope를 반복 주기와 함께 호출하고 목록을 다시 읽는다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValueOnce([]).mockResolvedValueOnce([CARTAX])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(envelopesApi.createEnvelope).mockResolvedValue(CARTAX)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#envelope-name').setValue('자동차세')
    await wrapper.find('#envelope-target').setValue('220000')
    await wrapper.find('#envelope-due').setValue(FUTURE)
    await wrapper.find('#envelope-cycle').setValue('12')
    await wrapper.find('#envelope-account').setValue('1')
    await buttonByText(wrapper, i18n.global.t('envelopes.form.save'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.createEnvelope).toHaveBeenCalledWith({
      accountId: 1,
      name: '자동차세',
      targetAmount: 220000,
      nextDueDate: FUTURE,
      cycleMonths: 12,
      memo: null,
    })
    expect(envelopesApi.listEnvelopes).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('자동차세')
  })

  it('일회성을 선택하면 cycleMonths=null로 생성한다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(envelopesApi.createEnvelope).mockResolvedValue(ONETIME)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#envelope-name').setValue('결혼 선물')
    await wrapper.find('#envelope-target').setValue('100000')
    await wrapper.find('#envelope-due').setValue(FUTURE)
    await buttonByText(wrapper, i18n.global.t('envelopes.form.oneTime'))!.trigger('click')
    await wrapper.find('#envelope-account').setValue('1')
    await buttonByText(wrapper, i18n.global.t('envelopes.form.save'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.createEnvelope).toHaveBeenCalledWith(
      expect.objectContaining({ name: '결혼 선물', cycleMonths: null }),
    )
  })

  it('이름이 비면 저장 시 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#envelope-target').setValue('220000')
    await wrapper.find('#envelope-due').setValue(FUTURE)
    await wrapper.find('#envelope-account').setValue('1')
    await buttonByText(wrapper, i18n.global.t('envelopes.form.save'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.createEnvelope).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('적립 통장 미선택이면 저장 시 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#envelope-name').setValue('자동차세')
    await wrapper.find('#envelope-target').setValue('220000')
    await wrapper.find('#envelope-due').setValue(FUTURE)
    // 통장 선택 안 함
    await buttonByText(wrapper, i18n.global.t('envelopes.form.save'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.createEnvelope).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('통장이 하나도 없으면 안내를 띄우고 저장 버튼을 막는다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain(i18n.global.t('envelopes.form.noAccounts'))
    const save = buttonByText(wrapper, i18n.global.t('envelopes.form.save'))!
    expect((save.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('수정 버튼을 누르면 기존 값을 프리필하고, 바꿔 저장하면 updateEnvelope를 호출한다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([CARTAX])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(envelopesApi.updateEnvelope).mockResolvedValue(CARTAX)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.edit'))!.trigger('click')
    await wrapper.vm.$nextTick()
    expect((wrapper.find('#envelope-name').element as HTMLInputElement).value).toBe('자동차세')
    expect((wrapper.find('#envelope-target').element as HTMLInputElement).value).toBe('220,000')
    expect((wrapper.find('#envelope-due').element as HTMLInputElement).value).toBe('2027-01-10')
    expect((wrapper.find('#envelope-cycle').element as HTMLInputElement).value).toBe('12')

    await wrapper.find('#envelope-target').setValue('250000')
    await buttonByText(wrapper, i18n.global.t('envelopes.form.save'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.updateEnvelope).toHaveBeenCalledWith(10, {
      accountId: 1,
      name: '자동차세',
      targetAmount: 250000,
      nextDueDate: '2027-01-10',
      cycleMonths: 12,
      memo: null,
    })
    expect(envelopesApi.listEnvelopes).toHaveBeenCalledTimes(2)
  })

  it('수정 모드에서 삭제 2단 확인 후 deleteEnvelope를 호출한다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([CARTAX])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(envelopesApi.deleteEnvelope).mockResolvedValue()
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.edit'))!.trigger('click')
    await wrapper.vm.$nextTick()

    // 첫 삭제 클릭은 확인 단계로 전환만 한다(즉시 삭제 안 함).
    await buttonByText(wrapper, i18n.global.t('envelopes.form.delete'))!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(envelopesApi.deleteEnvelope).not.toHaveBeenCalled()

    await buttonByText(wrapper, i18n.global.t('envelopes.form.delete'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.deleteEnvelope).toHaveBeenCalledWith(10)
  })

  it('50개 상한 초과 시 서버의 ENVELOPE_LIMIT_EXCEEDED를 표시한다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(envelopesApi.createEnvelope).mockRejectedValue(
      new ApiError('ENVELOPE_LIMIT_EXCEEDED', { limit: 50 }, 409),
    )
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#envelope-name').setValue('자동차세')
    await wrapper.find('#envelope-target').setValue('220000')
    await wrapper.find('#envelope-due').setValue(FUTURE)
    await wrapper.find('#envelope-account').setValue('1')
    await buttonByText(wrapper, i18n.global.t('envelopes.form.save'))!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(
      i18n.global.t('errors.ENVELOPE_LIMIT_EXCEEDED'),
    )
  })

  it('지출 처리: 부족하면 충당 출처를 골라 spendEnvelope 호출, 반복형은 갱신 안내 후 목록을 다시 읽는다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([CARTAX])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(envelopesApi.spendEnvelope).mockResolvedValue({ ...CARTAX, nextDueDate: '2028-01-10' })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.spend'))!.trigger('click')
    await wrapper.vm.$nextTick()
    // 적립 90,000보다 큰 230,000 입력 → 부족분 충당 출처 노출
    await wrapper.find('#envelope-actual').setValue('230000')
    await wrapper.vm.$nextTick()
    await buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.source.LIVING'))!.trigger('click')
    await buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.submit'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.spendEnvelope).toHaveBeenCalledWith(10, {
      actualAmount: 230000,
      shortfallSource: 'LIVING',
      carryOver: null,
    })
    // 반복형 → 갱신 안내(다음 지출일 이동, 로케일 표기)
    expect(wrapper.text()).toContain(i18n.global.t('envelopes.spendSheet.renewed', { date: RENEWED }))

    await buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.confirm'))!.trigger('click')
    await flushPromises()
    expect(envelopesApi.listEnvelopes).toHaveBeenCalledTimes(2)
  })

  it('지출 처리: 부족인데 충당 출처를 안 고르면 VALIDATION_FAILED, 서버 미호출', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([CARTAX])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.spend'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#envelope-actual').setValue('230000')
    await wrapper.vm.$nextTick()
    await buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.submit'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.spendEnvelope).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('지출 처리: 잉여면 이월을 골라 carryOver=true로 spendEnvelope 호출한다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([CARTAX])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(envelopesApi.spendEnvelope).mockResolvedValue(CARTAX)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.spend'))!.trigger('click')
    await wrapper.vm.$nextTick()
    // 적립 90,000보다 적은 50,000 입력 → 잉여 이월/회수 노출
    await wrapper.find('#envelope-actual').setValue('50000')
    await wrapper.vm.$nextTick()
    await buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.carryOver'))!.trigger('click')
    await buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.submit'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.spendEnvelope).toHaveBeenCalledWith(10, {
      actualAmount: 50000,
      shortfallSource: null,
      carryOver: true,
    })
  })

  it('지출 처리: 일회성은 처리 후 종료(CLOSED) 안내를 보여준다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([ONETIME])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(envelopesApi.spendEnvelope).mockResolvedValue({ ...ONETIME, status: 'CLOSED' })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.spend'))!.trigger('click')
    await wrapper.vm.$nextTick()
    // 적립=목표=100,000과 같은 금액 → 정확 일치(부가 선택 없음)
    await wrapper.find('#envelope-actual').setValue('100000')
    await wrapper.vm.$nextTick()
    await buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.submit'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.spendEnvelope).toHaveBeenCalledWith(20, {
      actualAmount: 100000,
      shortfallSource: null,
      carryOver: null,
    })
    expect(wrapper.text()).toContain(
      i18n.global.t('envelopes.spendSheet.closed', { name: '결혼 선물' }),
    )
  })

  it('다음 지출일이 과거면 저장 시 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#envelope-name').setValue('자동차세')
    await wrapper.find('#envelope-target').setValue('220000')
    await wrapper.find('#envelope-due').setValue(PAST)
    await wrapper.find('#envelope-account').setValue('1')
    await buttonByText(wrapper, i18n.global.t('envelopes.form.save'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.createEnvelope).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('목표 금액이 10억을 넘으면 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#envelope-name').setValue('자동차세')
    await wrapper.find('#envelope-target').setValue('1000000001') // 10억 + 1
    await wrapper.find('#envelope-due').setValue(FUTURE)
    await wrapper.find('#envelope-account').setValue('1')
    await buttonByText(wrapper, i18n.global.t('envelopes.form.save'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.createEnvelope).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('반복 주기가 1200개월을 넘으면 VALIDATION_FAILED를 보이고 서버를 호출하지 않는다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.add'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#envelope-name').setValue('자동차세')
    await wrapper.find('#envelope-target').setValue('220000')
    await wrapper.find('#envelope-due').setValue(FUTURE)
    await wrapper.find('#envelope-cycle').setValue('2000') // CYCLE_MAX(1200) 초과
    await wrapper.find('#envelope-account').setValue('1')
    await buttonByText(wrapper, i18n.global.t('envelopes.form.save'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.createEnvelope).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toBe(i18n.global.t('errors.VALIDATION_FAILED'))
  })

  it('지출 처리: 적립액과 정확히 같으면 부가 선택 칩 없이 둘 다 null로 처리한다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([CARTAX])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(envelopesApi.spendEnvelope).mockResolvedValue({ ...CARTAX, nextDueDate: '2028-01-10' })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.spend'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#envelope-actual').setValue('90000') // 적립액과 동일 → 정확 일치
    await wrapper.vm.$nextTick()

    // 정확 일치이면 충당 출처·이월/회수 칩을 띄우지 않는다.
    expect(buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.source.LIVING'))).toBeUndefined()
    expect(buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.carryOver'))).toBeUndefined()

    await buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.submit'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.spendEnvelope).toHaveBeenCalledWith(10, {
      actualAmount: 90000,
      shortfallSource: null,
      carryOver: null,
    })
  })

  it('지출 처리: 잉여에서 회수를 고르면 carryOver=false로 호출한다', async () => {
    vi.mocked(envelopesApi.listEnvelopes).mockResolvedValue([CARTAX])
    vi.mocked(accountsApi.listAccounts).mockResolvedValue([KAKAO])
    vi.mocked(envelopesApi.spendEnvelope).mockResolvedValue(CARTAX)
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, i18n.global.t('envelopes.spend'))!.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('#envelope-actual').setValue('50000') // 적립 90,000보다 적음 → 잉여
    await wrapper.vm.$nextTick()
    await buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.recover'))!.trigger('click')
    await buttonByText(wrapper, i18n.global.t('envelopes.spendSheet.submit'))!.trigger('click')
    await flushPromises()

    expect(envelopesApi.spendEnvelope).toHaveBeenCalledWith(10, {
      actualAmount: 50000,
      shortfallSource: null,
      carryOver: false,
    })
  })
})
