import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PageState from './PageState.vue'

describe('PageState', () => {
  it('shows actionable error text', () => {
    const wrapper = mount(PageState, { props: { error: '数据库暂时不可用' } })
    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.text()).toContain('数据库暂时不可用')
  })

  it('renders content when data is ready', () => {
    const wrapper = mount(PageState, { slots: { default: '<p>ready</p>' } })
    expect(wrapper.text()).toContain('ready')
  })
})
