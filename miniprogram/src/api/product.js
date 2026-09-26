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
