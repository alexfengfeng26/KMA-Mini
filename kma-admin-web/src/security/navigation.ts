import { consoleModules, isFrontendModuleEnabled } from '../modules/registry'
import type { RouteComponent } from 'vue-router'

export type ConsoleNavigationSectionId =
  'governance' | 'knowledge' | 'intelligence' | 'operations' | 'access' | 'platform' | 'account'
export interface ConsoleNavigationItem {
  path: string
  title: string
  badge: string
  section: ConsoleNavigationSectionId
  permissions: string[]
  component: RouteComponent
  meta?: Record<string, unknown>
}
export interface ConsoleNavigationSection {
  id: Exclude<ConsoleNavigationSectionId, 'account'>
  title: string
  items: ConsoleNavigationItem[]
}

export const navigationSectionTitles: ReadonlyArray<Pick<ConsoleNavigationSection, 'id' | 'title'>> = [
  { id: 'governance', title: '党建内容治理' },
  { id: 'knowledge', title: '知识技术治理' },
  { id: 'intelligence', title: '检索与 AI 质量' },
  { id: 'operations', title: '高级运维' },
  { id: 'access', title: '组织与权限' },
  { id: 'platform', title: '平台管理' },
]

export const consoleNavigation: ConsoleNavigationItem[] = consoleModules
  .filter((module) => module.navigation)
  .map((module) => ({
    path: module.navigation!.path,
    title: module.navigation!.label,
    badge: module.navigation!.badge || '·',
    section: module.navigation!.section as ConsoleNavigationSectionId,
    permissions: module.permissions,
    component: module.routes[0].component,
    meta: { moduleId: module.id, ...module.routes[0].meta },
  }))

export function authorizedNavigationSections(
  hasAny: (permissions?: string[]) => boolean,
  isFeatureEnabled: (featureKey: string, core?: boolean, defaultEnabled?: boolean) => boolean = () => true,
): ConsoleNavigationSection[] {
  return navigationSectionTitles
    .map((section) => ({
      ...section,
      items: consoleNavigation.filter((item) => {
        const module = consoleModules.find((candidate) => candidate.id === item.meta?.moduleId)
        return (
          item.section === section.id &&
          hasAny(item.permissions) &&
          (!module || isFrontendModuleEnabled(module, isFeatureEnabled))
        )
      }),
    }))
    .filter((section) => section.items.length > 0)
}
export function accountNavigationItem() {
  return consoleNavigation.find((item) => item.section === 'account')!
}
export function firstAuthorizedPath(
  hasAny: (permissions?: string[]) => boolean,
  isFeatureEnabled: (featureKey: string, core?: boolean, defaultEnabled?: boolean) => boolean = () => true,
) {
  return (
    consoleNavigation.find((item) => {
      const module = consoleModules.find((candidate) => candidate.id === item.meta?.moduleId)
      return (
        item.section !== 'account' &&
        hasAny(item.permissions) &&
        (!module || isFrontendModuleEnabled(module, isFeatureEnabled))
      )
    })?.path || '/console/profile'
  )
}
