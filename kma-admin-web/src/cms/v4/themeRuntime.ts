import type { PortalBootstrap, PortalThemeRuntime } from '../siteConfig'

const escapeHtml = (value: unknown) =>
  String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')

function valueAt(context: Record<string, unknown>, path: string): unknown {
  if (!/^[A-Za-z_][A-Za-z0-9_.]*$/.test(path)) return ''
  return path.split('.').reduce<unknown>((value, key) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined
    return (value as Record<string, unknown>)[key]
  }, context)
}

function renderLiquid(source: string, context: Record<string, unknown>): string {
  let result = source
  const forPattern = /\{%\s*for\s+([A-Za-z_]\w*)\s+in\s+([A-Za-z_][\w.]*)\s*%\}([\s\S]*?)\{%\s*endfor\s*%\}/g
  result = result.replace(forPattern, (_match, alias: string, path: string, body: string) => {
    const items = valueAt(context, path)
    if (!Array.isArray(items)) return ''
    return items
      .slice(0, 100)
      .map((item) => renderLiquid(body, { ...context, [alias]: item }))
      .join('')
  })
  const ifPattern =
    /\{%\s*if\s+([A-Za-z_][\w.]*)\s*%\}([\s\S]*?)(?:\{%\s*else\s*%\}([\s\S]*?))?\{%\s*endif\s*%\}/g
  result = result.replace(ifPattern, (_match, path: string, truthy: string, falsy = '') =>
    renderLiquid(valueAt(context, path) ? truthy : falsy, context),
  )
  return result.replace(
    /\{\{\s*([A-Za-z_][\w.]*)(?:\s*\|\s*(escape|upcase|downcase|default|truncate)(?::\s*["']?([^}"']+)["']?)?)?\s*\}\}/g,
    (_match, path: string, filter?: string, argument?: string) => {
      let value = valueAt(context, path)
      if (filter === 'default' && !value) value = argument || ''
      if (filter === 'upcase') value = String(value ?? '').toUpperCase()
      if (filter === 'downcase') value = String(value ?? '').toLowerCase()
      if (filter === 'truncate') {
        const length = Math.max(1, Math.min(Number(argument) || 80, 500))
        const text = String(value ?? '')
        value = text.length > length ? `${text.slice(0, length)}…` : text
      }
      return escapeHtml(value)
    },
  )
}

