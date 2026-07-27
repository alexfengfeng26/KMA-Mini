import type { PortalTemplate } from '../app/runtimeConfig'

const templateStyleLoaders: Record<PortalTemplate, () => Promise<unknown>> = {
  'knowledge-classic': () => import('./knowledge-classic/template.css'),
  'cms-news': () => import('./cms-news/template.css'),
  'reading-focus': () => import('./reading-focus/template.css'),
}

const loadedTemplates = new Set<PortalTemplate>()

export async function loadPortalTemplateStyle(template: PortalTemplate) {
  if (loadedTemplates.has(template)) return
  await templateStyleLoaders[template]()
  loadedTemplates.add(template)
}
