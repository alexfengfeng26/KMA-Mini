// 本地主题渲染验证：使用真实的 V4 buildThemeDocument + mock bootstrap 渲染各主题页面
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildThemeDocument } from './themeRuntime.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = join(here, '..', '..')
const themesRoot = join(repoRoot, 'src', 'main', 'resources', 'portal-themes')
const outRoot = join(here, 'out')

const recent = [
  { contentId: 101, title: '关于进一步优化政务服务提升行政效能的指导意见', summary: '明确政务服务标准化、规范化、便利化建设的总体要求、重点任务与保障措施。', issuingAuthority: '国务院办公厅', documentNumber: '国办发〔2026〕12 号', publishDate: '2026-07-21', contentType: '政策文件', validityStatus: '现行有效' },
  { contentId: 102, title: '数字政府建设数据共享管理办法', summary: '规范政务数据目录编制、共享交换与安全使用，明确各级部门职责边界。', issuingAuthority: '数据发展司', documentNumber: '数发规〔2026〕4 号', publishDate: '2026-07-15', contentType: '制度规范', validityStatus: '现行有效' },
  { contentId: 103, title: '基层治理典型案例汇编（2026 年第一批）', summary: '收录十二个城市在社区网格化、接诉即办、协商议事方面的可复制经验。', issuingAuthority: '政策研究室', documentNumber: '', publishDate: '2026-07-08', contentType: '案例汇编', validityStatus: '现行有效' },
  { contentId: 104, title: '行政复议文书写作规范与常见错误解析', summary: '结合真实案例讲解复议决定书的结构、说理方式与高频格式错误。', issuingAuthority: '法规司', documentNumber: '', publishDate: '2026-06-30', contentType: '业务指引', validityStatus: '现行有效' },
  { contentId: 105, title: '公共安全应急预案编制指南（修订版）', summary: '覆盖风险评估、组织体系、响应分级与演练评估的全流程编制方法。', issuingAuthority: '应急管理办公室', documentNumber: '应急办函〔2026〕31 号', publishDate: '2026-06-18', contentType: '业务指引', validityStatus: '现行有效' },
  { contentId: 106, title: '政务服务窗口人员行为准则与考核办法', summary: '明确首问负责、一次性告知、限时办结等服务规范及配套考核指标。', issuingAuthority: '政务服务局', documentNumber: '政服发〔2026〕7 号', publishDate: '2026-06-02', contentType: '制度规范', validityStatus: '现行有效' },
]

const topics = [
  { topicCode: 'policy-study', name: '政策学习专题', description: '汇聚最新政策原文、权威解读与分层分类学习辅导材料。', coverColor: '#8d1b20' },
  { topicCode: 'governance-case', name: '基层治理案例库', description: '按地区与场景组织的治理实践案例，支持对照检索与引用。', coverColor: '#b8860b' },
  { topicCode: 'document-writing', name: '公文写作规范', description: '文种选用、格式标准、常用句式与易错点清单。', coverColor: '#2f5d3a' },
  { topicCode: 'data-sharing', name: '数据共享与开放', description: '数据目录、共享流程、接口规范与安全合规要求。', coverColor: '#1f4e79' },
  { topicCode: 'rule-of-law', name: '依法行政', description: '行政执法、复议应诉、合法性审查相关制度与指引。', coverColor: '#5a3d8a' },
  { topicCode: 'service-hall', name: '政务服务大厅', description: '窗口设置、事项清单、办理流程与服务评价管理。', coverColor: '#0f766e' },
]

const favorites = [
  { favoriteId: 1, docId: 101, title: '关于进一步优化政务服务提升行政效能的指导意见', issuingAuthority: '国务院办公厅', lastReadAt: '2026-07-26' },
  { favoriteId: 2, docId: 103, title: '基层治理典型案例汇编（2026 年第一批）', issuingAuthority: '政策研究室', lastReadAt: '2026-07-24' },
  { favoriteId: 3, docId: 104, title: '行政复议文书写作规范与常见错误解析', issuingAuthority: '法规司', lastReadAt: '2026-07-20' },
]

