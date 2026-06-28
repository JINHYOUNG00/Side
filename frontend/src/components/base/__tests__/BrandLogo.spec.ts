import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import i18n from '@/i18n'
import BrandLogo from '../BrandLogo.vue'

describe('BrandLogo', () => {
  it('워드마크를 i18n app.name으로 렌더한다(문구 하드코딩 금지)', () => {
    const w = mount(BrandLogo, { global: { plugins: [i18n] } })
    expect(w.find('.brand-word').text()).toBe(i18n.global.t('app.name'))
  })

  it("'흐르는 물방울' 마크 SVG를 함께 렌더한다", () => {
    const w = mount(BrandLogo, { global: { plugins: [i18n] } })
    expect(w.find('svg.brand-mark').exists()).toBe(true)
    // 물줄기 2갈래 + 물방울
    expect(w.findAll('.flow').length).toBe(2)
    expect(w.find('.drop').exists()).toBe(true)
  })
})
