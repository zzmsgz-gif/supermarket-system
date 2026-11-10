import { request } from './request'

// 我的售后列表（分页）。后端不提供 status 筛选参数，处理中/已完成在前端按状态分组。
// 返回 PageResponse<ReturnRequestResponse>，字段含：
// orderId/orderNo、itemProductName/itemCoverUrl/itemQuantity/itemUnitPrice、
// status + statusLabel（后端已给中文）+ nextStep（后端已给下一步提示）、
// refundAmount、reason/remark/adminRemark、needReturn/expressCompany/trackingNo。
export function listReturns(params = {}) {
  return request('/returns', { data: params })
}

// 某订单的全部售后单（按商品行并行售后时会有多条）
export function listReturnsByOrder(orderId) {
  return request('/returns/order/' + orderId)
}

// 提交售后申请。orderItemId 传具体商品行 => 按行售后；不传则整单。
// type 缺省 "REFUND"（后端默认值）。
export function applyReturn(payload) {
  return request('/returns', { method: 'POST', data: payload })
}

// 填写寄回物流（状态 WAITING_SHIP 时可用）
export function shipBackReturn(id, payload) {
  return request('/returns/' + id + '/ship-back', { method: 'POST', data: payload })
}
