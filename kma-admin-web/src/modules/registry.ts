import type { FrontendModule } from './contract'

interface ModuleSource {
  default: FrontendModule | FrontendModule[]
}

const moduleSources = import.meta.glob('./**/module.ts', { eager: true }) as Record<string, ModuleSource>

export const frontendModules = Object.values(moduleSources)
  .flatMap((source) => (Array.isArray(source.default) ? source.default : [source.default]))
  .sort((left, right) => left.order - right.order)

export const portalModules = frontendModules.filter((module) => module.shell === 'portal')
export const consoleModules = frontendModules.filter((module) => module.shell === 'console')

export function getFrontendModule(moduleId?: string | null) {
  return moduleId ? frontendModules.find((module) => module.id === moduleId) : undefined
}

export function isFrontendModuleEnabled(
  module: FrontendModule,
  isFeatureEnabled: (featureKey: string, core?: boolean, defaultEnabled?: boolean) => boolean,
) {
  return isFeatureEnabled(module.featureKey, module.core, module.defaultEnabled)
}

export function validateFrontendModuleRegistry(modules = frontendModules) {
  const errors: string[] = []
  const ids = new Set<string>()
  const routeNames = new Set<string>()
  const shellPaths = new Set<string>()
  const navigationPaths = new Set<string>()
  for (const module of modules) {
    if (ids.has(module.id)) errors.push(`模块 ID 重复：${module.id}`)
    ids.add(module.id)
    if (!module.routes.length) errors.push(`模块没有路由：${module.id}`)
    for (const route of module.routes) {
      if (routeNames.has(route.name)) errors.push(`路由名称重复：${route.name}`)
      routeNames.add(route.name)
      const scopedPath = `${module.shell}:${route.path}`
      if (shellPaths.has(scopedPath)) errors.push(`模块路由重复：${scopedPath}`)
      shellPaths.add(scopedPath)
      if (!module.permissions.length && !module.core) errors.push(`非核心模块缺少权限：${module.id}`)
    }
    if (module.navigation) {
      if (navigationPaths.has(module.navigation.path)) errors.push(`导航路径重复：${module.navigation.path}`)
      navigationPaths.add(module.navigation.path)
    }
  }
  return errors
}

const registryErrors = validateFrontendModuleRegistry()
if (registryErrors.length) throw new Error(`KMA 前端模块注册失败：${registryErrors.join('；')}`)
