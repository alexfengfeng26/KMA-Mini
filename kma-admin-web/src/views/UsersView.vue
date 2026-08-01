<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import AppPagination from '../components/AppPagination.vue'
import FormDialog from '../components/FormDialog.vue'
import PageHeader from '../components/PageHeader.vue'
import PageState from '../components/PageState.vue'
import { useAuthStore } from '../stores/auth'
import { useMutationAction } from '../composables/useMutationAction'
import { userStatusMeta } from '../domain/systemCatalog'
import {
  batchResetPassword,
  batchUserStatus,
  createUser,
  getOrganizationsTree,
  getRoles,
  getUserDetail,
  getUserOrganizations,
  getUsersPage,
  setUserOrganizations,
  type OrganizationNode,
  type RoleRow,
  type UserCreateRequest,
  type UserRow,
} from '../api/identity'
import { authorizedJson, errorMessage, unwrap, api } from '../api/client'

interface FlatOrganization {
  orgId: number
  orgCode: string
  name: string
  children?: FlatOrganization[]
}

const auth = useAuthStore()
const mutation = useMutationAction()

const users = ref<UserRow[]>([])
const roles = ref<RoleRow[]>([])
const organizations = ref<FlatOrganization[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(true)
const error = ref('')
const selectedRows = ref<UserRow[]>([])

const filters = reactive({
  keyword: '',
  status: '',
  roleCode: '',
  orgId: undefined as number | undefined,
})

const detail = ref<UserRow>()
const detailDialog = ref(false)
const userOrganizations = ref<{ org_id: number; primary_org?: boolean }[]>([])

const userDialog = ref(false)
const userForm = reactive<UserCreateRequest>({
  username: '',
  displayName: '',
  initialPassword: '',
  generatePassword: false,
  roles: [],
})
const generatedPassword = ref('')
const generatedDialog = ref(false)

const roleDialog = ref(false)
const roleForm = reactive({ roles: [] as string[] })

const orgDialog = ref(false)
const orgForm = reactive({
  organizationIds: [] as number[],
  primaryOrganizationId: undefined as number | undefined,
})

const resetDialog = ref(false)
const resetForm = reactive({ newPassword: '', confirmPassword: '' })

const batchResetResult = ref<Record<string, string>>({})
const batchResetDialog = ref(false)

const assignableRoles = computed(() => roles.value.filter((item) => item.assignable !== false))

watch(
  () => [...orgForm.organizationIds],
  (ids) => {
    if (orgForm.primaryOrganizationId && ids.includes(orgForm.primaryOrganizationId)) return
    orgForm.primaryOrganizationId = ids[0]
  },
)

function flatten(nodes: OrganizationNode[]): FlatOrganization[] {
  return nodes.flatMap((node) => [node as FlatOrganization, ...flatten(node.children || [])])
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await getUsersPage({
      pageNum: page.value,
      pageSize: pageSize.value,
      keyword: filters.keyword,
      status: filters.status,
      roleCode: filters.roleCode,
      orgId: filters.orgId,
      sortBy: 'createTime',
      sortOrder: 'desc',
    })
    users.value = result.list
    total.value = result.total
    if (!roles.value.length && auth.hasAnyPermission(['role:read', 'user:role:assign'])) {
      roles.value = await getRoles()
    }
    if (!organizations.value.length && auth.hasAnyPermission(['org:read'])) {
      organizations.value = flatten(await getOrganizationsTree())
    }
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取用户')
  } finally {
    loading.value = false
  }
}

function searchUsers() {
  page.value = 1
  void load()
}

async function openCreate() {
  Object.assign(userForm, {
    username: '',
    displayName: '',
    initialPassword: '',
    generatePassword: false,
    roles: [],
  })
  userDialog.value = true
}

async function createUserHandler() {
  if (!userForm.generatePassword && (userForm.initialPassword || '').length < 12) return
  const result = await mutation.run(() => createUser(userForm), '用户已创建')
  if (result.ok) {
    userDialog.value = false
    if (result.value?.generatedPassword) {
      generatedPassword.value = result.value.generatedPassword
      generatedDialog.value = true
    }
    await load()
  }
}

