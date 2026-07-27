import { defineFrontendModules } from '../contract'

export default defineFrontendModules([
  {
    id: 'knowledge.spaces',
    shell: 'console',
    featureKey: 'knowledge.spaces',
    title: '知识空间',
    order: 110,
    core: false,
    defaultEnabled: true,
    permissions: ['space:read'],
    navigation: { section: 'knowledge', path: '/console/spaces', label: '知识空间', badge: '空' },
    routes: [
      {
        path: 'spaces',
        name: 'console-spaces',
        component: () => import('../../views/SpacesView.vue'),
      },
    ],
  },
  {
    id: 'knowledge.documents',
    shell: 'console',
    featureKey: 'knowledge.documents',
    title: '技术文档',
    order: 120,
    core: false,
    defaultEnabled: true,
    permissions: ['document:read'],
    navigation: {
      section: 'knowledge',
      path: '/console/documents',
      label: '技术文档',
      badge: '文',
    },
    routes: [
      {
        path: 'documents',
        name: 'console-documents',
        component: () => import('../../views/DocumentsView.vue'),
      },
    ],
  },
  {
    id: 'knowledge.datasets',
    shell: 'console',
    featureKey: 'knowledge.datasets',
    title: '数据集与向量',
    order: 130,
    core: false,
    defaultEnabled: true,
    permissions: ['dataset:read'],
    navigation: {
      section: 'knowledge',
      path: '/console/datasets',
      label: '数据集与向量',
      badge: '向',
    },
    routes: [
      {
        path: 'datasets',
        name: 'console-datasets',
        component: () => import('../../views/DatasetsView.vue'),
      },
    ],
  },
])
