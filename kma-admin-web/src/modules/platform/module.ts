import { defineFrontendModules } from '../contract'

export default defineFrontendModules([
  {
    id: 'account.profile',
    shell: 'console',
    featureKey: 'account.profile',
    title: '个人与密码',
    order: 1000,
    core: true,
    defaultEnabled: true,
    permissions: [],
    navigation: {
      section: 'account',
      path: '/console/profile',
      label: '个人与密码',
      badge: '我',
    },
    routes: [
      {
        path: 'profile',
        name: 'console-profile',
        component: () => import('../../views/ProfileView.vue'),
      },
    ],
  },
])
