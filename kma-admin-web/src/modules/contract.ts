import type { RouteComponent } from 'vue-router'

export type FrontendShell = 'portal' | 'console'

export interface ModuleRoute {
  path: string
  name: string
  component: RouteComponent
  permissions?: string[]
  meta?: Record<string, unknown>
}

export interface ModuleNavigation {
  section?: string
  path: string
  label: string
  badge?: string
}

export interface FrontendModule {
  id: string
  shell: FrontendShell
  featureKey: string
  title: string
  order: number
  core: boolean
  defaultEnabled: boolean
  permissions: string[]
  routes: ModuleRoute[]
  navigation?: ModuleNavigation
}

export function defineFrontendModules(modules: FrontendModule[]) {
  return modules
}
