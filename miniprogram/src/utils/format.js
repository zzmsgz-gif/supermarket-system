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

/**
 * 昵称「视觉宽度」计算 —— 必须与后端 util/TextWidth.java 的 columns() 同一套规则，
 * 也必须与 web 端 frontend/src/utils/format.js 的 nicknameColumns() 一致，
 * 否则会出现「前端放行、后端拒」或反过来。
 *
 * 约定：汉字/假名/韩文/全角 = 2 列，英文数字与半角符号 = 1 列。
 * 为什么按宽度而不是字符数：12 个汉字占 24 列会把订单行、评价撑折行，而 12 个字母还很宽松；
 * 按字符数定两套规则（中文 ≤6、英文 ≤12）又判不了「小明Tom」这种混合昵称。
 */
export const NICKNAME_MAX_COLUMNS = 12

export function nicknameColumns(value) {
  const text = String(value || '')
  let width = 0
  for (const ch of text) {
    const cp = ch.codePointAt(0)
    const wide =
      (cp >= 0x1100 && cp <= 0x115f) ||
      (cp >= 0x2e80 && cp <= 0xa4cf) ||
      (cp >= 0xac00 && cp <= 0xd7a3) ||
      (cp >= 0xf900 && cp <= 0xfaff) ||
      (cp >= 0xfe30 && cp <= 0xfe4f) ||
      (cp >= 0xff00 && cp <= 0xff60) ||
      (cp >= 0xffe0 && cp <= 0xffe6)
    width += wide ? 2 : 1
  }
  return width
}

/** 昵称超限的统一文案（与后端 TextWidth.NICKNAME_TOO_WIDE 一致） */
export const NICKNAME_TOO_WIDE = '昵称过长：中文不超过 6 个字，英文不超过 12 个字母'
