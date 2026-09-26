import { request } from './request'

export function getCart() {
  return request('/cart')
}

export function addToCart(productId, quantity = 1) {
  return request('/cart', { method: 'POST', data: { productId, quantity } })
}

export function updateCartItem(id, quantity) {
  return request('/cart/' + id, { method: 'PUT', data: { quantity } })
}

export function removeCartItem(id) {
  return request('/cart/' + id, { method: 'DELETE' })
}