function expandIncludes(source: string, files: Record<string, string>, trail: string[] = []): string {
  return source.replace(/\{%\s*include\s+['"]([A-Za-z0-9_./-]+)['"]\s*%\}/g, (_match, path: string) => {
    if (!path.startsWith('partials/') || trail.includes(path) || trail.length >= 10) return ''
    return expandIncludes(files[path] || '', files, [...trail, path])
  })
}

function contentCards(items: unknown[]): string {
  return `<div class="kma-grid">${items
    .slice(0, 12)
    .map((item) => {
      const value = item as Record<string, unknown>
      const id = value.contentId ?? value.id ?? value.docId ?? ''
      return `<article class="kma-card"><h3>${escapeHtml(value.title)}</h3><p>${escapeHtml(value.summary || value.issuingAuthority || '')}</p><button data-kma-content="${escapeHtml(id)}">查看详情</button></article>`
    })
    .join('')}</div>`
}

function documentReader(value: unknown): string {
  if (!value || typeof value !== 'object')
    return '<article class="kma-card"><h1>资料正文</h1><p>正在通过受控内容能力加载资料…</p></article>'
  const content = value as Record<string, unknown>
  const sections = Array.isArray(content.sections) ? content.sections : []
  return `<article class="kma-card kma-document">
    <h1>${escapeHtml(content.title)}</h1>
    <p>${escapeHtml(content.summary || '')}</p>
    ${sections
      .slice(0, 200)
      .map((section) => {
        const item = section as Record<string, unknown>
        return `<section>${escapeHtml(item.content || '')}</section>`
      })
      .join('')}
  </article>`
}

function widgets(source: string, bootstrap: PortalBootstrap): string {
  const withLinks = source
    .replace(
      /<kma-link\s+[^>]*to\s*=\s*["']([a-z0-9?=&._-]+)["'][^>]*>([\s\S]*?)<\/kma-link>/gi,
      '<a href="#" data-kma-nav="$1">$2</a>',
    )
    .replace(
      /<kma-action\s+[^>]*name\s*=\s*["']([a-z0-9-]+)["'][^>]*>([\s\S]*?)<\/kma-action>/gi,
      '<button type="button" data-kma-action="$1">$2</button>',
    )
  return withLinks.replace(
    /<kma-widget\s+[^>]*name\s*=\s*["']([a-z0-9-]+)["'][^>]*(?:\/>|>\s*<\/kma-widget>)/gi,
    (_match, name: string) => {
      if (name === 'content-list') return contentCards(bootstrap.portalData.recent)
      if (name === 'favorite-list') return contentCards(bootstrap.portalData.favorites)
      if (name === 'topic-directory')
        return `<div class="kma-grid">${bootstrap.portalData.topics
          .map(
            (topic) =>
              `<article class="kma-card"><h3>${escapeHtml(topic.name)}</h3><p>${escapeHtml(topic.description)}</p><button data-kma-topic="${escapeHtml(topic.topicCode)}">进入专题</button></article>`,
          )
          .join('')}</div>`
      if (name === 'ai-chat')
        return '<div class="kma-card kma-ai"><textarea id="kma-ai-query" placeholder="请输入问题"></textarea><button id="kma-ai-submit">基于资料回答</button><pre id="kma-ai-answer"></pre></div>'
      if (name === 'document-reader') return documentReader(bootstrap.themeData?.currentContent)
      if (name === 'profile-card')
        return `<article class="kma-card"><h2>个人中心</h2><p>${escapeHtml(
          (bootstrap.themeData?.user as Record<string, unknown> | undefined)?.displayName ||
            (bootstrap.themeData?.user as Record<string, unknown> | undefined)?.username ||
            '当前用户',
        )}</p><p>用户身份与访问权限由 KMA 核心系统管理。</p></article>`
      return ''
    },
  )
}

const sdkScript = String.raw`
(() => {
  let port; let sequence = 0; let navigationPending = false; const pending = new Map();
  const request = (type, payload, onEvent) => new Promise((resolve, reject) => {
    if (!port) return reject(new Error('PORTAL_SDK_UNAVAILABLE'));
    const id = 'theme-' + (++sequence); pending.set(id,{resolve,reject,onEvent});
    port.postMessage({id,type,payload});
  });
  addEventListener('message', event => {
    if(event.data?.type!=='kma-theme-init'||!event.ports[0]) return;
    port=event.ports[0]; port.onmessage=event=>{
      const call=pending.get(event.data?.id); if(!call)return;
      if(event.data.done===false){ if(call.onEvent)call.onEvent(event.data.value); return; }
      pending.delete(event.data.id);
      event.data.ok?call.resolve(event.data.value):call.reject(event.data.value);
    }; dispatchEvent(new Event('portal-sdk-ready'));
  });
  window.portal=Object.freeze({
    context:{get:()=>request('portal.context.get')},
    data:{query:(query)=>request('portal.data.query',query)},
    navigation:{
      go:(target)=>request('portal.navigation.go',String(target||'')),
      replace:(target)=>request('portal.navigation.replace',String(target||'')),
      back:()=>request('portal.navigation.back')
    },
    search:{query:(value)=>request('portal.search.query',String(value||''))},
    ask:{
      submit:(value)=>request('portal.ask.submit',String(value||'')),
      stream:(value,onEvent)=>request('portal.ask.stream',value,onEvent)
    },
    content:{open:(id)=>request('portal.content.open',String(id||''))},
    analytics:{track:(value)=>request('portal.analytics.track',value)}
  });
  const withNavigationLock = (fn) => {
    if(navigationPending) return;
    navigationPending=true;
    Promise.resolve(fn()).finally(()=>{navigationPending=false});
  };
  const navigate = target => withNavigationLock(() => window.portal.navigation.go(target));
  const openContent = id => withNavigationLock(() => window.portal.content.open(id));
  const openTopic = code => withNavigationLock(() => window.portal.navigation.go('topics?topic='+encodeURIComponent(code)));
  document.addEventListener('click',event=>{
    const target=event.target instanceof Element ? event.target : null; if(!target)return;
    const nav=target.closest('[data-kma-nav]'); if(nav){event.preventDefault();navigate(nav.dataset.kmaNav);return;}
    const content=target.closest('[data-kma-content]'); if(content){event.preventDefault();openContent(content.dataset.kmaContent);return;}
    const topic=target.closest('[data-kma-topic]'); if(topic){event.preventDefault();openTopic(topic.dataset.kmaTopic);}
  });
  document.addEventListener('click',async event=>{
    if(event.target?.id!=='kma-ai-submit')return;
    const query=document.querySelector('#kma-ai-query')?.value||''; const answer=document.querySelector('#kma-ai-answer');
    if(answer)answer.textContent='正在检索资料并生成回答…';
    try{const value=await window.portal.ask.submit(query);if(answer)answer.textContent=value?.answer||JSON.stringify(value,null,2)}
    catch(error){if(answer)answer.textContent=error?.message||error?.code||'回答失败，请稍后重试'}
  });
})();`

function resolveAssetReferences(source: string, files: Record<string, string>): string {
  return source.replace(/(["'(])((?:assets)\/[A-Za-z0-9_./-]+)/g, (match, prefix: string, path: string) => {
    const content = files[path]
    return content?.startsWith('data:') ? `${prefix}${content}` : match
  })
}

function resolveModulePath(owner: string, dependency: string) {
  const parts = owner.split('/')
  parts.pop()
  for (const segment of dependency.split('/')) {
    if (!segment || segment === '.') continue
    if (segment === '..') parts.pop()
    else parts.push(segment)
  }
  return parts.join('/')
}

function moduleSource(path: string, files: Record<string, string>, trail: string[] = []): string {
  let source = files[path] || ''
  if (trail.includes(path) || trail.length > 20) return ''
  source = source.replace(
    /(\b(?:import|export)\s+(?:[^'"]*?\s+from\s+)?)(['"])(\.{1,2}\/[^'"]+)\2/g,
    (_match, prefix: string, _quote: string, dependency: string) => {
      const resolved = resolveModulePath(path, dependency)
      const nested = moduleSource(resolved, files, [...trail, path])
      const uri = `data:text/javascript;charset=utf-8,${encodeURIComponent(nested)}`
      return `${prefix}"${uri}"`
    },
  )
  return source
}

export function buildThemeDocument(runtime: PortalThemeRuntime, bootstrap: PortalBootstrap): string {
  const files = runtime.files || {}
  const routeTemplate = 'template' in bootstrap.page ? bootstrap.page.template : 'pages/home.html'
  const page = expandIncludes(files[routeTemplate] || files['pages/home.html'] || '', files)
  const layout = expandIncludes(files['layout.html'] || '{% slot content %}', files)
  const combined = layout
    .replace(/\{%\s*slot\s+content\s*%\}/g, page)
    .replace(/<kma-slot\s+[^>]*name\s*=\s*["']content["'][^>]*(?:\/>|>\s*<\/kma-slot>)/gi, page)
  const context = {
    site: bootstrap.site,
    page: bootstrap.page,
    data: bootstrap.portalData,
    recent: bootstrap.portalData.recent,
    topics: bootstrap.portalData.topics,
    favorites: bootstrap.portalData.favorites,
    history: bootstrap.portalData.history,
    user: bootstrap.themeData?.user,
    content: bootstrap.themeData?.currentContent,
  }
  const content = resolveAssetReferences(widgets(renderLiquid(combined, context), bootstrap), files)
  const css = resolveAssetReferences(files['styles/theme.css'] || '', files).replaceAll(
    '</style',
    '<\\/style',
  )
  const script = moduleSource('scripts/theme.js', files).replaceAll('</script', '<\\/script')
  const csp =
    "default-src 'none'; script-src 'unsafe-inline' data:; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; connect-src 'none'; base-uri 'none'; form-action 'none'"
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="${csp}"><style>${css}</style></head><body>${content}<script>${sdkScript}</script><script type="module">${script}</script></body></html>`
}
