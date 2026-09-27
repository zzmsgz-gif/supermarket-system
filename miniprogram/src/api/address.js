import { request } from './request'

// 收货地址列表（需登录，返回裸 List，非分页）
export function listAddresses() {
  return request('/addresses')
}

export function createAddress(payload) {
  return request('/addresses', { method: 'POST', data: payload })
}

export function updateAddress(id, payload) {
  return request('/addresses/' + id, { method: 'PUT', data: payload })
}

export function deleteAddress(id) {
  return request('/addresses/' + id, { method: 'DELETE' })
}

// 设为默认（PATCH）
export function setDefaultAddress(id) {
  return request('/addresses/' + id + '/default', { method: 'PATCH' })
}
