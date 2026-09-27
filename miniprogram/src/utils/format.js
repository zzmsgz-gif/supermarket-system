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

// 优惠券状态（UserCoupon.status: UNUSED/USED/EXPIRED）
export function couponStatusLabel(status) {
  const map = {
    UNUSED: '未使用',
    USED: '已使用',
    EXPIRED: '已过期'
  }
  return map[status] || status || ''
}

// 秒杀活动状态（FlashSale.state: RUNNING/UPCOMING/ENDED）
export function flashStateLabel(state) {
  const map = {
    RUNNING: '抢购中',
    UPCOMING: '即将开始',
    ENDED: '已结束'
  }
  return map[state] || state || ''
}

// 星级字符串：rating 整数 1~5 → "★★★★☆"
export function starString(rating) {
  const r = Math.max(0, Math.min(5, Number(rating) || 0))
  return '★'.repeat(r) + '☆'.repeat(5 - r)
}

// 金额格式化：统一两位小数字符串（前缀 ¥ 由调用方决定）
export function formatAmount(n) {
  return Number(n || 0).toFixed(2)
}

// 时间格式化：ISO 字符串 → 'YYYY-MM-DD HH:mm'
export function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  const p = (x) => (x < 10 ? '0' + x : '' + x)
  return (
    d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) +
    ' ' + p(d.getHours()) + ':' + p(d.getMinutes())
  )
}

// 日期格式化（会员日等 LocalDate）→ 'YYYY-MM-DD'
export function formatDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  const p = (x) => (x < 10 ? '0' + x : '' + x)
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate())
}

// 把秒数格式化为 mm:ss / hh:mm:ss（秒杀倒计时用）
export function formatCountdown(sec) {
  let s = Math.max(0, Number(sec) || 0)
  const h = Math.floor(s / 3600)
  s -= h * 3600
  const m = Math.floor(s / 60)
  s -= m * 60
  const p = (x) => (x < 10 ? '0' + x : '' + x)
  return h > 0 ? p(h) + ':' + p(m) + ':' + p(s) : p(m) + ':' + p(s)
}