const history = [
  { docId: 105, title: '公共安全应急预案编制指南（修订版）', issuingAuthority: '应急管理办公室', lastReadAt: '2026-07-27' },
  { docId: 101, title: '关于进一步优化政务服务提升行政效能的指导意见', issuingAuthority: '国务院办公厅', lastReadAt: '2026-07-26' },
  { docId: 102, title: '数字政府建设数据共享管理办法', issuingAuthority: '数据发展司', lastReadAt: '2026-07-25' },
  { docId: 106, title: '政务服务窗口人员行为准则与考核办法', issuingAuthority: '政务服务局', lastReadAt: '2026-07-23' },
]

const categories = [
  { contentType: 'policy', name: '政策文件', total: 128 },
  { contentType: 'regulation', name: '制度规范', total: 86 },
  { contentType: 'guide', name: '业务指引', total: 64 },
  { contentType: 'case', name: '案例汇编', total: 42 },
  { contentType: 'notice', name: '通知公告', total: 37 },
]

const currentContent = {
  title: '关于进一步优化政务服务提升行政效能的指导意见',
  summary: '明确政务服务标准化、规范化、便利化建设的总体要求、重点任务与保障措施。',
  sections: [
    { content: '一、总体要求\n以人民为中心，坚持问题导向、需求导向，推动政务服务从“能办”向“好办、易办”转变。到 2027 年，基本形成泛在可及、智慧便捷、公平普惠的政务服务体系。' },
    { content: '二、重点任务\n（一）推进事项标准化。全面梳理政务服务事项清单，统一事项名称、编码、依据、类型等基本要素，实现同一事项无差别受理、同标准办理。' },
    { content: '（二）强化数据共享。完善政务数据共享协调机制，推动高频电子证照跨地区、跨部门互认共享，减少证明材料重复提交。' },
    { content: '三、保障措施\n各地区要加强组织领导，健全考核评估机制，将政务服务效能纳入年度绩效考核范围，确保各项任务落地见效。' },
  ],
}

const pages = [
  ['home', '首页'],
  ['library', '资料中心'],
  ['topics', '专题目录'],
  ['ask', 'AI 知识问答'],
  ['content', '资料正文'],
  ['search', '搜索结果'],
  ['favorites', '我的收藏'],
  ['profile', '个人中心'],
]

const themeKeys = process.argv.slice(2)
const targets = themeKeys.length ? themeKeys : ['governance-blue', 'heritage-red', 'ink-night']

for (const themeKey of targets) {
  const themeDir = join(themesRoot, themeKey)
  const manifest = JSON.parse(readFileSync(join(themeDir, 'theme.json'), 'utf8'))
  const files = {}
  for (const path of manifest.files) {
    files[path] = readFileSync(join(themeDir, path), 'utf8')
  }
  const runtime = {
    versionId: 1,
    versionNo: 1,
    status: 'published',
    manifest: { capabilities: manifest.capabilities, entry: manifest.entry },
    checksum: 'preview',
    themeKey,
    displayName: manifest.displayName,
    files,
  }
  mkdirSync(join(outRoot, themeKey), { recursive: true })
  for (const [slug, title] of pages) {
    const bootstrap = {
      site: { siteId: 1, siteKey: 'demo', name: '城区治理知识门户' },
      publishedVersion: 1,
      revision: 'preview',
      schemaVersion: 4,
      shell: {},
      theme: undefined,
      modules: {},
      search: undefined,
      assistant: undefined,
      page: { slug, kind: 'system', title, template: `pages/${slug}.html` },
      extensions: [],
      portalData: { config: { unitName: '城区治理示范区', helpText: '', currentTopicCode: '' }, categories, recent, topics, favorites, history },
      themeData: {
        user: { userId: 7, username: 'zhang.my', displayName: '张明远' },
        currentContent,
      },
    }
    const html = buildThemeDocument(runtime, bootstrap)
    writeFileSync(join(outRoot, themeKey, `${slug}.html`), html)
  }
  console.log(`rendered ${themeKey}`)
}
