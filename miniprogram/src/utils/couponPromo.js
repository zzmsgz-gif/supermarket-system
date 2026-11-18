import { listUsableCoupons } from '@/api/coupon'

/**
 * 优惠券选择的跨页面共享状态。
 *
 * 为什么需要共享：购物车和结算页都要算「商品金额 − max(券, 活动)」，
 * 如果各页面各自存一份"选了哪张券"，就会出现
 * 「购物车合计 147、结算页应付 157」这种自相矛盾（用户会立刻发现）。
 * 所以统一落本地存储，两边都读写同一份。
 *
 * 口径（与后端 OrderService 完全一致）：
 *   bestPromo = max(券 discountAmount, 活动 activityDiscount)   ← 互斥取大，绝不叠加
 *   应付 = 商品金额 − bestPromo（+ 运费 / − 积分在结算页另外算）
 */

const KEY_SELECTED = 'supermarket_selected_user_coupon'
const KEY_OPT_OUT = 'supermarket_coupon_opted_out'

export function readSelectedCouponId() {
  try {
    const v = uni.getStorageSync(KEY_SELECTED)
    if (v === '' || v == null) return null
    const n = Number(v)
    return Number.isFinite(n) ? n : null
  } catch (e) {
    return null
  }
}

export function writeSelectedCouponId(id) {
  try {
    if (id == null) uni.removeStorageSync(KEY_SELECTED)
    else uni.setStorageSync(KEY_SELECTED, Number(id))
  } catch (e) {
    /* 存不下只是记不住偏好，不影响本次使用 */
  }
}

/** 用户是否显式选了「不使用优惠券」（避免每次都被自动补选最优券） */
export function readOptedOut() {
  try {
    return uni.getStorageSync(KEY_OPT_OUT) === 1
  } catch (e) {
    return false
  }
}

export function writeOptedOut(v) {
  try {
    uni.setStorageSync(KEY_OPT_OUT, v ? 1 : 0)
  } catch (e) {
    /* ignore */
  }
}

/**
 * 拉可用券并定下「当前该用哪张」。
 * @param {number} nonFlashAmount 券门槛的基数 = 非秒杀金额（券不作用于秒杀商品）
 * @returns {Promise<{coupons: Array, selectedId: number|null, selected: object|null, discount: number}>}
 */
export async function loadCouponState(nonFlashAmount) {
  let coupons = []
  try {
    const res = await listUsableCoupons(Number(nonFlashAmount) || 0)
    coupons = Array.isArray(res) ? res : []
  } catch (e) {
    coupons = []
  }

  let selectedId = readSelectedCouponId()
  // 已选的那张不再可用（门槛变了 / 已被用掉 / 过期）→ 清掉，别让界面显示一张用不了的券
  if (selectedId != null && !coupons.some((c) => c.id === selectedId)) {
    selectedId = null
    writeSelectedCouponId(null)
  }
  // 用户没显式放弃、也没选过 → 自动选减免最大的（与 PC 端 autoSelectCoupon 同规则）
  if (!readOptedOut() && selectedId == null && coupons.length) {
    const best = coupons
      .slice()
      .sort((a, b) => Number(b.discountAmount || 0) - Number(a.discountAmount || 0))[0]
    if (best) {
      selectedId = best.id
      writeSelectedCouponId(best.id)
    }
  }

  const selected = coupons.find((c) => c.id === selectedId) || null
  return {
    coupons,
    selectedId,
    selected,
    discount: selected ? Number(selected.discountAmount || 0) : 0
  }
}

/** 退出登录时清掉，否则下一个账号会沿用上一个账号选的券 id */
export function clearCouponSelection() {
  writeSelectedCouponId(null)
  writeOptedOut(false)
}

/**
 * 用户在弹层里选定一张券（或选「不使用优惠券」传 null）后调用：
 * 落存储 + 返回新的 couponState，页面一行 `couponState.value = pickCouponState(state, id)`。
 * 抽出来是为了让购物车与结算页走**完全相同**的写入路径 ——
 * 否则很容易一处写了 opted_out、另一处忘了，导致"取消用券后又被自动补选回去"。
 */
export function pickCouponState(state, id) {
  const coupons = (state && state.coupons) || []
  const selectedId = id == null ? null : Number(id)
  writeSelectedCouponId(selectedId)
  writeOptedOut(selectedId == null)
  const selected = selectedId == null ? null : coupons.find((c) => Number(c.id) === selectedId) || null
  return {
    coupons,
    selectedId,
    selected,
    discount: selected ? Number(selected.discountAmount || 0) : 0
  }
}
