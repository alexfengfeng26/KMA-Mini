import { defineFrontendModules } from '../contract'

export default defineFrontendModules([
  {
    id: 'intelligence.qa',
    shell: 'console',
    featureKey: 'intelligence.qa',
    title: '问答实验室',
    order: 210,
    core: false,
    defaultEnabled: true,
    permissions: ['qa:use'],
    navigation: {
      section: 'intelligence',
      path: '/console/qa',
      label: '问答实验室',
      badge: '问',
    },
    routes: [{ path: 'qa', name: 'console-qa', component: () => import('../../views/QaView.vue') }],
  },
  {
    id: 'intelligence.retrieval',
    shell: 'console',
    featureKey: 'intelligence.retrieval',
    title: '检索调试',
    order: 220,
    core: false,
    defaultEnabled: true,
    permissions: ['retrieval:use'],
    navigation: {
      section: 'intelligence',
      path: '/console/retrieval',
      label: '检索调试',
      badge: '检',
    },
    routes: [
      {
        path: 'retrieval',
        name: 'console-retrieval',
        component: () => import('../../views/RetrievalView.vue'),
      },
    ],
  },
  {
    id: 'intelligence.evaluations',
    shell: 'console',
    featureKey: 'intelligence.evaluations',
    title: 'RAG 评测',
    order: 230,
    core: false,
    defaultEnabled: true,
    permissions: ['evaluation:read'],
    navigation: {
      section: 'intelligence',
      path: '/console/evaluations',
      label: 'RAG 评测',
      badge: '评',
    },
    routes: [
      {
        path: 'evaluations',
        name: 'console-evaluations',
        component: () => import('../../views/EvaluationsView.vue'),
      },
    ],
  },
])
