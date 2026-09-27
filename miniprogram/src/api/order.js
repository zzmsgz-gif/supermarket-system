import { request } from './request'

// payload: { items: [{ productId, quantity }], channel: 'BALANCE', addressId?, ... }
// 字段名以 backend/src/main/java/.../dto 下 OrderRequest 为准，必要时对齐。
export function createOrder(payload) {
  return request('/orders', { method: 'POST', data: payload })
}

// 后端 POST /orders/{id}/pay 无 body，默认走钱包余额 BALANCE（首期未接入微信支付）
export function payOrder(id) {
  return request('/orders/' + id + '/pay', { method: 'POST' })
}

export function listOrders(params = {}) {
  return request('/orders', { data: params })
}

export function getOrder(id) {
  return request('/orders/' + id)
}
