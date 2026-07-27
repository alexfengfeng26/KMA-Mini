import { defineFrontendModules } from '../contract'

export default defineFrontendModules([
  {
    id: 'access.users',
    shell: 'console',
    featureKey: 'access.users',
    title: '用户管理',
    order: 410,
    core: false,
    defaultEnabled: true,
    permissions: ['user:read'],
    navigation: {
      section: 'access',
      path: '/console/access/users',
      label: '用户管理',
      badge: '用',
    },
    routes: [
      {
        path: 'access/users',
        name: 'console-users',
        component: () => import('../../views/UsersView.vue'),
      },
    ],
  },
  {
    id: 'access.roles',
    shell: 'console',
    featureKey: 'access.roles',
    title: '角色权限',
    order: 420,
    core: false,
    defaultEnabled: true,
    permissions: ['role:read'],
    navigation: {
      section: 'access',
      path: '/console/access/roles',
      label: '角色权限',
      badge: '角',
    },
    routes: [
      {
        path: 'access/roles',
        name: 'console-roles',
        component: () => import('../../views/RolesView.vue'),
      },
    ],
  },
  {
    id: 'access.organizations',
    shell: 'console',
    featureKey: 'access.organizations',
    title: '组织管理',
    order: 430,
    core: false,
    defaultEnabled: true,
    permissions: ['org:read'],
    navigation: {
      section: 'access',
      path: '/console/access/organizations',
      label: '组织管理',
      badge: '组',
    },
    routes: [
      {
        path: 'access/organizations',
        name: 'console-organizations',
        component: () => import('../../views/OrganizationsView.vue'),
      },
    ],
  },
])
