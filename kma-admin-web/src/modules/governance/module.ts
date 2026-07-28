import { defineFrontendModules } from '../contract'

export default defineFrontendModules([
  {
    id: 'governance.contents',
    shell: 'console',
    featureKey: 'governance.contents',
    title: '内容库',
    order: 10,
    core: false,
    defaultEnabled: true,
    permissions: ['content:read'],
    navigation: { section: 'governance', path: '/console/contents', label: '内容库', badge: '库' },
    routes: [
      {
        path: 'contents',
        name: 'console-contents',
        component: () => import('../../views/ContentGovernanceView.vue'),
      },
    ],
  },
  {
    id: 'governance.reviews',
    shell: 'console',
    featureKey: 'governance.reviews',
    title: '审核中心',
    order: 20,
    core: false,
    defaultEnabled: true,
    permissions: ['content:review'],
    navigation: { section: 'governance', path: '/console/reviews', label: '审核中心', badge: '核' },
    routes: [
      {
        path: 'reviews',
        name: 'console-reviews',
        component: () => import('../../views/ContentGovernanceView.vue'),
        meta: { governanceMode: 'review' },
      },
    ],
  },
  {
    id: 'governance.publications',
    shell: 'console',
    featureKey: 'governance.publications',
    title: '发布管理',
    order: 30,
    core: false,
    defaultEnabled: true,
    permissions: ['content:publish'],
    navigation: {
      section: 'governance',
      path: '/console/publications',
      label: '发布管理',
      badge: '发',
    },
    routes: [
      {
        path: 'publications',
        name: 'console-publications',
        component: () => import('../../views/ContentGovernanceView.vue'),
        meta: { governanceMode: 'publication' },
      },
    ],
  },
  {
    id: 'governance.topics',
    shell: 'console',
    featureKey: 'governance.topics',
    title: '分类专题',
    order: 40,
    core: false,
    defaultEnabled: true,
    permissions: ['topic:manage'],
    navigation: { section: 'governance', path: '/console/topics', label: '分类专题', badge: '题' },
    routes: [
      {
        path: 'topics',
        name: 'console-topics',
        component: () => import('../../views/TopicsManagementView.vue'),
      },
    ],
  },
  {
    id: 'governance.portal-config',
    shell: 'console',
    featureKey: 'governance.portal-config',
    title: '门户配置',
    order: 50,
    core: false,
    defaultEnabled: true,
    permissions: ['portal:configure'],
    navigation: {
      section: 'governance',
      path: '/console/portal-config',
      label: '门户配置',
      badge: '门',
    },
    routes: [
      {
        path: 'portal-config',
        name: 'console-portal-config',
        component: () => import('../../views/PortalConfigView.vue'),
      },
    ],
  },
  {
    id: 'governance.portal-appearance',
    shell: 'console',
    featureKey: 'governance.portal-appearance',
    title: '门户设计中心',
    order: 60,
    core: false,
    defaultEnabled: true,
    permissions: ['portal-site:read'],
    navigation: {
      section: 'governance',
      path: '/console/portal-appearance',
      label: '门户设计中心',
      badge: '饰',
    },
    routes: [
      {
        path: 'portal-appearance',
        name: 'console-portal-appearance',
        component: () => import('../../views/theme/PortalThemeStudioView.vue'),
      },
    ],
  },
])
