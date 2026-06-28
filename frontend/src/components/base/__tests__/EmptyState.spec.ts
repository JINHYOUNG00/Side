import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from '../EmptyState.vue'

describe('EmptyState', () => {
  it('주입된 title과 body를 렌더한다', () => {
    const w = mount(EmptyState, { props: { title: '아직 항목이 없어요', body: '추가해 보세요' } })
    expect(w.find('.empty-title').text()).toBe('아직 항목이 없어요')
    expect(w.find('.empty-body').text()).toBe('추가해 보세요')
  })

  it('빈 상태를 Card로 감싼다', () => {
    const w = mount(EmptyState, { props: { title: 't', body: 'b' } })
    expect(w.find('.card.empty').exists()).toBe(true)
  })
})
