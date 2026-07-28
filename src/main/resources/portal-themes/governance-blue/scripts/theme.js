document.documentElement.dataset.themePreset = 'governance-blue'

window.addEventListener('portal-sdk-ready', () => {
  // 依据当前页面高亮主导航（page-context 能力不可用时保持静态）
  window.portal.context.get()
    .then((context) => {
      const slug = context && context.page && context.page.slug ? context.page.slug : 'home'
      const current = slug === 'content' ? 'library' : slug
      document.querySelectorAll('.site-nav [data-kma-nav]').forEach((link) => {
        link.classList.toggle('active', link.dataset.kmaNav === current)
      })
    })
    .catch(() => {})

  // 首页检索条：通过受控搜索能力查询并就地渲染结果
  const input = document.querySelector('#gov-search')
  const submit = document.querySelector('#gov-search-submit')
  const results = document.querySelector('#gov-search-results')
  if (!input || !submit || !results) return

  const showMessage = (text) => {
    results.hidden = false
    results.replaceChildren()
    const message = document.createElement('p')
    message.className = 'search-empty'
    message.textContent = text
    results.append(message)
  }

  const render = (payload) => {
    const list = Array.isArray(payload) ? payload : payload && Array.isArray(payload.list) ? payload.list : []
    const usable = list.filter((entry) => entry && entry.title)
    if (!usable.length) {
      showMessage('没有检索到匹配的资料，换个关键词试试。')
      return
    }
    results.hidden = false
    results.replaceChildren()
    usable.slice(0, 8).forEach((entry) => {
      const id = entry.contentId !== undefined ? entry.contentId : entry.docId !== undefined ? entry.docId : entry.id
      const row = document.createElement('button')
      row.type = 'button'
      row.className = 'search-result-row'
      if (id !== undefined && id !== null) row.dataset.kmaContent = String(id)
      const title = document.createElement('strong')
      title.textContent = String(entry.title)
      row.append(title)
      const meta = document.createElement('span')
      meta.textContent = [entry.issuingAuthority, entry.publishDate].filter(Boolean).join(' · ')
      row.append(meta)
      results.append(row)
    })
  }

  const run = () => {
    const value = input.value.trim()
    if (!value) return
    showMessage('正在检索门户资料…')
    window.portal.search.query(value)
      .then(render)
      .catch(() => showMessage('检索失败，请稍后重试。'))
  }

  submit.addEventListener('click', run)
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') run()
  })
})
