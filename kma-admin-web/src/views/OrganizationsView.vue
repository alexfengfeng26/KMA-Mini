<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { api, asList, errorMessage, unwrap } from '../api/client'
import AppPagination from '../components/AppPagination.vue'
import { useClientPagination } from '../components/listPagination'
import PageState from '../components/PageState.vue'
import type { components } from '../api/generated/schema'
import { getAuthorizedPage } from '../api/page'
import { useMutationAction } from '../composables/useMutationAction'

type OrganizationNode = components['schemas']['OrganizationNode']
type FlatOrganizationNode = OrganizationNode & { depth: number }
type OrganizationMember = Record<string, unknown>

const tree = ref<OrganizationNode[]>([]),
  loading = ref(true),
  error = ref(''),
  dialog = ref(false),
  moveDialog = ref(false),
  membersDialog = ref(false),
  editing = ref<OrganizationNode>(),
  selected = ref<OrganizationNode>(),
  members = ref<OrganizationMember[]>([])
const form = reactive({
    orgCode: '',
    name: '',
    parentId: undefined as number | undefined,
    status: 'active',
    sortOrder: 0,
  }),
  moveForm = reactive({ parentId: undefined as number | undefined })
function flatten(nodes: OrganizationNode[], depth = 0): FlatOrganizationNode[] {
  return nodes.flatMap((node) => [{ ...node, depth }, ...flatten(node.children || [], depth + 1)])
}
const options = computed(() => flatten(tree.value))
const useVirtualTree = computed(() => options.value.length > 500)
const virtualTreeProps = { value: 'orgId', label: 'name', children: 'children' }
const validOptions = computed(() =>
  options.value.filter(
    (organization): organization is FlatOrganizationNode & { orgId: number } => !!organization.orgId,
  ),
)
const { page, pageSize, total, pagedItems, resetPage } = useClientPagination(options)
const { page: memberPage, pageSize: memberPageSize } = useClientPagination(members)
const memberTotal = ref(0)
const mutation = useMutationAction()
async function load() {
  loading.value = true
  error.value = ''
  try {
    tree.value = asList<OrganizationNode>(await unwrap(api.GET('/api/v1/admin/organizations/tree')))
    resetPage()
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取组织树')
  } finally {
    loading.value = false
  }
}
function openCreate(parent?: OrganizationNode) {
  editing.value = undefined
  Object.assign(form, {
    orgCode: '',
    name: '',
    parentId: parent?.orgId || tree.value[0]?.orgId,
    status: 'active',
    sortOrder: 0,
  })
  dialog.value = true
}
function openEdit(row: OrganizationNode) {
  editing.value = row
  Object.assign(form, {
    orgCode: row.orgCode,
    name: row.name,
    parentId: row.parentId,
    status: row.status,
    sortOrder: row.sortOrder,
  })
  dialog.value = true
}
async function save() {
  const result = await mutation.run(async () => {
    if (editing.value) {
      if (!editing.value.orgId) throw new Error('组织编号缺失')
      await unwrap(
        api.PUT('/api/v1/admin/organizations/{orgId}', {
          params: { path: { orgId: editing.value.orgId } },
          body: { name: form.name, status: form.status, sortOrder: form.sortOrder },
        }),
      )
    } else {
      if (!form.parentId) throw new Error('请选择上级组织')
      await unwrap(
        api.POST('/api/v1/admin/organizations', {
          body: {
            orgCode: form.orgCode,
            name: form.name,
            parentId: form.parentId,
            sortOrder: form.sortOrder,
          },
        }),
      )
    }
  }, '组织已保存')
  if (!result.ok) return
  dialog.value = false
  await load()
}
function openMove(row: OrganizationNode) {
  selected.value = row
  moveForm.parentId = row.parentId
  moveDialog.value = true
}
async function move() {
  const orgId = selected.value?.orgId
  const parentId = moveForm.parentId
  if (!orgId || !parentId) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.PUT('/api/v1/admin/organizations/{orgId}/move', {
          params: { path: { orgId } },
          body: { parentId },
        }),
      ),
    '组织已移动',
  )
  if (!result.ok) return
  moveDialog.value = false
  await load()
}
async function remove(row: OrganizationNode) {
  const confirmed = await ElMessageBox.confirm(`确认删除组织“${row.name}”？`, '删除组织', {
    type: 'warning',
  }).then(
    () => true,
    () => false,
  )
  if (!confirmed) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.DELETE('/api/v1/admin/organizations/{orgId}', {
          params: { path: { orgId: row.orgId! } },
        }),
      ),
    '组织已删除',
  )
  if (result.ok) await load()
}
async function showMembers(row: OrganizationNode) {
  if (!row.orgId) return
  selected.value = row
  memberPage.value = 1
  await loadMembers()
  membersDialog.value = true
}
async function loadMembers() {
  if (!selected.value?.orgId) return
  const result = await getAuthorizedPage<OrganizationMember>(
    `/api/v1/admin/organizations/${selected.value.orgId}/members/page`,
    {
      pageNum: memberPage.value,
      pageSize: memberPageSize.value,
      sortBy: 'username',
      sortOrder: 'asc',
    },
  )
  members.value = result.list
  memberTotal.value = result.total
}
onMounted(load)
</script>
<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">ORGANIZATION TREE</span>
        <h2>组织管理</h2>
      </div>
      <el-button v-permission="'org:create'" type="primary" @click="openCreate()">创建组织</el-button>
    </div>
    <PageState :loading="loading" :error="error" :empty="!tree.length"
      ><el-tree-v2
        v-if="useVirtualTree"
        :data="tree"
        :props="virtualTreeProps"
        :height="600"
        :item-size="48"
        default-expand-all
      >
        <template #default="{ data }">
          <div class="virtual-org-row">
            <strong>{{ data.name }}</strong>
            <span>{{ data.orgCode }}</span>
            <span>{{ data.memberCount || 0 }} 名成员</span>
            <div class="virtual-org-actions">
              <el-button link @click="showMembers(data)">成员</el-button>
              <el-button v-permission="'org:create'" link @click="openCreate(data)">新增下级</el-button>
              <el-button v-permission="'org:update'" link @click="openEdit(data)">编辑</el-button>
              <el-button v-if="!data.builtIn" v-permission="'org:move'" link @click="openMove(data)"
                >移动</el-button
              >
              <el-button
                v-if="!data.builtIn"
                v-permission="'org:delete'"
                link
                type="danger"
                @click="remove(data)"
                >删除</el-button
              >
            </div>
          </div>
        </template>
      </el-tree-v2>
      <el-table v-else :data="pagedItems" row-key="orgId"
        ><el-table-column label="组织"
          ><template #default="{ row }"
            ><span :class="`organization-depth-${Math.min(row.depth, 12)}`"
              >{{ row.depth ? '└ ' : '' }}{{ row.name }}</span
            ></template
          ></el-table-column
        ><el-table-column prop="orgCode" label="编码" /><el-table-column
          prop="status"
          label="状态"
          width="90"
        /><el-table-column prop="memberCount" label="直接成员" width="90" /><el-table-column
          label="操作"
          min-width="300"
          ><template #default="{ row }"
            ><el-button link @click="showMembers(row)">成员</el-button
            ><el-button v-permission="'org:create'" link @click="openCreate(row)">新增下级</el-button
            ><el-button v-permission="'org:update'" link @click="openEdit(row)">编辑</el-button
            ><el-button v-permission="'org:move'" v-if="!row.builtIn" link @click="openMove(row)"
              >移动</el-button
            ><el-button
              v-permission="'org:delete'"
              v-if="!row.builtIn"
              link
              type="danger"
              @click="remove(row)"
              >删除</el-button
            ></template
          ></el-table-column
        ></el-table
      ><AppPagination
        v-if="!useVirtualTree"
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total" /></PageState
    ><el-alert type="info" :closable="false" title="空间授权给上级组织后，其全部下级组织成员自动继承。" />
  </section>
  <el-dialog v-model="dialog" :title="editing ? '编辑组织' : '创建组织'" width="540"
    ><el-form label-position="top"
      ><el-form-item label="组织编码"><el-input v-model="form.orgCode" :disabled="!!editing" /></el-form-item
      ><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item
      ><el-form-item v-if="!editing" label="上级组织"
        ><el-select v-model="form.parentId" class="full-width"
          ><el-option
            v-for="o in validOptions"
            :key="o.orgId"
            :label="`${o.name} (${o.orgCode})`"
            :value="o.orgId" /></el-select></el-form-item
      ><el-form-item v-if="editing" label="状态"
        ><el-select v-model="form.status"
          ><el-option label="启用" value="active" /><el-option
            label="停用"
            value="disabled" /></el-select></el-form-item
      ><el-form-item label="排序"><el-input-number v-model="form.sortOrder" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button
        v-permission="editing ? 'org:update' : 'org:create'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="mutation.pending.value"
        @click="save"
        >保存</el-button
      ></template
    ></el-dialog
  >
  <el-dialog v-model="moveDialog" title="移动组织" width="500"
    ><el-select v-model="moveForm.parentId" class="full-width"
      ><el-option
        v-for="o in validOptions.filter((item) => item.orgId !== selected?.orgId)"
        :key="o.orgId"
        :label="o.name"
        :value="o.orgId" /></el-select
    ><template #footer
      ><el-button @click="moveDialog = false">取消</el-button
      ><el-button
        v-permission="'org:move'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="mutation.pending.value"
        @click="move"
        >移动</el-button
      ></template
    ></el-dialog
  >
  <el-dialog v-model="membersDialog" :title="`${selected?.name || ''} · 直接成员`" width="620"
    ><el-table :data="members"
      ><el-table-column prop="username" label="用户名" /><el-table-column
        prop="display_name"
        label="显示名称" /><el-table-column prop="identity_provider" label="来源" /><el-table-column
        prop="primary_org"
        label="主组织" /></el-table
    ><AppPagination
      v-model:page="memberPage"
      v-model:page-size="memberPageSize"
      :total="memberTotal"
      @change="loadMembers"
  /></el-dialog>
</template>
