import { request } from './request'

export function getCart() {
  return request('/cart')
}

// 加购：后端实际路由是 POST /cart/items（注意是 /items 子路径），skuSpec 可选（多规格商品必传）
// 后端 AddCartItemRequest: { productId@NotNull, quantity@NotNull(1-999), skuSpec String(nullable) }
export function addToCart(productId, quantity = 1, skuSpec = '') {
  return request('/cart/items', {
    method: 'POST',
    data: { productId, quantity, skuSpec: skuSpec || undefined }
  })
}

export function updateCartItem(id, quantity) {
  return request('/cart/items/' + id, { method: 'PUT', data: { quantity } })
}

export function removeCartItem(id) {
  return request('/cart/items/' + id, { method: 'DELETE' })
}
