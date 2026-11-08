import { request } from './request'

export function listCategories() {
  return request('/categories')
}

// params: { categoryId, keyword, page, size }
export function listProducts(params = {}) {
  return request('/products', { data: params })
}

export function getProduct(id) {
  return request('/products/' + id)
}

// 热销（按销量倒序）。后端 limit 上限 50。
// 用于购物车空态、详情页"猜你喜欢"这类"没有上下文也要给推荐"的位置。
export function listHotProducts(limit = 10) {
  return request('/products/hot', { data: { limit }, auth: false })
}
