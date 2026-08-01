import { authorizedJson } from './client'

export interface UserRow {
  user_id: number
  username: string
  display_name?: string
  identity_provider?: string
  roles?: string
  organizations?: string
  must_change_password?: boolean
  status: string
  last_login_time?: string
  create_time?: string
}

export interface RoleRow {
  roleId: number
  roleCode: string
  name: string
  description?: string
  status: string
  permissions?: string[]
  permissionCount?: number
  userCount?: number
  builtIn?: boolean
  assignable?: boolean
}

export interface OrganizationNode {
  orgId: number
  orgCode: string
  name: string
  parentId?: number
  status: string
  builtIn?: boolean
  sortOrder?: number
  memberCount?: number
  children?: OrganizationNode[]
}

export interface PermissionNode {
  permissionCode: string
  name: string
  type: string
  scope?: string
  module?: string
  description?: string
  sortOrder: number
  children?: PermissionNode[]
}

export interface UserCreateRequest {
  username: string
  displayName?: string
  initialPassword?: string
  generatePassword?: boolean
  roles?: string[]
}

export interface RoleUpsertRequest {
  roleCode: string
  name: string
  description?: string
  status: string
  permissions: string[]
}

export interface OrganizationCreateRequest {
  orgCode: string
  name: string
  parentId: number
  sortOrder?: number
}

export interface OrganizationUpdateRequest {
  name: string
  status: string
  sortOrder?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export async function getUsersPage(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: string
  roleCode?: string
  orgId?: number
  sortBy?: string
  sortOrder?: string
}): Promise<PageResult<UserRow>> {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  })
  return authorizedJson(`/api/v1/admin/users/page?${query}`)
}

export async function getUserDetail(userId: number) {
  return authorizedJson<UserRow>(`/api/v1/admin/users/${userId}`)
}

export async function createUser(body: UserCreateRequest) {
  return authorizedJson<{ userId: number; generatedPassword?: string }>('/api/v1/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function batchUserStatus(userIds: number[], status: 'active' | 'disabled') {
  return authorizedJson<void>('/api/v1/admin/users/batch/status', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userIds, status }),
  })
}

export async function batchResetPassword(userIds: number[]) {
  return authorizedJson<Record<string, string>>('/api/v1/admin/users/batch/reset-password', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userIds, status: 'active' }),
  })
}

export async function getRoles() {
  return authorizedJson<RoleRow[]>('/api/v1/admin/roles')
}

export async function createRole(body: RoleUpsertRequest) {
  return authorizedJson<number>('/api/v1/admin/roles', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function updateRole(roleId: number, body: RoleUpsertRequest) {
  return authorizedJson<void>(`/api/v1/admin/roles/${roleId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function deleteRole(roleId: number) {
  return authorizedJson<void>(`/api/v1/admin/roles/${roleId}`, { method: 'DELETE' })
}

export async function cloneRole(roleId: number) {
  return authorizedJson<number>(`/api/v1/admin/roles/${roleId}/clone`, { method: 'POST' })
}

export async function batchRoleStatus(roleIds: number[], status: 'active' | 'disabled') {
  return authorizedJson<void>('/api/v1/admin/roles/batch/status', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ roleIds, status }),
  })
}

export async function getRoleUsers(roleId: number) {
  return authorizedJson<UserRow[]>(`/api/v1/admin/roles/${roleId}/users`)
}

export async function getPermissionsTree() {
  return authorizedJson<PermissionNode[]>('/api/v1/admin/permissions/tree')
}

export async function getOrganizationsTree() {
  return authorizedJson<OrganizationNode[]>('/api/v1/admin/organizations/tree')
}

export async function createOrganization(body: OrganizationCreateRequest) {
  return authorizedJson<number>('/api/v1/admin/organizations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function updateOrganization(orgId: number, body: OrganizationUpdateRequest) {
  return authorizedJson<void>(`/api/v1/admin/organizations/${orgId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function moveOrganization(orgId: number, parentId: number) {
  return authorizedJson<void>(`/api/v1/admin/organizations/${orgId}/move`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ parentId }),
  })
}

export async function deleteOrganization(orgId: number) {
  return authorizedJson<void>(`/api/v1/admin/organizations/${orgId}`, { method: 'DELETE' })
}

export async function getOrganizationMembersPage(
  orgId: number,
  params: { pageNum: number; pageSize: number; keyword?: string; sortBy?: string; sortOrder?: string },
): Promise<PageResult<UserRow & { primary_org?: boolean }>> {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  })
  return authorizedJson(`/api/v1/admin/organizations/${orgId}/members/page?${query}`)
}

export async function addOrganizationMembers(orgId: number, userIds: number[], primary = false) {
  return authorizedJson<void>(`/api/v1/admin/organizations/${orgId}/members`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userIds, primary }),
  })
}

export async function removeOrganizationMember(orgId: number, userId: number) {
  return authorizedJson<void>(`/api/v1/admin/organizations/${orgId}/members/${userId}`, {
    method: 'DELETE',
  })
}

export async function getUserOrganizations(userId: number) {
  return authorizedJson<{ org_id: number; primary_org?: boolean }[]>(
    `/api/v1/admin/users/${userId}/organizations`,
  )
}

export async function setUserOrganizations(
  userId: number,
  organizationIds: number[],
  primaryOrganizationId?: number,
) {
  return authorizedJson<void>(`/api/v1/admin/users/${userId}/organizations`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ organizationIds, primaryOrganizationId }),
  })
}
