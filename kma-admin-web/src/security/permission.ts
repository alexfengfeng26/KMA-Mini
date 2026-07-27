import {
  effectScope,
  shallowRef,
  watchEffect,
  type Directive,
  type DirectiveBinding,
  type EffectScope,
  type ShallowRef,
} from 'vue'
import { useAuthStore } from '../stores/auth'

interface PermissionState {
  scope: EffectScope
  permissions: ShallowRef<string[]>
}

const states = new WeakMap<HTMLElement, PermissionState>()

function required(binding: DirectiveBinding<string | string[]>) {
  if (Array.isArray(binding.value)) return binding.value
  return binding.value ? [binding.value] : []
}

function renderPermission(element: HTMLElement, allowed: boolean) {
  element.style.display = allowed ? '' : 'none'
  element.setAttribute('aria-hidden', allowed ? 'false' : 'true')
}

export const permissionDirective: Directive<HTMLElement, string | string[]> = {
  mounted(element, binding) {
    const scope = effectScope()
    const permissions = shallowRef(required(binding))
    states.set(element, { scope, permissions })
    scope.run(() => {
      const auth = useAuthStore()
      watchEffect(() => renderPermission(element, auth.hasAnyPermission(permissions.value)))
    })
  },
  updated(element, binding) {
    const state = states.get(element)
    if (state) state.permissions.value = required(binding)
  },
  unmounted(element) {
    states.get(element)?.scope.stop()
    states.delete(element)
  },
}
