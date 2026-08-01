<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import AppPagination from '../components/AppPagination.vue'
import PageHeader from '../components/PageHeader.vue'
import PageState from '../components/PageState.vue'
import { useMutationAction } from '../composables/useMutationAction'
import { orgStatusMeta } from '../domain/systemCatalog'
import {
  addOrganizationMembers,
  createOrganization,
  deleteOrganization,
  getOrganizationMembersPage,
  getOrganizationsTree,
  getUsersPage,
  moveOrganization,
  removeOrganizationMember,
  updateOrganization,
  type OrganizationNode,
  type UserRow,
} from '../api/identity'
import { errorMessage } from '../api/client'
import type TreeNode from 'element-plus/es/components/tree/src/model/node'

type FlatOrganizationNode = OrganizationNode & { depth: number }

const tree = ref<OrganizationNode[]>([])
const loading = ref(true)
const error = ref('')
const dialog = ref(false)
const editing = ref<OrganizationNode>()
const selected = ref<OrganizationNode>()
const membersDialog = ref(false)
const members = ref<(UserRow & { primary_org?: boolean })[]>([])
const memberTotal = ref(0)
const memberPage = ref(1)
const memberPageSize = ref(20)
const memberKeyword = ref('')
const memberLoading = ref(false)
const addMemberDialog = ref(false)
const availableUsers = ref<UserRow[]>([])
const addMemberForm = reactive({ userIds: [] as number[], primary: false })

const form = reactive({
  orgCode: '',
  name: '',
  parentId: undefined as number | undefined,
  status: 'active',
  sortOrder: 0,
})

const mutation = useMutationAction()

const orgCodeValid = computed(() => /^[a-zA-Z0-9][a-zA-Z0-9_-]{1,63}$/.test(form.orgCode))

function flatten(nodes: OrganizationNode[], depth = 0): FlatOrganizationNode[] {
  return nodes.flatMap((node) => [{ ...node, depth }, ...flatten(node.children || [], depth + 1)])
}
const options = computed(() => flatten(tree.value))
const validOptions = computed(() =>
  options.value.filter((o): o is FlatOrganizationNode & { orgId: number } => !!o.orgId),
)
const useVirtualTree = computed(() => options.value.length > 500)

