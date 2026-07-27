import { authorizedFetch } from './client'

function contentDispositionFilename(value: string | null) {
  if (!value) return undefined
  const encoded = value.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) return decodeURIComponent(encoded)
  return value.match(/filename="?([^";]+)"?/i)?.[1]
}

export async function openAuthorizedFile(path: string) {
  const response = await authorizedFetch(path, { headers: { Accept: '*/*' } })
  if (!response.ok) throw new Error(`原始文件打开失败 (${response.status})`)

  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  const filename = contentDispositionFilename(response.headers.get('content-disposition'))
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.target = '_blank'
  anchor.rel = 'noopener noreferrer'
  if (filename) anchor.download = filename
  document.body.append(anchor)
  anchor.click()
  anchor.remove()

  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000)
}
