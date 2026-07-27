<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, asList, asRecord, authorizedJson, errorMessage, unwrap } from '../api/client'
import AppPagination from '../components/AppPagination.vue'
import FormDialog from '../components/FormDialog.vue'
import PageState from '../components/PageState.vue'
import { useAuthStore } from '../stores/auth'
import { useMutationAction } from '../composables/useMutationAction'
import { getAuthorizedPage } from '../api/page'

interface UserRow {
  user_id: number
  username: string
  display_name?: string
  identity_provider?: string
  roles?: string
  organizations?: string
  must_change_password?: boolean
  status: string
}

interface RoleRow {
  roleCode: string
  name: string
  assignable?: boolean
}

interface OrganizationRow {
  orgId: number
  orgCode: string
  name: string
  children?: OrganizationRow[]
}

interface UserOrganizationRow {
  org_id: number
  primary_org?: boolean
}

const auth = useAuthStore()
const users = ref<UserRow[]>([]),
  roles = ref<RoleRow[]>([]),
  organizations = ref<OrganizationRow[]>([])
const page = ref(1),
  pageSize = ref(20),
  total = ref(0),
  keyword = ref('')
const loading = ref(true),
  error = ref('')
const userDialog = ref(false),
  roleDialog = ref(false),
  orgDialog = ref(false),
  resetDialog = ref(false),
  selected = ref<UserRow>()
const userForm = reactive({ username: '', displayName: '', initialPassword: '', roles: [] as string[] })
const roleForm = reactive({ roles: [] as string[] })
const orgForm = reactive({
  organizationIds: [] as number[],
  primaryOrganizationId: undefined as number | undefined,
})
const resetForm = reactive({ newPassword: '', confirmPassword: '' })
const mutation = useMutationAction()
const assignableRoles = computed(() => roles.value.filter((item) => item.assignable !== false))
watch(
  () => [...orgForm.organizationIds],
  (ids) => {
    if (orgForm.primaryOrganizationId && ids.includes(orgForm.primaryOrganizationId)) return
    orgForm.primaryOrganizationId = ids[0]
  },
)

