import { authorizedJson } from './client'

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface PageQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
  [key: string]: string | number | boolean | undefined
}

export function getAuthorizedPage<T>(path: string, query: PageQuery, signal?: AbortSignal) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') search.set(key, String(value))
  }
  return authorizedJson<PageResult<T>>(`${path}?${search.toString()}`, { signal })
}
