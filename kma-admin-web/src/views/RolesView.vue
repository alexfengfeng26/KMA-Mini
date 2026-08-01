<script setup lang="ts">
import { computed, onMounted, reactive, ref, nextTick } from 'vue'
import { ElMessageBox } from 'element-plus'
import AppPagination from '../components/AppPagination.vue'
import PageHeader from '../components/PageHeader.vue'
import PageState from '../components/PageState.vue'
import PermissionTree from '../components/PermissionTree.vue'
import { useAuthStore } from '../stores/auth'
import { useMutationAction } from '../composables/useMutationAction'
import { roleStatusMeta } from '../domain/systemCatalog'
import {
  batchRoleStatus,
  cloneRole,
  createRole,
  deleteRole,
  getPermissionsTree,
  getRoleUsers,
  getRoles,
  updateRole,
  type PermissionNode,
  type RoleRow,
  type RoleUpsertRequest,
  type UserRow,
} from '../api/identity'
import { errorMessage } from '../api/client'
import { useClientPagination } from '../components/listPagination'

const auth = useAuthStore()
const mutation = useMutationAction()

const rows = ref<RoleRow[]>([])
const tree = ref<PermissionNode[]>([])
const loading = ref(true)
const error = ref('')
const keyword = ref('')
const selectedRows = ref<RoleRow[]>([])

const dialog = ref(false)
const editing = ref<RoleRow>()
const permissionTree = ref<InstanceType<typeof PermissionTree>>()
const permissionPreview = ref<string[]>([])

const usersDialog = ref(false)
const roleUsers = ref<UserRow[]>([])
const selectedRole = ref<RoleRow>()

const form = reactive<RoleUpsertRequest>({
  roleCode: '',
  name: '',
  description: '',
  status: 'active',
  permissions: [],
})

const filteredRows = computed(() => {
  if (!keyword.value.trim()) return rows.value
  const q = keyword.value.trim().toLowerCase()
  return rows.value.filter(
    (r) =>
      r.roleCode.toLowerCase().includes(q) ||
      r.name.toLowerCase().includes(q) ||
      (r.description || '').toLowerCase().includes(q),
  )
})

const { page, pageSize, total, pagedItems, resetPage } = useClientPagination(filteredRows)

const selectedPermissionsByModule = computed(() => {
  const map = new Map<string, string[]>()
  permissionPreview.value.forEach((code) => {
    const node = findNode(tree.value, code)
    const module = node?.module || '系统管理'
    if (!map.has(module)) map.set(module, [])
    map.get(module)!.push(node?.name || code)
  })
  return map
})

function findNode(nodes: PermissionNode[], code: string): PermissionNode | undefined {
  for (const node of nodes) {
    if (node.permissionCode === code) return node
    const child = findNode(node.children || [], code)
    if (child) return child
  }
  return undefined
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await getRoles()
    tree.value = auth.hasAnyPermission(['permission:read']) ? await getPermissionsTree() : []
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
  permissionPreview.value = []
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
  permissionPreview.value = [...form.permissions]
  dialog.value = true
  nextTick(() => permissionTree.value?.setCheckedKeys(form.permissions))
}

async function clone(value: unknown) {
  const row = value as RoleRow
  const result = await mutation.run(() => cloneRole(row.roleId), '角色已克隆')
  if (result.ok) await load()
}

async function save() {
  form.permissions = permissionPreview.value
  const result = await mutation.run(async () => {
    if (editing.value) {
      await updateRole(editing.value.roleId, form)
    } else {
      await createRole(form)
    }
  }, '角色权限已保存')
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
  const result = await mutation.run(() => deleteRole(row.roleId), '角色已删除')
  if (result.ok) await load()
}

async function showUsers(value: unknown) {
  const row = value as RoleRow
  selectedRole.value = row
  roleUsers.value = await getRoleUsers(row.roleId)
  usersDialog.value = true
}

function selectionChange(rows: RoleRow[]) {
  selectedRows.value = rows
}

async function batchAction(action: 'active' | 'disabled') {
  const targets = [...selectedRows.value]
  if (!targets.length) return
  const confirmed = await ElMessageBox.confirm(
    `确定对选中的 ${targets.length} 个角色执行${action === 'active' ? '启用' : '停用'}吗？`,
    '批量操作',
    { type: 'warning' },
  ).then(
    () => true,
    () => false,
  )
  if (!confirmed) return
  const result = await mutation.run(
    () =>
      batchRoleStatus(
        targets.map((r) => r.roleId),
        action,
      ),
    '批量操作完成',
  )
  if (result.ok) await load()
}

