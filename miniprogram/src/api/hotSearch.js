import { request } from './request'

// 公开热搜词（permitAll）
export function listHotSearches() {
  return request('/hot-searches', { auth: false })
}