async function openDetail(value: unknown) {
  const row = value as UserRow
  selectedRows.value = [row]
  detail.value = row
  detailDialog.value = true
  userOrganizations.value = []
  try {
    const [userDetail, orgs] = await Promise.all([
      getUserDetail(row.user_id),
      getUserOrganizations(row.user_id),
    ])
    detail.value = userDetail
    userOrganizations.value = orgs
  } catch {
    // keep row data
  }
}

function openRoles(value: unknown) {
  const row = value as UserRow
  selectedRows.value = [row]
  roleForm.roles = (row.roles || '').split(',').filter(Boolean)
  roleDialog.value = true
}

async function saveRoles() {
  if (!selectedRows.value.length) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.PUT('/api/v1/admin/users/{userId}/roles', {
          params: { path: { userId: selectedRows.value[0].user_id } },
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
  selectedRows.value = [row]
  const assigned = await getUserOrganizations(row.user_id)
  orgForm.organizationIds = assigned.map((item) => item.org_id)
  orgForm.primaryOrganizationId = assigned.find((item) => item.primary_org)?.org_id
  orgDialog.value = true
}

async function saveOrganizations() {
  if (!selectedRows.value.length) return
  const result = await mutation.run(
    () =>
      setUserOrganizations(
        selectedRows.value[0].user_id,
        orgForm.organizationIds,
        orgForm.primaryOrganizationId,
      ),
    '组织归属已更新',
  )
  if (result.ok) {
    orgDialog.value = false
    await load()
  }
}

async function toggleStatus(value: unknown) {
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
  selectedRows.value = [row]
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  resetDialog.value = true
}

async function resetPassword() {
  if (
    !selectedRows.value.length ||
    resetForm.newPassword.length < 12 ||
    resetForm.newPassword !== resetForm.confirmPassword
  ) {
    return
  }
  const result = await mutation.run(
    () =>
      authorizedJson<void>(`/api/v1/admin/users/${selectedRows.value[0].user_id}/reset-password`, {
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
  const result = await mutation.run(
    () =>
      unwrap(
        api.POST('/api/v1/admin/users/{userId}/revoke-tokens', {
          params: { path: { userId: row.user_id } },
        }),
      ),
    '用户令牌已撤销',
  )
  if (result.ok) await load()
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function selectionChange(rows: UserRow[]) {
  selectedRows.value = rows
}

async function batchAction(action: 'active' | 'disabled' | 'reset') {
  const targets = [...selectedRows.value]
  if (!targets.length) return
  if (action === 'reset') {
    try {
      await ElMessageBox.confirm(`确定对选中的 ${targets.length} 个用户批量重置密码吗？`, '批量重置密码', {
        type: 'warning',
      })
    } catch {
      return
    }
    const result = await mutation.run(
      () => batchResetPassword(targets.map((r) => r.user_id)),
      '批量重置密码完成',
    )
    if (result.ok && result.value) {
      batchResetResult.value = result.value
      batchResetDialog.value = true
    }
  } else {
    try {
      await ElMessageBox.confirm(
        `确定对选中的 ${targets.length} 个用户执行${action === 'active' ? '启用' : '停用'}吗？`,
        '批量操作',
        { type: 'warning' },
      )
    } catch {
      return
    }
    const result = await mutation.run(
      () =>
        batchUserStatus(
          targets.map((r) => r.user_id),
          action,
        ),
      '批量操作完成',
    )
    if (result.ok) await load()
  }
}

onMounted(load)
</script>

<template>
  <section class="panel">
    <PageHeader
      eyebrow="IDENTITY"
      title="用户管理"
      description="管理本地用户、角色分配、组织归属与安全状态。"
    >
      <template #actions>
        <el-button v-permission="'user:create'" type="primary" @click="openCreate">创建本地用户</el-button>
      </template>
    </PageHeader>

    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        clearable
        placeholder="搜索用户名或显示名称"
        @keyup.enter="searchUsers"
      />
      <el-select v-model="filters.status" placeholder="全部状态" clearable @change="searchUsers">
        <el-option label="启用" value="active" />
        <el-option label="停用" value="disabled" />
      </el-select>
      <el-select v-model="filters.roleCode" placeholder="全部角色" clearable @change="searchUsers">
        <el-option v-for="r in assignableRoles" :key="r.roleCode" :label="r.name" :value="r.roleCode" />
      </el-select>
      <el-select v-model="filters.orgId" placeholder="全部组织" clearable @change="searchUsers">
        <el-option v-for="o in organizations" :key="o.orgId" :label="o.name" :value="o.orgId" />
      </el-select>
      <el-button @click="searchUsers">查询</el-button>
    </div>

    <div v-if="selectedRows.length" class="batch-bar">
      <span>已选 {{ selectedRows.length }} 项</span>
      <el-button v-permission="'user:status:update'" size="small" @click="batchAction('active')"
        >批量启用</el-button
      >
      <el-button v-permission="'user:status:update'" size="small" @click="batchAction('disabled')"
        >批量停用</el-button
      >
      <el-button v-permission="'user:password:reset'" size="small" @click="batchAction('reset')"
        >批量重置密码</el-button
      >
      <el-button size="small" text @click="selectedRows = []">清空</el-button>
    </div>

    <PageState :loading="loading" :error="error" :empty="!users.length">
      <el-table :data="users" @selection-change="selectionChange">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="username" label="用户名" min-width="110" />
        <el-table-column prop="display_name" label="显示名称" min-width="110" />
        <el-table-column prop="identity_provider" label="身份来源" width="100" />
        <el-table-column label="角色" min-width="140">
          <template #default="{ row }">
            <el-tag
              v-for="role in (row.roles || '').split(',').filter(Boolean)"
              :key="role"
              size="small"
              class="mr-4"
            >
              {{ role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="组织" min-width="140">
          <template #default="{ row }">
            <el-tag
              v-for="org in (row.organizations || '').split(',').filter(Boolean)"
              :key="org"
              size="small"
              type="info"
              class="mr-4"
            >
              {{ org }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="150">
          <template #default="{ row }">{{ formatDateTime(row.last_login_time) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.create_time) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="userStatusMeta(row.status).type">{{ userStatusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button link @click="openDetail(row)">详情</el-button>
            <el-dropdown>
              <el-button link>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-permission="'user:role:assign'" @click="openRoles(row)"
                    >分配角色</el-dropdown-item
                  >
                  <el-dropdown-item v-permission="'org:member:manage'" @click="openOrganizations(row)"
                    >组织归属</el-dropdown-item
                  >
                  <el-dropdown-item v-permission="'user:status:update'" @click="toggleStatus(row)">
                    {{ row.status === 'active' ? '停用' : '启用' }}
                  </el-dropdown-item>
                  <el-dropdown-item v-permission="'user:password:reset'" @click="openReset(row)"
                    >重置密码</el-dropdown-item
                  >
                  <el-dropdown-item v-permission="'user:token:revoke'" @click="revoke(row)"
                    >撤销令牌</el-dropdown-item
                  >
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
        :disabled="loading"
        @change="load"
      />
    </PageState>

    <el-drawer v-model="detailDialog" title="用户详情" size="420">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item>
          <el-descriptions-item label="显示名称">{{ detail.display_name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="身份来源">{{
            detail.identity_provider || 'local'
          }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="userStatusMeta(detail.status).type">{{
              userStatusMeta(detail.status).label
            }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="需改密码">{{
            detail.must_change_password ? '是' : '否'
          }}</el-descriptions-item>
          <el-descriptions-item label="最后登录">{{ detail.last_login_time || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.create_time || '-' }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ detail.roles || '-' }}</el-descriptions-item>
          <el-descriptions-item label="组织">{{ detail.organizations || '-' }}</el-descriptions-item>
          <el-descriptions-item label="主组织">
            {{ userOrganizations.find((o) => o.primary_org)?.org_id || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-dialog v-model="userDialog" title="创建本地用户" width="520">
      <el-form label-position="top">
        <el-form-item label="用户名"><el-input v-model="userForm.username" /></el-form-item>
        <el-form-item label="显示名称"><el-input v-model="userForm.displayName" /></el-form-item>
        <el-form-item>
          <el-checkbox v-model="userForm.generatePassword">自动生成临时密码</el-checkbox>
        </el-form-item>
        <el-form-item v-if="!userForm.generatePassword" label="初始密码">
          <el-input v-model="userForm.initialPassword" type="password" show-password />
        </el-form-item>
        <el-form-item v-if="assignableRoles.length" label="角色">
          <el-select v-model="userForm.roles" multiple class="full-width">
            <el-option v-for="r in assignableRoles" :key="r.roleCode" :label="r.name" :value="r.roleCode" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialog = false">取消</el-button>
        <el-button
          v-permission="'user:create'"
          type="primary"
          :loading="mutation.pending.value"
          :disabled="
            !userForm.username.trim() ||
            (!userForm.generatePassword && (userForm.initialPassword || '').length < 12) ||
            mutation.pending.value
          "
          @click="createUserHandler"
        >
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="generatedDialog" title="临时密码" width="420">
      <el-alert type="warning" :closable="false" title="请立即复制并告知用户；该密码只显示一次。" />
      <div class="generated-password">{{ generatedPassword }}</div>
      <template #footer>
        <el-button type="primary" @click="generatedDialog = false">我已保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog" title="分配角色" width="520">
      <el-select v-model="roleForm.roles" multiple class="full-width">
        <el-option
          v-for="r in assignableRoles"
          :key="r.roleCode"
          :label="`${r.name} (${r.roleCode})`"
          :value="r.roleCode"
        />
      </el-select>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button
          v-permission="'user:role:assign'"
          type="primary"
          :loading="mutation.pending.value"
          :disabled="mutation.pending.value"
          @click="saveRoles"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="orgDialog" title="组织归属" width="560">
      <el-alert
        type="info"
        :closable="false"
        title="用户至少归属一个组织；清空所属组织时会自动回到根组织。"
        class="spaced-alert"
      />
      <el-form label-position="top">
        <el-form-item label="所属组织">
          <el-select v-model="orgForm.organizationIds" multiple class="full-width">
            <el-option
              v-for="o in organizations"
              :key="o.orgId"
              :label="`${o.name} (${o.orgCode})`"
              :value="o.orgId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主组织">
          <el-select
            v-model="orgForm.primaryOrganizationId"
            :disabled="!orgForm.organizationIds.length"
            class="full-width"
          >
            <el-option
              v-for="o in organizations.filter((item) => orgForm.organizationIds.includes(item.orgId))"
              :key="o.orgId"
              :label="o.name"
              :value="o.orgId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orgDialog = false">取消</el-button>
        <el-button
          v-permission="'org:member:manage'"
          type="primary"
          :loading="mutation.pending.value"
          :disabled="mutation.pending.value"
          @click="saveOrganizations"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <FormDialog
      v-model="resetDialog"
      :title="`重置 ${selectedRows[0]?.display_name || selectedRows[0]?.username || ''} 的密码`"
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
          <el-input
            v-model="resetForm.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
          />
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

    <el-dialog v-model="batchResetDialog" title="批量重置密码结果" width="520">
      <el-alert type="warning" :closable="false" title="以下临时密码只显示一次，请立即分发。" />
      <el-table
        :data="Object.entries(batchResetResult).map(([username, password]) => ({ username, password }))"
      >
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="password" label="临时密码" />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="batchResetDialog = false">我已保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.filter-bar > * {
  flex: 0 0 auto;
}
.filter-bar .el-input {
  width: 220px;
}
.filter-bar .el-select {
  width: 160px;
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  font-size: 12px;
}
.mr-4 {
  margin-right: 4px;
}
.generated-password {
  margin: 16px 0;
  padding: 12px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  font-family: ui-monospace, monospace;
  font-size: 16px;
  letter-spacing: 0.1em;
  text-align: center;
  user-select: all;
}
</style>