function flatten(nodes: OrganizationRow[]): OrganizationRow[] {
  return nodes.flatMap((node) => [node, ...flatten(node.children || [])])
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await getAuthorizedPage<UserRow>('/api/v1/admin/users/page', {
      pageNum: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value,
      sortBy: 'createTime',
      sortOrder: 'desc',
    })
    users.value = result.list
    total.value = result.total
    roles.value = auth.hasAnyPermission(['role:read', 'user:role:assign'])
      ? (asList(await unwrap(api.GET('/api/v1/admin/roles'))) as RoleRow[])
      : []
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取用户')
  } finally {
    loading.value = false
  }
}
async function createUser() {
  const result = await mutation.run(
    () => unwrap(api.POST('/api/v1/admin/users', { body: userForm })),
    '用户已创建',
  )
  if (result.ok) {
    userDialog.value = false
    Object.assign(userForm, { username: '', displayName: '', initialPassword: '', roles: [] })
    await load()
  }
}
function openRoles(value: unknown) {
  const row = value as UserRow
  selected.value = row
  roleForm.roles = (row.roles || '').split(',').filter(Boolean)
  roleDialog.value = true
}
async function saveRoles() {
  if (!selected.value) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.PUT('/api/v1/admin/users/{userId}/roles', {
          params: { path: { userId: selected.value?.user_id as number } },
          body: roleForm,
        }),
      ),
    '用户角色已更新',
  )
  if (result.ok) {
    roleDialog.value = false
    await load()
  }
}
async function openOrganizations(value: unknown) {
  const row = value as UserRow
  selected.value = row
  const [tree, current] = await Promise.all([
    unwrap(api.GET('/api/v1/admin/organizations/tree')),
    unwrap(
      api.GET('/api/v1/admin/users/{userId}/organizations', { params: { path: { userId: row.user_id } } }),
    ),
  ])
  organizations.value = flatten(asList(tree) as OrganizationRow[])
  const assigned = asList(current).map((value) => asRecord(value) as unknown as UserOrganizationRow)
  orgForm.organizationIds = assigned.map((item) => item.org_id)
  orgForm.primaryOrganizationId = assigned.find((item) => item.primary_org)?.org_id
  orgDialog.value = true
}
async function saveOrganizations() {
  if (!selected.value) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.PUT('/api/v1/admin/users/{userId}/organizations', {
          params: { path: { userId: selected.value?.user_id as number } },
          body: orgForm,
        }),
      ),
    '组织归属已更新',
  )
  if (result.ok) {
    orgDialog.value = false
    await load()
  }
}
async function toggle(value: unknown) {
  const row = value as UserRow
  const result = await mutation.run(
    () =>
      unwrap(
        api.PUT('/api/v1/admin/users/{userId}/status', {
          params: {
            path: { userId: row.user_id },
            query: { status: row.status === 'active' ? 'disabled' : 'active' },
          },
        }),
      ),
    '用户状态已更新',
  )
  if (result.ok) await load()
}
function openReset(value: unknown) {
  const row = value as UserRow
  selected.value = row
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  resetDialog.value = true
}
async function resetPassword() {
  if (
    !selected.value ||
    resetForm.newPassword.length < 12 ||
    resetForm.newPassword !== resetForm.confirmPassword
  ) {
    return
  }
  const result = await mutation.run(
    () =>
      authorizedJson<void>(`/api/v1/admin/users/${selected.value?.user_id}/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ newPassword: resetForm.newPassword }),
      }),
    '临时密码已更新；用户下次登录必须修改密码',
  )
  if (result.ok) {
    resetDialog.value = false
    resetForm.newPassword = ''
    resetForm.confirmPassword = ''
  }
}
async function revoke(value: unknown) {
  const row = value as UserRow
  await mutation.run(
    () =>
      unwrap(
        api.POST('/api/v1/admin/users/{userId}/revoke-tokens', {
          params: { path: { userId: row.user_id } },
        }),
      ),
    '用户令牌已撤销',
  )
}

function searchUsers() {
  page.value = 1
  void load()
}

onMounted(load)
</script>

<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">IDENTITY</span>
        <h2>用户管理</h2>
      </div>
      <el-button v-permission="'user:create'" type="primary" @click="userDialog = true"
        >创建本地用户</el-button
      >
    </div>
    <div class="filter-bar">
      <el-input v-model="keyword" clearable placeholder="搜索用户名或显示名称" @keyup.enter="searchUsers" />
      <el-button @click="searchUsers">查询</el-button>
    </div>
    <PageState :loading="loading" :error="error" :empty="!users.length">
      <el-table :data="users"
        ><el-table-column prop="username" label="用户名" /><el-table-column
          prop="display_name"
          label="显示名称"
        /><el-table-column prop="identity_provider" label="身份来源" /><el-table-column
          prop="roles"
          label="角色"
        /><el-table-column prop="organizations" label="组织" /><el-table-column
          prop="must_change_password"
          label="需改密码"
        /><el-table-column prop="status" label="状态" /><el-table-column label="操作" min-width="390"
          ><template #default="{ row }"
            ><el-button v-permission="'user:role:assign'" link @click="openRoles(row)">分配角色</el-button
            ><el-button v-permission="'org:member:manage'" link @click="openOrganizations(row)"
              >组织归属</el-button
            ><el-button v-permission="'user:status:update'" link @click="toggle(row)">{{
              row.status === 'active' ? '停用' : '启用'
            }}</el-button
            ><el-button v-permission="'user:password:reset'" link @click="openReset(row)">重置密码</el-button
            ><el-button v-permission="'user:token:revoke'" link type="danger" @click="revoke(row)"
              >撤销令牌</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <AppPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
        :disabled="loading"
        @change="load"
      />
    </PageState>
  </section>
  <el-dialog v-model="userDialog" title="创建本地用户" width="520"
    ><el-form label-position="top"
      ><el-form-item label="用户名"><el-input v-model="userForm.username" /></el-form-item
      ><el-form-item label="显示名称"><el-input v-model="userForm.displayName" /></el-form-item
      ><el-form-item label="初始密码"
        ><el-input v-model="userForm.initialPassword" type="password" show-password /></el-form-item
      ><el-form-item v-if="assignableRoles.length" label="角色"
        ><el-select v-model="userForm.roles" multiple class="full-width"
          ><el-option
            v-for="r in assignableRoles"
            :key="r.roleCode"
            :label="r.name"
            :value="r.roleCode" /></el-select></el-form-item></el-form
    ><template #footer
      ><el-button @click="userDialog = false">取消</el-button
      ><el-button
        v-permission="'user:create'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="
          !userForm.username.trim() || userForm.initialPassword.length < 12 || mutation.pending.value
        "
        @click="createUser"
        >创建</el-button
      ></template
    ></el-dialog
  >
  <el-dialog v-model="roleDialog" title="分配角色" width="520"
    ><el-select v-model="roleForm.roles" multiple class="full-width"
      ><el-option
        v-for="r in assignableRoles"
        :key="r.roleCode"
        :label="`${r.name} (${r.roleCode})`"
        :value="r.roleCode" /></el-select
    ><template #footer
      ><el-button @click="roleDialog = false">取消</el-button
      ><el-button
        v-permission="'user:role:assign'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="mutation.pending.value"
        @click="saveRoles"
        >保存</el-button
      ></template
    ></el-dialog
  >
  <el-dialog v-model="orgDialog" title="组织归属" width="560"
    ><el-alert
      type="info"
      :closable="false"
      title="用户至少归属一个组织；清空所属组织时会自动回到根组织。"
      class="spaced-alert"
    /><el-form label-position="top"
      ><el-form-item label="所属组织"
        ><el-select v-model="orgForm.organizationIds" multiple class="full-width"
          ><el-option
            v-for="o in organizations"
            :key="o.orgId"
            :label="`${o.name} (${o.orgCode})`"
            :value="o.orgId" /></el-select></el-form-item
      ><el-form-item label="主组织"
        ><el-select
          v-model="orgForm.primaryOrganizationId"
          :disabled="!orgForm.organizationIds.length"
          class="full-width"
          ><el-option
            v-for="o in organizations.filter((item) => orgForm.organizationIds.includes(item.orgId))"
            :key="o.orgId"
            :label="o.name"
            :value="o.orgId" /></el-select></el-form-item></el-form
    ><template #footer
      ><el-button @click="orgDialog = false">取消</el-button
      ><el-button
        v-permission="'org:member:manage'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="mutation.pending.value"
        @click="saveOrganizations"
        >保存</el-button
      ></template
    ></el-dialog
  >
  <FormDialog
    v-model="resetDialog"
    :title="`重置 ${selected?.display_name || selected?.username || ''} 的密码`"
    :width="520"
    :submitting="mutation.pending.value"
  >
    <el-alert
      class="spaced-alert"
      type="warning"
      :closable="false"
      title="新密码只通过加密请求体提交；保存后该用户的现有会话会失效，并须在下次登录时修改密码。"
    />
    <el-form label-position="top">
      <el-form-item
        label="新临时密码"
        :error="resetForm.newPassword && resetForm.newPassword.length < 12 ? '临时密码至少 12 个字符' : ''"
      >
        <el-input v-model="resetForm.newPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item
        label="确认新密码"
        :error="
          resetForm.confirmPassword && resetForm.newPassword !== resetForm.confirmPassword
            ? '两次输入的密码不一致'
            : ''
        "
      >
        <el-input
          v-model="resetForm.confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
          @keyup.enter="resetPassword"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="resetDialog = false">取消</el-button>
      <el-button
        v-permission="'user:password:reset'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="
          resetForm.newPassword.length < 12 ||
          resetForm.newPassword !== resetForm.confirmPassword ||
          mutation.pending.value
        "
        @click="resetPassword"
      >
        确认重置
      </el-button>
    </template>
  </FormDialog>
</template>
