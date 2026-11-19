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

/**
 * 「立即购买」金额预览：与 /orders/quick-buy **同口径**，但只算不落库。
 *
 * 为什么需要：quick 分支没有 GET /cart 可依，活动优惠在前端拿不到（恒为 0），
 * 结算页只减券、收银台却按 max(券,活动) 扣 —— 实测差过 40 元。
 * 前端自己复刻门槛计算必然与服务端分叉，所以直接问后端。
 *
 * 纯只读：不消耗券、不冻结积分、不占秒杀名额，切换优惠券时可反复调用。
 */
export function previewQuickOrder(payload) {
  return request('/orders/preview', { method: 'POST', data: payload })
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