async function load() {
  loading.value = true
  error.value = ''
  try {
    tree.value = await getOrganizationsTree()
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

function openEdit(value: unknown) {
  const row = value as OrganizationNode
  editing.value = row
  Object.assign(form, {
    orgCode: row.orgCode,
    name: row.name,
    parentId: row.parentId,
    status: row.status,
    sortOrder: row.sortOrder ?? 0,
  })
  dialog.value = true
}

async function save() {
  if (!editing.value && !orgCodeValid.value) return
  const result = await mutation.run(async () => {
    if (editing.value) {
      if (!editing.value.orgId) throw new Error('组织编号缺失')
      await updateOrganization(editing.value.orgId, {
        name: form.name,
        status: form.status,
        sortOrder: form.sortOrder,
      })
    } else {
      if (!form.parentId) throw new Error('请选择上级组织')
      await createOrganization({
        orgCode: form.orgCode,
        name: form.name,
        parentId: form.parentId,
        sortOrder: form.sortOrder,
      })
    }
  }, '组织已保存')
  if (!result.ok) return
  dialog.value = false
  await load()
}

async function remove(value: unknown) {
  const row = value as OrganizationNode
  const confirmed = await ElMessageBox.confirm(`确认删除组织“${row.name}”？`, '删除组织', {
    type: 'warning',
  }).then(
    () => true,
    () => false,
  )
  if (!confirmed) return
  const result = await mutation.run(() => deleteOrganization(row.orgId!), '组织已删除')
  if (result.ok) await load()
}

function isDescendant(ancestorId: number, maybeDescendantId: number): boolean {
  function find(nodes: OrganizationNode[]): OrganizationNode | undefined {
    for (const node of nodes) {
      if (node.orgId === ancestorId) return node
      const child = find(node.children || [])
      if (child) return child
    }
    return undefined
  }
  function walk(node: OrganizationNode): boolean {
    if (node.orgId === maybeDescendantId) return true
    return (node.children || []).some(walk)
  }
  const ancestor = find(tree.value)
  return ancestor ? walk(ancestor) : false
}

function orgNodeData(node: TreeNode) {
  return node.data as OrganizationNode
}

function allowDrop(draggingNode: TreeNode, dropNode: TreeNode, type: 'prev' | 'inner' | 'next') {
  if (type !== 'inner') return false
  const dragId = orgNodeData(draggingNode).orgId
  const dropId = orgNodeData(dropNode).orgId
  if (dragId === dropId) return false
  if (orgNodeData(dropNode).builtIn) return false
  return !isDescendant(dragId, dropId)
}

async function onNodeDrop(draggingNode: TreeNode, dropNode: TreeNode) {
  const dragId = orgNodeData(draggingNode).orgId
  const newParentId = orgNodeData(dropNode).orgId
  if (!dragId || !newParentId) return
  const result = await mutation.run(() => moveOrganization(dragId, newParentId), '组织已移动')
  if (result.ok) await load()
}

async function showMembers(value: unknown) {
  const row = value as OrganizationNode
  if (!row.orgId) return
  selected.value = row
  memberPage.value = 1
  memberKeyword.value = ''
  await loadMembers()
  membersDialog.value = true
}

async function loadMembers() {
  if (!selected.value?.orgId) return
  memberLoading.value = true
  try {
    const result = await getOrganizationMembersPage(selected.value.orgId, {
      pageNum: memberPage.value,
      pageSize: memberPageSize.value,
      keyword: memberKeyword.value,
      sortBy: 'username',
      sortOrder: 'asc',
    })
    members.value = result.list
    memberTotal.value = result.total
  } finally {
    memberLoading.value = false
  }
}

function searchMembers() {
  memberPage.value = 1
  void loadMembers()
}

async function openAddMember() {
  const result = await getUsersPage({ pageNum: 1, pageSize: 100 })
  availableUsers.value = result.list
  addMemberForm.userIds = []
  addMemberForm.primary = false
  addMemberDialog.value = true
}

async function addMembers() {
  if (!selected.value?.orgId || !addMemberForm.userIds.length) return
  const result = await mutation.run(
    () => addOrganizationMembers(selected.value!.orgId!, addMemberForm.userIds, addMemberForm.primary),
    '成员已添加',
  )
  if (result.ok) {
    addMemberDialog.value = false
    await loadMembers()
    await load()
  }
}

async function removeMember(value: unknown) {
  const row = value as UserRow & { primary_org?: boolean }
  if (!selected.value?.orgId) return
  const confirmed = await ElMessageBox.confirm(
    `确定将「${row.display_name || row.username}」从 ${selected.value.name} 中移除吗？`,
    '移除成员',
    { type: 'warning' },
  ).then(
    () => true,
    () => false,
  )
  if (!confirmed) return
  const result = await mutation.run(
    () => removeOrganizationMember(selected.value!.orgId!, row.user_id),
    '成员已移除',
  )
  if (result.ok) {
    await loadMembers()
    await load()
  }
}

watch(memberKeyword, () => {
  searchMembers()
})

onMounted(load)
</script>

<template>
  <section class="panel">
    <PageHeader eyebrow="ORGANIZATION TREE" title="组织管理" description="维护组织树、成员归属与组织编码。">
      <template #actions>
        <el-button v-permission="'org:create'" type="primary" @click="openCreate()">创建组织</el-button>
      </template>
    </PageHeader>

    <PageState :loading="loading" :error="error" :empty="!tree.length">
      <el-tree-v2
        v-if="useVirtualTree"
        :data="tree"
        :props="{ value: 'orgId', label: 'name', children: 'children' }"
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
      <el-tree
        v-else
        :data="tree"
        :props="{ label: 'name', children: 'children' }"
        node-key="orgId"
        default-expand-all
        draggable
        :allow-drop="allowDrop"
        @node-drop="onNodeDrop"
      >
        <template #default="{ data }">
          <div class="org-node">
            <div class="org-node-info">
              <strong>{{ data.name }}</strong>
              <span class="org-code">{{ data.orgCode }}</span>
              <el-tag size="small" :type="orgStatusMeta(data.status).type">{{
                orgStatusMeta(data.status).label
              }}</el-tag>
              <span class="org-members">{{ data.memberCount || 0 }} 名成员</span>
            </div>
            <div class="org-actions">
              <el-button link size="small" @click.stop="showMembers(data)">成员</el-button>
              <el-button v-permission="'org:create'" link size="small" @click.stop="openCreate(data)"
                >新增下级</el-button
              >
              <el-button v-permission="'org:update'" link size="small" @click.stop="openEdit(data)"
                >编辑</el-button
              >
              <el-button
                v-if="!data.builtIn"
                v-permission="'org:delete'"
                link
                size="small"
                type="danger"
                @click.stop="remove(data)"
              >
                删除
              </el-button>
            </div>
          </div>
        </template>
      </el-tree>
      <el-alert
        type="info"
        :closable="false"
        title="拖动组织节点到目标组织上即可移动；空间授权给上级组织后，其全部下级组织成员自动继承。"
        class="mt-12"
      />
    </PageState>

    <el-dialog v-model="dialog" :title="editing ? '编辑组织' : '创建组织'" width="540">
      <el-form label-position="top">
        <el-form-item
          label="组织编码"
          :error="!editing && !orgCodeValid && form.orgCode ? '只能包含字母、数字、下划线和中划线' : ''"
        >
          <el-input v-model="form.orgCode" :disabled="!!editing" />
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item v-if="!editing" label="上级组织">
          <el-select v-model="form.parentId" class="full-width">
            <el-option
              v-for="o in validOptions"
              :key="o.orgId"
              :label="`${o.name} (${o.orgCode})`"
              :value="o.orgId"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-select v-model="form.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button
          v-permission="editing ? 'org:update' : 'org:create'"
          type="primary"
          :loading="mutation.pending.value"
          :disabled="mutation.pending.value || (!editing && !orgCodeValid)"
          @click="save"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="membersDialog" :title="`${selected?.name || ''} · 成员`" size="560">
      <div class="member-toolbar">
        <el-input
          v-model="memberKeyword"
          clearable
          placeholder="搜索用户名或显示名称"
          class="member-search"
        />
        <el-button v-permission="'org:member:manage'" type="primary" @click="openAddMember"
          >添加成员</el-button
        >
      </div>
      <el-table v-loading="memberLoading" :data="members">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="display_name" label="显示名称" />
        <el-table-column label="主组织" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.primary_org" size="small" type="success">是</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button
              v-permission="'org:member:manage'"
              link
              type="danger"
              size="small"
              @click="removeMember(row)"
              >移除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <AppPagination
        v-model:page="memberPage"
        v-model:page-size="memberPageSize"
        :total="memberTotal"
        @change="loadMembers"
      />
    </el-drawer>

    <el-dialog v-model="addMemberDialog" :title="`添加成员到 ${selected?.name || ''}`" width="520">
      <el-form label-position="top">
        <el-form-item label="选择用户">
          <el-select v-model="addMemberForm.userIds" multiple filterable class="full-width">
            <el-option
              v-for="u in availableUsers"
              :key="u.user_id"
              :label="`${u.display_name || u.username} (${u.username})`"
              :value="u.user_id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="addMemberForm.primary">设为主组织</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addMemberDialog = false">取消</el-button>
        <el-button
          v-permission="'org:member:manage'"
          type="primary"
          :loading="mutation.pending.value"
          :disabled="!addMemberForm.userIds.length || mutation.pending.value"
          @click="addMembers"
        >
          添加
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.virtual-org-row {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(120px, 0.65fr) 96px auto;
  gap: 12px;
  align-items: center;
  width: 100%;
  min-width: 680px;
  padding-right: 8px;
}
.virtual-org-actions {
  display: flex;
  justify-content: flex-end;
}
.org-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 4px 0;
}
.org-node-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.org-code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.org-members {
  color: var(--el-text-color-regular);
  font-size: 12px;
}
.org-actions {
  display: flex;
  gap: 4px;
  flex: 0 0 auto;
}
.member-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.member-search {
  width: 240px;
}
.mt-12 {
  margin-top: 12px;
}
</style>
