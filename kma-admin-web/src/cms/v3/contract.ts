import type { LayoutNode, LowCodePage, PortalCoreComponent, PortalBreakpoint } from '../siteConfig'

export const LOW_CODE_LIMITS = {
  maxDepth: 8,
  maxNodesPerPage: 300,
  maxChildren: 50,
  maxSandboxPerPage: 5,
} as const

export const BREAKPOINT_COLUMNS: Record<PortalBreakpoint, number> = {
  desktop: 12,
  tablet: 8,
  mobile: 4,
}

export const REQUIRED_PAGE_COMPONENT: Partial<Record<LowCodePage['kind'], PortalCoreComponent>> = {
  library: 'content-results',
  search: 'content-results',
  content: 'document-reader',
  ask: 'ai-conversation',
  topics: 'topic-directory',
  favorites: 'favorite-list',
  profile: 'profile-card',
}

export function nodeChildren(node: LayoutNode): LayoutNode[] {
  return 'children' in node ? node.children : []
}

export function walkNodes(root: LayoutNode, visit: (node: LayoutNode, depth: number) => void) {
  const stack: Array<{ node: LayoutNode; depth: number }> = [{ node: root, depth: 1 }]
  while (stack.length) {
    const current = stack.pop()
    if (!current) continue
    visit(current.node, current.depth)
    const children = nodeChildren(current.node)
    for (let index = children.length - 1; index >= 0; index -= 1) {
      stack.push({ node: children[index], depth: current.depth + 1 })
    }
  }
}

export function findNode(root: LayoutNode, nodeId: string): LayoutNode | undefined {
  let found: LayoutNode | undefined
  walkNodes(root, (node) => {
    if (!found && node.id === nodeId) found = node
  })
  return found
}

export function findParent(root: LayoutNode, nodeId: string): LayoutNode | undefined {
  let parent: LayoutNode | undefined
  walkNodes(root, (node) => {
    if (!parent && nodeChildren(node).some((child) => child.id === nodeId)) parent = node
  })
  return parent
}

export function responsiveValue<T>(
  value: { desktop: T; tablet?: T; mobile?: T } | undefined,
  breakpoint: PortalBreakpoint,
  fallback: T,
) {
  if (!value) return fallback
  if (breakpoint === 'mobile') return value.mobile ?? value.tablet ?? value.desktop
  if (breakpoint === 'tablet') return value.tablet ?? value.desktop
  return value.desktop
}
