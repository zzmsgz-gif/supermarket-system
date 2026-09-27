// 订单状态中文标签（与后端 OrderEntity 状态常量对齐）
export function orderStatusLabel(status) {
  const map = {
    PENDING_PAYMENT: '待付款',
    PAID: '待发货',
    SHIPPED: '已发货',
    COMPLETED: '已完成',
    CANCELED: '已取消',
    CLOSED: '已关闭',
    REFUNDING: '退款中',
    REFUNDED: '已退款'
  }
  return map[status] || status || ''
}

// 履约方式中文标签（PICKUP / INSTANT / EXPRESS）
export function fulfillmentLabel(type) {
  const map = {
    PICKUP: '门店自提',
    INSTANT: '同城配送',
    EXPRESS: '快递配送'
  }
  return map[type] || type || ''
}
