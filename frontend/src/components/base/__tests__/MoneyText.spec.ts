import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MoneyText from '../MoneyText.vue'

describe('MoneyText', () => {
  it('천 단위 구분 기호로 포맷한다', () => {
    const w = mount(MoneyText, { props: { amount: 2500000 } })
    expect(w.text()).toContain('2,500,000')
  })

  it('음수는 − (U+2212) 기호를 붙인다', () => {
    const w = mount(MoneyText, { props: { amount: -47000 } })
    expect(w.text()).toContain('−47,000')
  })

  it('signed면 양수에 +를 붙이고 unit을 함께 표시한다', () => {
    const w = mount(MoneyText, { props: { amount: 18600, signed: true, unit: '원' } })
    expect(w.text()).toContain('+18,600')
    expect(w.text()).toContain('원')
  })
})
