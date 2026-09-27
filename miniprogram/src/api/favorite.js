import { request } from './request'

// 我的收藏（分页）
export function listFavorites(params = {}) {
  return request('/favorites', { data: params })
}

// 已收藏商品 id 列表（卡片心形状态用）
export function listFavoriteIds() {
  return request('/favorites/ids')
}

// 收藏 / 取消收藏（路径 productId，分开两个接口，没有 toggle）
export function addFavorite(productId) {
  return request('/favorites/' + productId, { method: 'POST' })
}

export function removeFavorite(productId) {
  return request('/favorites/' + productId, { method: 'DELETE' })
}
