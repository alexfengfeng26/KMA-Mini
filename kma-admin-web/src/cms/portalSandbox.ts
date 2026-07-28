import type { PortalInlineCode } from './siteConfig'

const CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; connect-src 'none'; base-uri 'none'; form-action 'none'"

const sdk = String.raw`<script>
(() => {
  let port; let sequence = 0; const pending = new Map();
  const request = (type, payload) => new Promise((resolve, reject) => {
    if (!port) return reject(new Error('PORTAL_SDK_UNAVAILABLE'));
    const id = 'portal-' + (++sequence);
    pending.set(id, { resolve, reject }); port.postMessage({ id, type, payload });
  });
  window.addEventListener('message', (event) => {
    if (event.data?.type !== 'kma-sdk-init' || !event.ports[0]) return;
    port = event.ports[0];
    port.onmessage = (event) => {
      const message = event.data || {}; const call = pending.get(message.id); if (!call) return;
      pending.delete(message.id); message.ok ? call.resolve(message.value) : call.reject(message.value || { code: 'SDK_REQUEST_FAILED' });
    };
    window.dispatchEvent(new Event('portal-sdk-ready'));
  });
  window.portal = Object.freeze({
    context: { get: () => request('portal.context.get') },
    contents: { list: (filters = {}) => request('portal.contents.list', filters) },
    search: { query: (keyword) => request('portal.search.query', String(keyword || '')) },
    ask: { submit: (query) => request('portal.ask.submit', String(query || '')) },
    analytics: { track: (event) => request('portal.analytics.track', String(event || '')) },
  });
})();</script>`

/** Builds an opaque-origin preview document. User scripts never receive the host window or network access. */
export function buildInlineSandboxDocument(source: PortalInlineCode): string {
  const html = source.files['index.html'] || '<main></main>'
  const css = source.files['style.css'] || ''
  const js = source.files['main.js'] || ''
  const body = html.replace(/<script\b[^>]*>[\s\S]*?<\/script\s*>/gi, '')
  return `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${CSP}"><style>${css}</style></head><body>${body}${sdk}<script type="module">${js}</script></body></html>`
}

export const defaultInlineCode = (): PortalInlineCode => ({
  files: {
    'index.html': '<main class="kma-inline-widget"><h2>自定义代码区块</h2><p id="portal-context">正在连接门户 SDK…</p></main>',
    'style.css': '.kma-inline-widget{padding:24px;border:1px solid #b8d9ce;border-radius:14px;background:#f2faf7;color:#143d34}.kma-inline-widget h2{margin:0 0 8px}',
    'main.js': "window.addEventListener('portal-sdk-ready', async () => { const context = await window.portal.context.get(); document.querySelector('#portal-context').textContent = context.site.name + ' · ' + context.page; });",
  },
  manifest: { capabilities: ['page-context'] },
})
