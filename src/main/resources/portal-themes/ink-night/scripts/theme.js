document.documentElement.dataset.themePreset = 'ink-night'

// 隐藏没有数据的区块（如暂无阅读历史、暂无书签）
document.querySelectorAll('[data-auto-hide]').forEach((block) => {
  if (!block.querySelector('button, li')) block.hidden = true
})

// 依据当前页面高亮主导航（page-context 能力不可用时保持静态）
window.addEventListener('portal-sdk-ready', () => {
  window.portal.context.get()
    .then((context) => {
      const slug = context && context.page && context.page.slug ? context.page.slug : 'home'
      const current = slug === 'content' ? 'library' : slug
      document.querySelectorAll('.site-nav [data-kma-nav]').forEach((link) => {
        link.classList.toggle('active', link.dataset.kmaNav === current)
      })
    })
    .catch(() => {})
})
