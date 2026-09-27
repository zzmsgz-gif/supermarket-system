import { request } from './request'

// 公开轮播（permitAll），点击靠 linkProductId 跳商品详情
export function listBanners() {
  return request('/banners', { auth: false })
}
