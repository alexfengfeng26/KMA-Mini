document.documentElement.dataset.themePreset = 'metro-daily'

// 隐藏没有数据的区块（如暂无收藏、暂无浏览历史）
document.querySelectorAll('[data-auto-hide]').forEach((block) => {
  if (!block.querySelector('button, li')) block.hidden = true
})

let sdkReady = false

// 依据当前页面高亮主导航（page-context 能力不可用时保持静态）
window.addEventListener('portal-sdk-ready', () => {
  sdkReady = true
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

// ---------- 流式问答（问答页） ----------

const chatMessages = document.querySelector('#chat-messages')
const chatInput = document.querySelector('#chat-input')
const chatSend = document.querySelector('#chat-send')

if (chatMessages && chatInput && chatSend) {
  let sessionId
  let streaming = false

  const el = (tag, className, text) => {
    const node = document.createElement(tag)
    if (className) node.className = className
    if (text !== undefined) node.textContent = text
    return node
  }

  const scrollToEnd = () => {
    const last = chatMessages.lastElementChild
    if (last) last.scrollIntoView({ block: 'end' })
  }

  const autogrow = () => {
    chatInput.style.height = 'auto'
    chatInput.style.height = Math.min(chatInput.scrollHeight, 168) + 'px'
  }

  const setStreaming = (flag) => {
    streaming = flag
    chatSend.disabled = flag
    chatSend.textContent = flag ? '回答中…' : '发送'
  }

  const addUserMessage = (text) => {
    const msg = el('div', 'msg user')
    msg.append(el('div', 'msg-body', text))
    chatMessages.append(msg)
    scrollToEnd()
  }

  const addAssistantMessage = () => {
    const msg = el('div', 'msg assistant')
    const status = el('p', 'msg-status', '正在检索资料…')
    const body = el('div', 'msg-body')
    const citations = el('div', 'citations')
    msg.append(status, body, citations)
    chatMessages.append(msg)
    scrollToEnd()
    return { msg, status, body, citations }
  }

  const renderCitations = (container, items) => {
    container.replaceChildren()
    ;(Array.isArray(items) ? items : []).forEach((item, index) => {
      if (!item || !(item.docTitle || item.title)) return
      const card = el('button', 'citation-card')
      card.type = 'button'
      const docId = item.docId !== undefined ? item.docId : item.contentId
      if (docId !== undefined && docId !== null) card.dataset.kmaContent = String(docId)
      card.append(el('span', 'citation-no', String(index + 1)))
      const main = el('span', 'citation-main')
      main.append(el('strong', '', item.docTitle || item.title))
      const meta = [item.issuingAuthority, item.sourceTag].filter(Boolean).join(' · ')
      if (meta) main.append(el('span', 'citation-meta', meta))
      if (item.content) main.append(el('span', 'citation-snippet', String(item.content).slice(0, 90)))
      card.append(main)
      container.append(card)
    })
  }

  const whenReady = () => sdkReady
    ? Promise.resolve()
    : new Promise((resolve) => window.addEventListener('portal-sdk-ready', resolve, { once: true }))

  const send = async (text) => {
    const query = String(text || '').trim()
    if (!query || streaming) return
    await whenReady()
    const welcome = chatMessages.querySelector('.chat-welcome')
    if (welcome) welcome.remove()
    addUserMessage(query)
    chatInput.value = ''
    autogrow()
    setStreaming(true)
    const view = addAssistantMessage()
    let received = false

    const finish = () => {
      view.body.classList.remove('streaming')
      setStreaming(false)
      chatInput.focus()
    }
    const fail = (error) => {
      view.status.hidden = false
      view.status.textContent = (error && error.message) || '回答失败，请稍后重试'
      view.status.classList.add('error')
      const retry = el('button', 'retry-button', '重试')
      retry.type = 'button'
      retry.addEventListener('click', () => {
        view.msg.remove()
        send(query)
      })
      view.msg.append(retry)
      finish()
    }
    const onEvent = (event) => {
      if (!event || !event.kind) return
      if (event.kind === 'citations') {
        view.status.textContent = event.items.length ? '已找到 ' + event.items.length + ' 条出处' : '未检索到直接出处'
        renderCitations(view.citations, event.items)
        scrollToEnd()
      } else if (event.kind === 'delta') {
        if (!received) { received = true; view.status.hidden = true }
        view.body.textContent += event.text
        view.body.classList.add('streaming')
        scrollToEnd()
      } else if (event.kind === 'done') {
        if (event.sessionId) sessionId = event.sessionId
      } else if (event.kind === 'error') {
        view.status.hidden = false
        view.status.textContent = '回答失败：' + (event.message || '请稍后重试')
        view.status.classList.add('error')
      }
    }
    const payload = { query }
    if (sessionId) payload.sessionId = sessionId

    window.portal.ask.stream(payload, onEvent)
      .then(finish)
      .catch((error) => {
        if (error && error.code === 'SDK_CAPABILITY_FORBIDDEN') {
          // 宿主不支持流式时退回一次性问答（结果同样带引用）
          view.status.textContent = '正在生成回答…'
          window.portal.ask.submit(query)
            .then((result) => {
              renderCitations(view.citations, result && result.citations)
              view.status.hidden = true
              view.body.textContent = (result && result.answer) || '未获得回答'
              if (result && result.sessionId) sessionId = result.sessionId
              finish()
            })
            .catch(fail)
        } else fail(error)
      })
  }

  chatSend.addEventListener('click', () => send(chatInput.value))
  chatInput.addEventListener('input', autogrow)
  chatInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      send(chatInput.value)
    }
  })
  document.querySelectorAll('.suggest').forEach((chip) => {
    chip.addEventListener('click', () => send(chip.textContent))
  })
}
