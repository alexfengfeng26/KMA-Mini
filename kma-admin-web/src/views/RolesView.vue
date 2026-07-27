<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { api, asList, errorMessage, unwrap } from '../api/client'
import AppPagination from '../components/AppPagination.vue'
import { useClientPagination } from '../components/listPagination'
import PageState from '../components/PageState.vue'
import { useAuthStore } from '../stores/auth'
import type { components } from '../api/generated/schema'
import type { ElTree } from 'element-plus'
import { useMutationAction } from '../composables/useMutationAction'
import { roleStatusMeta } from '../domain/systemCatalog'

type PermissionNode = components['schemas']['PermissionNode']
interface RoleRow {
  roleId: number
  roleCode: string
  name: string
  description?: string
  status: string
  permissions?: string[]
  builtIn?: boolean
}

const auth = useAuthStore(),
  rows = ref<RoleRow[]>([]),
  tree = ref<PermissionNode[]>([]),
  loading = ref(true),
  error = ref(''),
  dialog = ref(false),
  editing = ref<RoleRow>(),
  permissionTree = ref<InstanceType<typeof ElTree>>()
const mutation = useMutationAction()
const { page, pageSize, total, pagedItems, resetPage } = useClientPagination(rows)
const form = reactive({
  roleCode: '',
  name: '',
  description: '',
  status: 'active',
  permissions: [] as string[],
})
const treeProps = { label: 'name', children: 'children' }
async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = asList<RoleRow>(await unwrap(api.GET('/api/v1/admin/roles')))
    tree.value = auth.hasAnyPermission(['permission:read'])
      ? asList<PermissionNode>(await unwrap(api.GET('/api/v1/admin/permissions/tree')))
      : []
    resetPage()
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取角色权限')
  } finally {
    loading.value = false
  }
}
function openCreate() {
  editing.value = undefined
  Object.assign(form, { roleCode: '', name: '', description: '', status: 'active', permissions: [] })
  dialog.value = true
  nextTick(() => permissionTree.value?.setCheckedKeys([]))
}
function openEdit(value: unknown) {
  const row = value as RoleRow
  editing.value = row
  Object.assign(form, {
    roleCode: row.roleCode,
    name: row.name,
    description: row.description || '',
    status: row.status,
    permissions: [...(row.permissions || [])],
  })
  dialog.value = true
  nextTick(() => permissionTree.value?.setCheckedKeys(form.permissions))
}
function includeParentPermissions(codes: string[]) {
  const selected = new Set(codes)
  function visit(nodes: PermissionNode[]): boolean {
    return nodes.reduce((contains, node) => {
      const childSelected = visit(node.children || [])
      const included = (node.permissionCode ? selected.has(node.permissionCode) : false) || childSelected
      if (included && node.permissionCode) selected.add(node.permissionCode)
      return contains || included
    }, false)
  }
  visit(tree.value)
  return [...selected]
}
async function save() {
  form.permissions = includeParentPermissions((permissionTree.value?.getCheckedKeys(false) || []).map(String))
  const result = await mutation.run(
    () =>
      editing.value
        ? unwrap(
            api.PUT('/api/v1/admin/roles/{roleId}', {
              params: { path: { roleId: editing.value.roleId } },
              body: form,
            }),
          )
        : unwrap(api.POST('/api/v1/admin/roles', { body: form })),
    '角色权限已保存',
  )
  if (!result.ok) return
  dialog.value = false
  await load()
}
async function remove(value: unknown) {
  const row = value as RoleRow
  const confirmed = await ElMessageBox.confirm(`确认删除角色“${row.name}”？`, '删除角色', {
    type: 'warning',
  }).then(
    () => true,
    () => false,
  )
  if (!confirmed) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.DELETE('/api/v1/admin/roles/{roleId}', {
          params: { path: { roleId: row.roleId } },
        }),
      ),
    '角色已删除',
  )
  if (result.ok) await load()
}
onMounted(load)
</script>

<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">RBAC</span>
        <h2>角色与权限</h2>
      </div>
      <el-button
        v-if="auth.hasAnyPermission(['permission:read'])"
        v-permission="'role:create'"
        type="primary"
        @click="openCreate"
        >创建角色</el-button
      >
    </div>
    <PageState :loading="loading" :error="error" :empty="!rows.length"
      ><el-table :data="pagedItems"
        ><el-table-column prop="roleCode" label="角色编码" /><el-table-column
          prop="name"
          label="名称"
        /><el-table-column prop="description" label="说明" /><el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag :type="roleStatusMeta(row.status).type">{{
              roleStatusMeta(row.status).label
            }}</el-tag></template
          ></el-table-column
        ><el-table-column prop="userCount" label="用户数" width="80" /><el-table-column
          label="类型"
          width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.builtIn ? 'warning' : 'info'">{{
              row.builtIn ? '内置' : '自定义'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="160"
          ><template #default="{ row }"
            ><el-button
              v-if="row.roleCode !== 'kma-admin' && auth.hasAnyPermission(['permission:read'])"
              v-permission="'role:update'"
              link
              @click="openEdit(row)"
              >编辑</el-button
            ><el-button
              v-if="!row.builtIn"
              v-permission="'role:delete'"
              link
              type="danger"
              @click="remove(row)"
              >删除</el-button
            ></template
          ></el-table-column
        ></el-table
      ><AppPagination v-model:page="page" v-model:page-size="pageSize" :total="total"
    /></PageState>
  </section>
  <el-dialog v-model="dialog" :title="editing ? '编辑角色' : '创建角色'" width="720"
    ><el-form label-position="top"
      ><el-form-item label="角色编码"><el-input v-model="form.roleCode" :disabled="!!editing" /></el-form-item
      ><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item
      ><el-form-item label="说明"><el-input v-model="form.description" type="textarea" /></el-form-item
      ><el-form-item label="状态"
        ><el-select v-model="form.status"
          ><el-option label="启用" value="active" /><el-option
            label="停用"
            value="disabled" /></el-select></el-form-item
      ><el-form-item label="权限"
        ><el-tree
          ref="permissionTree"
          :data="tree"
          :props="treeProps"
          node-key="permissionCode"
          show-checkbox
          check-strictly
          default-expand-all
          ><template #default="{ data }"
            ><span
              >{{ data.name }} <small class="muted">{{ data.permissionCode }}</small></span
            ></template
          ></el-tree
        ></el-form-item
      ></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button
        v-permission="editing ? 'role:update' : 'role:create'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="mutation.pending.value"
        @click="save"
        >保存</el-button
      ></template
    ></el-dialog
  >
</template>
