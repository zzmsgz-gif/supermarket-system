import { request } from './request'

// 我的券（status: UNUSED/USED/EXPIRED + usable + unusableReason）
export function listMyCoupons() {
  return request('/coupons/mine')
}

// 可领取列表（receivedByCurrentUser 标识是否领过）
export function listAvailableCoupons() {
  return request('/coupons/available')
}

// 领取（路径 id，非 claim）
export function receiveCoupon(id) {
  return request('/coupons/' + id + '/receive', { method: 'POST' })
}

// 结算页可用券（amount 必填）
export function listUsableCoupons(amount) {
  return request('/coupons/usable', { data: { amount } })
}