function onPermissionChange(codes: string[]) {
  permissionPreview.value = codes
}

onMounted(load)
</script>

<template>
  <section class="panel">
    <PageHeader eyebrow="RBAC" title="角色与权限" description="维护角色权限集合、克隆角色、查看角色用户。">
      <template #actions>
        <el-button
          v-if="auth.hasAnyPermission(['permission:read'])"
          v-permission="'role:create'"
          type="primary"
          @click="openCreate"
        >
          创建角色
        </el-button>
      </template>
    </PageHeader>

    <div class="filter-bar">
      <el-input v-model="keyword" clearable placeholder="搜索角色编码 / 名称 / 说明" class="role-search" />
      <div v-if="selectedRows.length" class="batch-actions">
        <span>已选 {{ selectedRows.length }} 项</span>
        <el-button size="small" @click="batchAction('active')">批量启用</el-button>
        <el-button size="small" @click="batchAction('disabled')">批量停用</el-button>
        <el-button size="small" text @click="selectedRows = []">清空</el-button>
      </div>
    </div>

    <PageState :loading="loading" :error="error" :empty="!filteredRows.length">
      <el-table :data="pagedItems" @selection-change="selectionChange">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="roleCode" label="角色编码" min-width="130" />
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column prop="description" label="说明" min-width="180" />
        <el-table-column prop="permissionCount" label="权限数" width="80" />
        <el-table-column prop="userCount" label="用户数" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="roleStatusMeta(row.status).type">{{ roleStatusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.builtIn ? 'warning' : 'info'">{{ row.builtIn ? '内置' : '自定义' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.roleCode !== 'kma-admin' && auth.hasAnyPermission(['permission:read'])"
              v-permission="'role:update'"
              link
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button v-permission="'role:create'" link @click="clone(row)">克隆</el-button>
            <el-button v-permission="'role:read'" link @click="showUsers(row)">用户</el-button>
            <el-button
              v-if="!row.builtIn"
              v-permission="'role:delete'"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </PageState>

    <el-dialog v-model="dialog" :title="editing ? '编辑角色' : '创建角色'" width="860">
      <div class="role-edit-layout">
        <el-form label-position="top" class="role-form">
          <el-form-item label="角色编码"
            ><el-input v-model="form.roleCode" :disabled="!!editing"
          /></el-form-item>
          <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="说明"><el-input v-model="form.description" type="textarea" /></el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status">
              <el-option label="启用" value="active" />
              <el-option label="停用" value="disabled" />
            </el-select>
          </el-form-item>
          <el-form-item label="权限">
            <PermissionTree
              ref="permissionTree"
              :data="tree"
              :model-value="form.permissions"
              @update:model-value="onPermissionChange"
            />
          </el-form-item>
        </el-form>
        <aside class="permission-preview">
          <h4>已选权限（{{ permissionPreview.length }}）</h4>
          <div v-for="[module, names] in selectedPermissionsByModule" :key="module" class="permission-group">
            <strong>{{ module }}</strong>
            <ul>
              <li v-for="name in names" :key="name">{{ name }}</li>
            </ul>
          </div>
        </aside>
      </div>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button
          v-permission="editing ? 'role:update' : 'role:create'"
          type="primary"
          :loading="mutation.pending.value"
          :disabled="mutation.pending.value"
          @click="save"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="usersDialog" :title="`${selectedRole?.name || ''} · 用户`" size="420">
      <el-table :data="roleUsers">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="display_name" label="显示名称" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="roleStatusMeta(row.status).type">{{ roleStatusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </section>
</template>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}
.role-search {
  width: 280px;
}
.batch-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  font-size: 12px;
}
.role-edit-layout {
  display: grid;
  grid-template-columns: 1fr 260px;
  gap: 16px;
}
.role-form {
  min-width: 0;
}
.permission-preview {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  max-height: 480px;
  overflow: auto;
}
.permission-preview h4 {
  margin: 0 0 10px;
  font-size: 13px;
}
.permission-group {
  margin-bottom: 12px;
}
.permission-group strong {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--el-color-primary);
}
.permission-group ul {
  margin: 0;
  padding-left: 16px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
