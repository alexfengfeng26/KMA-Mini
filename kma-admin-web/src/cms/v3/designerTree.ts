import type { LayoutNode, LowCodePage, PortalCoreComponent } from '../siteConfig'
import { findNode, findParent, LOW_CODE_LIMITS, REQUIRED_PAGE_COMPONENT, walkNodes } from './contract'

export type DesignerDropPosition = 'before' | 'inside' | 'after'

export interface DesignerMoveResult {
  moved: boolean
  reason?: string
}

export function cloneNode<T>(value: T): T {
  try {
    return structuredClone(value)
  } catch {
    return JSON.parse(JSON.stringify(value)) as T
  }
}

export function replaceNode(root: LayoutNode, next: LayoutNode): boolean {
  if (root.id === next.id) return false
  const parent = findParent(root, next.id)
  if (!parent || !('children' in parent)) return false
  const index = parent.children.findIndex((child) => child.id === next.id)
  if (index < 0) return false
  parent.children.splice(index, 1, next)
  return true
}

export function insertNode(root: LayoutNode, parentId: string, node: LayoutNode, index?: number): boolean {
  const parent = findNode(root, parentId)
  if (!parent || !('children' in parent) || parent.children.length >= 50) return false
  const target = Math.max(0, Math.min(index ?? parent.children.length, parent.children.length))
  parent.children.splice(target, 0, node)
  return true
}

export function insertNodeAt(
  root: LayoutNode,
  targetId: string,
  position: DesignerDropPosition,
  node: LayoutNode,
): boolean {
  const target = findNode(root, targetId)
  if (!target) return false
  if (position === 'inside') return insertNode(root, targetId, node)
  const parent = findParent(root, targetId)
  if (!parent || !('children' in parent)) return false
  const targetIndex = parent.children.findIndex((child) => child.id === targetId)
  if (targetIndex < 0) return false
  return insertNode(root, parent.id, node, targetIndex + (position === 'after' ? 1 : 0))
}

export function moveNodeAt(
  page: LowCodePage,
  nodeId: string,
  targetId: string,
  position: DesignerDropPosition,
): DesignerMoveResult {
  const source = findNode(page.root, nodeId)
  const target = findNode(page.root, targetId)
  const oldParent = findParent(page.root, nodeId)
  if (!source || !target || !oldParent || !('children' in oldParent))
    return { moved: false, reason: '找不到拖拽节点或目标位置' }
  if (source.id === page.root.id) return { moved: false, reason: '页面根节点不能移动' }
  if (source.locked) return { moved: false, reason: '锁定节点不能移动' }
  const required = REQUIRED_PAGE_COMPONENT[page.kind]
  if (required && source.type === 'component' && source.component === required)
    return { moved: false, reason: '系统必需组件不能移动' }
  if (source.id === target.id) return { moved: false, reason: '不能拖放到节点自身' }
  if (findNode(source, target.id)) return { moved: false, reason: '不能拖入自身的子节点' }

  const nextParent = position === 'inside' ? target : findParent(page.root, target.id)
  if (!nextParent || !('children' in nextParent)) return { moved: false, reason: '目标位置不接受子节点' }
  if (nextParent.children.length >= LOW_CODE_LIMITS.maxChildren && nextParent.id !== oldParent.id)
    return { moved: false, reason: `容器最多包含 ${LOW_CODE_LIMITS.maxChildren} 个节点` }

  const destinationDepth = nodeDepth(page.root, nextParent.id)
  if (destinationDepth + subtreeDepth(source) > LOW_CODE_LIMITS.maxDepth)
    return { moved: false, reason: `布局最多嵌套 ${LOW_CODE_LIMITS.maxDepth} 层` }

  const originalIndex = oldParent.children.findIndex((child) => child.id === source.id)
  oldParent.children.splice(originalIndex, 1)
  let destinationIndex = nextParent.children.length
  if (position !== 'inside') {
    const targetIndex = nextParent.children.findIndex((child) => child.id === target.id)
    destinationIndex = targetIndex + (position === 'after' ? 1 : 0)
  }
  nextParent.children.splice(Math.max(0, destinationIndex), 0, source)
  return { moved: true }
}

export function removeNode(page: LowCodePage, nodeId: string): boolean {
  const node = findNode(page.root, nodeId)
  if (!node || node.locked || node.id === page.root.id) return false
  const required = REQUIRED_PAGE_COMPONENT[page.kind]
  if (required && node.type === 'component' && node.component === required) return false
  const parent = findParent(page.root, nodeId)
  if (!parent || !('children' in parent)) return false
  parent.children = parent.children.filter((child) => child.id !== nodeId)
  return true
}

export function moveNode(root: LayoutNode, nodeId: string, direction: -1 | 1): boolean {
  const parent = findParent(root, nodeId)
  if (!parent || !('children' in parent)) return false
  const current = parent.children.findIndex((child) => child.id === nodeId)
  const target = current + direction
  if (current < 0 || target < 0 || target >= parent.children.length) return false
  const [node] = parent.children.splice(current, 1)
  parent.children.splice(target, 0, node)
  return true
}

export function duplicateNode(root: LayoutNode, nodeId: string): LayoutNode | undefined {
  const source = findNode(root, nodeId)
  const parent = findParent(root, nodeId)
  if (!source || source.locked || !parent || !('children' in parent) || parent.children.length >= 50)
    return undefined
  const copy = cloneNode(source)
  const suffix = Date.now().toString(36)
  walkNodes(copy, (node) => {
    node.id = `${node.id}-${suffix}`.slice(0, 64)
    node.locked = false
  })
  const index = parent.children.findIndex((child) => child.id === nodeId)
  parent.children.splice(index + 1, 0, copy)
  return copy
}

export function pageComponentCount(page: LowCodePage, component: PortalCoreComponent) {
  let count = 0
  walkNodes(page.root, (node) => {
    if (node.type === 'component' && node.component === component) count += 1
  })
  return count
}

export function uniqueNodeId(root: LayoutNode, prefix: string) {
  const ids = new Set<string>()
  walkNodes(root, (node) => ids.add(node.id))
  let candidate = `${prefix}-${Date.now().toString(36)}`.slice(0, 64)
  let sequence = 1
  while (ids.has(candidate)) candidate = `${prefix}-${sequence++}`.slice(0, 64)
  return candidate
}

export function canAcceptChildren(node: LayoutNode) {
  return 'children' in node
}

function nodeDepth(root: LayoutNode, nodeId: string) {
  let depth = 0
  walkNodes(root, (node, currentDepth) => {
    if (node.id === nodeId) depth = currentDepth
  })
  return depth
}

function subtreeDepth(root: LayoutNode) {
  let depth = 1
  walkNodes(root, (_node, currentDepth) => {
    depth = Math.max(depth, currentDepth)
  })
  return depth
}
