import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import i18n from '@/i18n'
import LoginView from '../LoginView.vue'

async function mountAt(path: string) {
  await router.replace(path)
  await router.isReady()
  return mount(LoginView, { global: { plugins: [createPinia(), router, i18n] } })
}

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('활성 공급자 버튼(카카오·구글·네이버)을 노출한다', async () => {
    const w = await mountAt('/login')
    expect(w.find('.btn.kakao').exists()).toBe(true)
    expect(w.find('.btn.google').exists()).toBe(true)
    expect(w.find('.btn.naver').exists()).toBe(true)
    expect(w.text()).toContain('카카오로 시작하기')
    expect(w.text()).toContain('Google로 시작하기')
    expect(w.text()).toContain('네이버로 시작하기')
  })

  it('?error 쿼리가 있으면 코드에 매핑된 오류 문구를 표시한다', async () => {
    const w = await mountAt('/login?error=OAUTH_EXCHANGE_FAILED')
    const alert = w.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toBe('로그인에 실패했어요. 다시 시도해 주세요')
  })

  it('error 쿼리가 없으면 오류 영역을 숨긴다', async () => {
    const w = await mountAt('/login')
    expect(w.find('[role="alert"]').exists()).toBe(false)
  })
})
