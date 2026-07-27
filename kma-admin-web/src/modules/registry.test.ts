import { describe, expect, it, vi } from 'vitest'
import {
  consoleModules,
  frontendModules,
  getFrontendModule,
  isFrontendModuleEnabled,
  portalModules,
  validateFrontendModuleRegistry,
} from './registry'

describe('frontend module registry', () => {
  it('keeps module, route and navigation contracts unique', () => {
    expect(frontendModules.length).toBeGreaterThan(20)
    expect(portalModules.length).toBeGreaterThan(5)
    expect(consoleModules.length).toBeGreaterThan(15)
    expect(validateFrontendModuleRegistry()).toEqual([])
  })

  it('binds every route to an owning module and lazy component', () => {
    for (const module of frontendModules) {
      expect(getFrontendModule(module.id)).toBe(module)
      expect(module.featureKey).toBeTruthy()
      for (const route of module.routes) {
        expect(route.path).toBeTruthy()
        expect(route.name).toBeTruthy()
        expect(typeof route.component).toBe('function')
      }
    }
  })

  it('never allows configuration to close a core module', () => {
    const core = portalModules.find((module) => module.core)
    const optional = portalModules.find((module) => !module.core)
    const resolver = vi.fn((_featureKey: string, core?: boolean) => Boolean(core))

    expect(core).toBeDefined()
    expect(optional).toBeDefined()
    expect(isFrontendModuleEnabled(core!, resolver)).toBe(true)
    expect(resolver).toHaveBeenCalledWith(core!.featureKey, true, core!.defaultEnabled)
  })
})
