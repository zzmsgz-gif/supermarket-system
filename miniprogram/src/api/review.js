import { request } from './request'

// 商品评价列表（permitAll），分页结构 { items, total, page, size, pages }
export function listProductReviews(productId, params = {}) {
  return request('/reviews/products/' + productId, { data: params, auth: false })
}

// 某订单的评价（需登录），返回裸 List
export function listOrderReviews(orderId) {
  return request('/reviews/orders/' + orderId)
}

// 提交订单评价（需登录），orderId 在路径，body 只需 rating/content/imageUrls
export function submitReview(orderId, payload) {
  return request('/reviews/orders/' + orderId, { method: 'POST', data: payload })
}
