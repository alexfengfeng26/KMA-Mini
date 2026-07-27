import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppPagination from './AppPagination.vue'

const PaginationStub = defineComponent({
  emits: ['update:current-page', 'update:page-size'],
  template: `<div><button class="page" @click="$emit('update:current-page', 2)">page</button><button class="size" @click="$emit('update:page-size', 20)">size</button></div>`,
})

describe('AppPagination', () => {
  it('emits a consistent page change payload', async () => {
    const wrapper = mount(AppPagination, {
      props: { page: 1, pageSize: 10, total: 25 },
      global: { stubs: { ElPagination: PaginationStub } },
    })
    await wrapper.get('.page').trigger('click')
    expect(wrapper.emitted('update:page')).toEqual([[2]])
    expect(wrapper.emitted('change')).toEqual([[{ page: 2, pageSize: 10 }]])
  })

  it('returns to page one when page size changes', async () => {
    const wrapper = mount(AppPagination, {
      props: { page: 3, pageSize: 10, total: 45 },
      global: { stubs: { ElPagination: PaginationStub } },
    })
    await wrapper.get('.size').trigger('click')
    expect(wrapper.emitted('update:pageSize')).toEqual([[20]])
    expect(wrapper.emitted('update:page')).toEqual([[1]])
    expect(wrapper.emitted('change')).toEqual([[{ page: 1, pageSize: 20 }]])
  })

  it('does not render for an empty list', () => {
    const wrapper = mount(AppPagination, {
      props: { page: 1, pageSize: 10, total: 0 },
      global: { stubs: { ElPagination: PaginationStub } },
    })
    expect(wrapper.find('[aria-label="列表分页"]').exists()).toBe(false)
  })
})
