import { request } from './request'

// payload: { items: [{ productId, quantity }], channel: 'BALANCE', addressId?, ... }
// 字段名以 backend/src/main/java/.../dto 下 OrderRequest 为准，必要时对齐。
export function createOrder(payload) {
  return request('/orders', { method: 'POST', data: payload })
}

// 首期走钱包余额支付（无商户号，不做微信支付 JSAPI）
export function payOrder(id, channel = 'BALANCE') {
  return request('/orders/' + id + '/pay', { method: 'POST', data: { channel } })
}

export function listOrders(params = {}) {
  return request('/orders', { data: params })
}

export function getOrder(id) {
  return request('/orders/' + id)
}
