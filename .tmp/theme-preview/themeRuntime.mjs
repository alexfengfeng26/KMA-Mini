// kma-admin-web/src/cms/v4/themeRuntime.ts
var escapeHtml = (value) => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
function valueAt(context, path) {
  if (!/^[A-Za-z_][A-Za-z0-9_.]*$/.test(path)) return "";
  return path.split(".").reduce((value, key) => {
    if (!value || typeof value !== "object" || Array.isArray(value)) return void 0;
    return value[key];
  }, context);
}
function renderLiquid(source, context) {
  let result = source;
  const forPattern = /\{%\s*for\s+([A-Za-z_]\w*)\s+in\s+([A-Za-z_][\w.]*)\s*%\}([\s\S]*?)\{%\s*endfor\s*%\}/g;
  result = result.replace(forPattern, (_match, alias, path, body) => {
    const items = valueAt(context, path);
    if (!Array.isArray(items)) return "";
    return items.slice(0, 100).map((item) => renderLiquid(body, { ...context, [alias]: item })).join("");
  });
  const ifPattern = /\{%\s*if\s+([A-Za-z_][\w.]*)\s*%\}([\s\S]*?)(?:\{%\s*else\s*%\}([\s\S]*?))?\{%\s*endif\s*%\}/g;
  result = result.replace(
    ifPattern,
    (_match, path, truthy, falsy = "") => renderLiquid(valueAt(context, path) ? truthy : falsy, context)
  );
  return result.replace(
    /\{\{\s*([A-Za-z_][\w.]*)(?:\s*\|\s*(escape|upcase|downcase|default|truncate)(?::\s*["']?([^}"']+)["']?)?)?\s*\}\}/g,
    (_match, path, filter, argument) => {
      let value = valueAt(context, path);
      if (filter === "default" && !value) value = argument || "";
      if (filter === "upcase") value = String(value ?? "").toUpperCase();
      if (filter === "downcase") value = String(value ?? "").toLowerCase();
      if (filter === "truncate") {
        const length = Math.max(1, Math.min(Number(argument) || 80, 500));
        const text = String(value ?? "");
        value = text.length > length ? `${text.slice(0, length)}\u2026` : text;
      }
      return escapeHtml(value);
    }
  );
}
function expandIncludes(source, files, trail = []) {
  return source.replace(/\{%\s*include\s+['"]([A-Za-z0-9_./-]+)['"]\s*%\}/g, (_match, path) => {
    if (!path.startsWith("partials/") || trail.includes(path) || trail.length >= 10) return "";
    return expandIncludes(files[path] || "", files, [...trail, path]);
  });
}
function contentCards(items) {
  return `<div class="kma-grid">${items.slice(0, 12).map((item) => {
    const value = item;
    const id = value.contentId ?? value.id ?? value.docId ?? "";
    return `<article class="kma-card"><h3>${escapeHtml(value.title)}</h3><p>${escapeHtml(value.summary || value.issuingAuthority || "")}</p><button data-kma-content="${escapeHtml(id)}">\u67E5\u770B\u8BE6\u60C5</button></article>`;
  }).join("")}</div>`;
}
function documentReader(value) {
  if (!value || typeof value !== "object")
    return '<article class="kma-card"><h1>\u8D44\u6599\u6B63\u6587</h1><p>\u6B63\u5728\u901A\u8FC7\u53D7\u63A7\u5185\u5BB9\u80FD\u529B\u52A0\u8F7D\u8D44\u6599\u2026</p></article>';
  const content = value;
  const sections = Array.isArray(content.sections) ? content.sections : [];
  return `<article class="kma-card kma-document">
    <h1>${escapeHtml(content.title)}</h1>
    <p>${escapeHtml(content.summary || "")}</p>
    ${sections.slice(0, 200).map((section) => {
    const item = section;
    return `<section>${escapeHtml(item.content || "")}</section>`;
  }).join("")}
  </article>`;
}
function widgets(source, bootstrap) {
  const withLinks = source.replace(
    /<kma-link\s+[^>]*to\s*=\s*["']([a-z0-9?=&._-]+)["'][^>]*>([\s\S]*?)<\/kma-link>/gi,
    '<a href="#" data-kma-nav="$1">$2</a>'
  ).replace(
    /<kma-action\s+[^>]*name\s*=\s*["']([a-z0-9-]+)["'][^>]*>([\s\S]*?)<\/kma-action>/gi,
    '<button type="button" data-kma-action="$1">$2</button>'
  );
  return withLinks.replace(
    /<kma-widget\s+[^>]*name\s*=\s*["']([a-z0-9-]+)["'][^>]*(?:\/>|>\s*<\/kma-widget>)/gi,
    (_match, name) => {
      if (name === "content-list") return contentCards(bootstrap.portalData.recent);
      if (name === "favorite-list") return contentCards(bootstrap.portalData.favorites);
      if (name === "topic-directory")
        return `<div class="kma-grid">${bootstrap.portalData.topics.map(
          (topic) => `<article class="kma-card"><h3>${escapeHtml(topic.name)}</h3><p>${escapeHtml(topic.description)}</p><button data-kma-topic="${escapeHtml(topic.topicCode)}">\u8FDB\u5165\u4E13\u9898</button></article>`
        ).join("")}</div>`;
      if (name === "ai-chat")
        return '<div class="kma-card kma-ai"><textarea id="kma-ai-query" placeholder="\u8BF7\u8F93\u5165\u95EE\u9898"></textarea><button id="kma-ai-submit">\u57FA\u4E8E\u8D44\u6599\u56DE\u7B54</button><pre id="kma-ai-answer"></pre></div>';
      if (name === "document-reader") return documentReader(bootstrap.themeData?.currentContent);
      if (name === "profile-card")
        return `<article class="kma-card"><h2>\u4E2A\u4EBA\u4E2D\u5FC3</h2><p>${escapeHtml(
          bootstrap.themeData?.user?.displayName || bootstrap.themeData?.user?.username || "\u5F53\u524D\u7528\u6237"
        )}</p><p>\u7528\u6237\u8EAB\u4EFD\u4E0E\u8BBF\u95EE\u6743\u9650\u7531 KMA \u6838\u5FC3\u7CFB\u7EDF\u7BA1\u7406\u3002</p></article>`;
      return "";
    }
  );
}
var sdkScript = String.raw`
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
  const navigate = target => {
    if(navigationPending) return;
    navigationPending=true;
    window.portal.navigation.go(target).catch(()=>{navigationPending=false});
  };
  document.addEventListener('click',event=>{
    const target=event.target instanceof Element ? event.target : null; if(!target)return;
    const nav=target.closest('[data-kma-nav]'); if(nav){event.preventDefault();navigate(nav.dataset.kmaNav);return;}
    const content=target.closest('[data-kma-content]'); if(content){event.preventDefault();if(!navigationPending){navigationPending=true;window.portal.content.open(content.dataset.kmaContent).catch(()=>{navigationPending=false});}return;}
    const topic=target.closest('[data-kma-topic]'); if(topic){event.preventDefault();navigate('topics?topic='+encodeURIComponent(topic.dataset.kmaTopic));}
  });
  document.addEventListener('click',async event=>{
    if(event.target?.id!=='kma-ai-submit')return;
    const query=document.querySelector('#kma-ai-query')?.value||''; const answer=document.querySelector('#kma-ai-answer');
    if(answer)answer.textContent='正在检索资料并生成回答…';
    try{const value=await window.portal.ask.submit(query);if(answer)answer.textContent=value?.answer||JSON.stringify(value,null,2)}
    catch(error){if(answer)answer.textContent=error?.message||error?.code||'回答失败，请稍后重试'}
  });
})();`;
function resolveAssetReferences(source, files) {
  return source.replace(/(["'(])((?:assets)\/[A-Za-z0-9_./-]+)/g, (match, prefix, path) => {
    const content = files[path];
    return content?.startsWith("data:") ? `${prefix}${content}` : match;
  });
}
function resolveModulePath(owner, dependency) {
  const parts = owner.split("/");
  parts.pop();
  for (const segment of dependency.split("/")) {
    if (!segment || segment === ".") continue;
    if (segment === "..") parts.pop();
    else parts.push(segment);
  }
  return parts.join("/");
}
function moduleSource(path, files, trail = []) {
  let source = files[path] || "";
  if (trail.includes(path) || trail.length > 20) return "";
  source = source.replace(
    /(\b(?:import|export)\s+(?:[^'"]*?\s+from\s+)?)(['"])(\.{1,2}\/[^'"]+)\2/g,
    (_match, prefix, _quote, dependency) => {
      const resolved = resolveModulePath(path, dependency);
      const nested = moduleSource(resolved, files, [...trail, path]);
      const uri = `data:text/javascript;charset=utf-8,${encodeURIComponent(nested)}`;
      return `${prefix}"${uri}"`;
    }
  );
  return source;
}
function buildThemeDocument(runtime, bootstrap) {
  const files = runtime.files || {};
  const routeTemplate = "template" in bootstrap.page ? bootstrap.page.template : "pages/home.html";
  const page = expandIncludes(files[routeTemplate] || files["pages/home.html"] || "", files);
  const layout = expandIncludes(files["layout.html"] || "{% slot content %}", files);
  const combined = layout.replace(/\{%\s*slot\s+content\s*%\}/g, page).replace(/<kma-slot\s+[^>]*name\s*=\s*["']content["'][^>]*(?:\/>|>\s*<\/kma-slot>)/gi, page);
  const context = {
    site: bootstrap.site,
    page: bootstrap.page,
    data: bootstrap.portalData,
    recent: bootstrap.portalData.recent,
    topics: bootstrap.portalData.topics,
    favorites: bootstrap.portalData.favorites,
    history: bootstrap.portalData.history,
    user: bootstrap.themeData?.user,
    content: bootstrap.themeData?.currentContent
  };
  const content = resolveAssetReferences(widgets(renderLiquid(combined, context), bootstrap), files);
  const css = resolveAssetReferences(files["styles/theme.css"] || "", files).replaceAll(
    "</style",
    "<\\/style"
  );
  const script = moduleSource("scripts/theme.js", files).replaceAll("<\/script", "<\\/script");
  const csp = "default-src 'none'; script-src 'unsafe-inline' data:; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; connect-src 'none'; base-uri 'none'; form-action 'none'";
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="${csp}"><style>${css}</style></head><body>${content}<script>${sdkScript}<\/script><script type="module">${script}<\/script></body></html>`;
}
export {
  buildThemeDocument
};
