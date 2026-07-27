import type { LayoutNode, PortalSiteConfigV3 } from '../siteConfig'

export interface DesignerStructureNode {
  key: string
  label: string
  kind: 'group' | 'page' | 'node'
  pageSlug?: string
  nodeId?: string
  nodeType?: string
  locked?: boolean
  children?: DesignerStructureNode[]
}

function layoutChildren(pageSlug: string, node: LayoutNode): DesignerStructureNode {
  return {
    key: `node:${pageSlug}:${node.id}`,
    label: node.name || node.id,
    kind: 'node',
    pageSlug,
    nodeId: node.id,
    nodeType: node.type,
    locked: node.locked,
    children: 'children' in node ? node.children.map((child) => layoutChildren(pageSlug, child)) : undefined,
  }
}

export function orderedPageSlugs(config: PortalSiteConfigV3): string[] {
  const available = new Set(Object.keys(config.pages))
  const ordered: string[] = []
  for (const item of config.shell.navigation) {
    if (available.delete(item.target)) ordered.push(item.target)
  }
  return [...ordered, ...Object.keys(config.pages).filter((slug) => available.has(slug))]
}

export function buildDesignerStructure(config: PortalSiteConfigV3): DesignerStructureNode[] {
  const shellChildren = [
    { slug: '$header', label: '全局页头', root: config.shell.header },
    { slug: '$footer', label: '全局页脚', root: config.shell.footer },
  ].map(({ slug, label, root }) => ({
    key: `page:${slug}`,
    label,
    kind: 'page' as const,
    pageSlug: slug,
    nodeType: 'shell',
    children: [layoutChildren(slug, root)],
  }))

  const pageChildren = orderedPageSlugs(config).map((slug) => {
    const page = config.pages[slug]
    return {
      key: `page:${slug}`,
      label: page.title || slug,
      kind: 'page' as const,
      pageSlug: slug,
      nodeType: page.kind,
      children: [layoutChildren(slug, page.root)],
    }
  })

  return [
    { key: 'group:shell', label: '全局壳层', kind: 'group', children: shellChildren },
    { key: 'group:pages', label: '站点页面', kind: 'group', children: pageChildren },
  ]
}

export function structureSelectionKey(pageSlug: string, nodeId: string) {
  return `node:${pageSlug}:${nodeId}`
}

export function fitCanvasZoom(availableWidth: number, canvasWidth: number, horizontalPadding = 56) {
  if (availableWidth <= 0 || canvasWidth <= 0) return 40
  return Math.max(40, Math.min(110, Math.floor(((availableWidth - horizontalPadding) / canvasWidth) * 100)))
}
