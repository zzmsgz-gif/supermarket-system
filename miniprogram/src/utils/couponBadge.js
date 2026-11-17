import { listAvailableCoupons } from '@/api/coupon'
import { isLoggedIn } from '@/store/auth'

/**
 * 「有券可领」红点。
 *
 * 为什么单独抽一个工具而不是写在某个页面里：
 * 用户可能在「我的」「领券中心」任意一个地方领完券，也可能从别的页面切回来 ——
 * 只要红点跟着某一个页面走，就会出现「券已经领完了红点还在」或者
 * 「明明有券可领但没红点」这种前后不一致（购物车角标就踩过同样的坑）。
 *
 * 交互选型：用 **tabBar 红点**（showTabBarRedDot）而不是数字角标 ——
 * 「可领 3 张」这种数字放 tab 上容易被误读成"待处理 3 件事"；
 * 红点只表达「那里有新东西」。具体张数在「我的」页优惠券入口上写明。
 */

// tabBar 下标：pages.json tabBar.list 顺序 = 首页 0 / 购物车 1 / 订单 2 / 我的 3
const MINE_TAB_INDEX = 3

// 节流：首页/我的/领券中心 onShow 可能前后脚触发，避免同一瞬间连打三次接口
const MIN_INTERVAL = 5000
let lastSyncAt = 0
let lastCount = 0

function applyDot(on) {
  // fail 静默：非 tabBar 页、tabBar 未渲染完成时都会失败，属正常情况
  if (on) {
    uni.showTabBarRedDot({ index: MINE_TAB_INDEX, fail: () => {} })
  } else {
    uni.hideTabBarRedDot({ index: MINE_TAB_INDEX, fail: () => {} })
  }
}

/**
 * 可领取张数 = 未领过 且 当日还没领满。
 * 每日券（claimType=1）后端会给 claimedToday，今天领过就不该继续提示。
 */
export function countClaimable(list) {
  return (list || []).filter(
    (c) => c && !c.receivedByCurrentUser && c.claimedToday !== true
  ).length
}

/** 用已知的可领张数直接刷红点（调用方已经拿到列表时用，省一次请求） */
export function applyCouponDot(count) {
  const n = Number(count) || 0
  lastCount = n
  lastSyncAt = Date.now()
  applyDot(n > 0)
  return n
}

/**
 * 拉一次「可领取列表」并同步红点。
 * @param {boolean} force 跳过节流（领完券、切回前台时必须传 true）
 * @returns {Promise<number>} 可领张数；未登录或失败时返回上次的值（首次 0）
 */
export async function syncCouponDot(force = false) {
  if (!isLoggedIn()) {
    lastCount = 0
    applyDot(false)
    return 0
  }
  const now = Date.now()
  if (!force && now - lastSyncAt < MIN_INTERVAL) return lastCount
  lastSyncAt = now
  try {
    const list = await listAvailableCoupons()
    // ⚠️ /coupons/available 返回纯数组；/coupons/mine 才是分页对象，别搞混
    const n = countClaimable(Array.isArray(list) ? list : (list && list.items) || [])
    lastCount = n
    applyDot(n > 0)
    return n
  } catch (e) {
    // 红点失败不能影响页面主流程
    return lastCount
  }
}

/** 退出登录时清掉红点 */
export function clearCouponDot() {
  lastCount = 0
  applyDot(false)
}
