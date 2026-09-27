import { request } from './request'

// 提交订单：CreateOrderRequest
//   { cartItemIds:List<Long>, fulfillmentType, addressId?, pickupStoreId?,
//     deliverySlot?, remark?, userCouponId?, usePoints?, pointsToUse? }
export function createOrder(payload) {
  return request('/orders', { method: 'POST', data: payload })
}

// 快速购买（不走购物车）：QuickBuyRequest
//   { productId, quantity, skuSpec?, fulfillmentType, addressId?, pickupStoreId?,
//     deliverySlot?, remark?, userCouponId?, usePoints?, pointsToUse? }
export function quickBuy(payload) {
  return request('/orders/quick-buy', { method: 'POST', data: payload })
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

// 再来一单：把订单内商品重新加入购物车
// 返回 ReorderResultResponse { addedCount:int, skipped:List<{productName,reason}> }
export function reorder(id) {
  return request('/orders/' + id + '/reorder', { method: 'POST' })
}

// 取消订单（PENDING_PAYMENT 等未支付态）
export function cancelOrder(id) {
  return request('/orders/' + id + '/cancel', { method: 'POST' })
}

// 确认收货（PAID/SHIPPED → COMPLETED）
export function confirmReceipt(id) {
  return request('/orders/' + id + '/confirm-receipt', { method: 'POST' })
}

// 申请退款：body { reason?, description? }
export function refundApply(id, payload = {}) {
  return request('/orders/' + id + '/refund-apply', { method: 'POST', data: payload })
}
