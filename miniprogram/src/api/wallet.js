import { request } from './request'

// 钱包余额
export function getWallet() {
  return request('/wallet')
}

// 直接充值（首期无支付通道，余额直接到账，对齐 PC 端调试充值）
export function recharge(payload) {
  return request('/wallet/recharges', { method: 'POST', data: payload })
}

// 走支付流程的充值订单（有商户号后启用）：返回 RechargeOrderResponse{id,amount,status,expireAt,...}
export function createRechargeOrder(payload) {
  return request('/wallet/recharge-orders', { method: 'POST', data: payload })
}

// 钱包流水（分页）
export function listWalletTransactions(params = {}) {
  return request('/wallet/transactions', { data: params })
}
