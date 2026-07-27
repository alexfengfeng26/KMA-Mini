import type { LayoutNode, LowCodePage, PortalCoreComponent } from '../siteConfig'
import { findNode, findParent, REQUIRED_PAGE_COMPONENT, walkNodes } from './contract'

export function cloneNode<T>(value: T): T {
  return structuredClone(value)
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
