import { request } from './request'

// 公开秒杀列表（permitAll，带 JWT 时返回我的占用情况）
export function listFlashSales() {
  return request('/flash-sales', { auth: false })
}
