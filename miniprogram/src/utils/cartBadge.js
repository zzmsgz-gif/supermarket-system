import { getCart } from '@/api/cart'
import { isLoggedIn } from '@/store/auth'

/**
 * tabBar 购物车角标同步。
 *
 * 为什么需要抽成共享工具：原来只有首页和分类页各写了一份 refreshCartBadge，
 * 结果「购物车页改数量 / 商品详情加购 / 下单后清空 / 从订单页切回来」这些路径全都不更新角标，
 * 用户看到的就是「购物车里有货但 tab 上没有红点」（2026-11-15 用户反馈）。
 *
 * 口径：按 quantity 累加（同一商品 2 件算 2），与购物车页显示的件数一致；
 * 超过 99 显示 99+（微信 tabBar 角标文字不宜过长）。
 */

// tabBar 下标：pages.json tabBar.list 顺序 = 首页 0 / 购物车 1 / 订单 2 / 我的 3
const CART_TAB_INDEX = 1

// 节流：多个页面 onShow 可能前后脚触发，避免同一瞬间重复请求 /cart
const MIN_INTERVAL = 1200
let lastSyncAt = 0
let lastCount = 0

function applyBadge(n) {
  // fail 里什么都不做：非 tabBar 页调用、或 tabBar 还没渲染好时会失败，
  // 这属于正常情况（页面自己的 onShow 会再同步一次），不该弹错误也不该刷控制台
  if (n > 0) {
    uni.setTabBarBadge({
      index: CART_TAB_INDEX,
      text: n > 99 ? '99+' : String(n),
      fail: () => {}
    })
  } else {
    uni.removeTabBarBadge({ index: CART_TAB_INDEX, fail: () => {} })
  }
}

/**
 * 直接用已知件数更新角标（调用方自己已经拿到购物车数据时用，省一次 /cart 请求）。
 * 购物车页的 load() 就走这条：它本来就要拉购物车，改数量/删除后刷新 load 即可顺带把角标带上。
 */
export function applyCartBadge(n) {
  const count = Number(n) || 0
  lastCount = count
  lastSyncAt = Date.now()
  applyBadge(count)
  return count
}

/**
 * 拉一次购物车并把件数写到 tabBar 角标上。
 * @param {boolean} force 跳过节流（加购/改数量/删除/下单后必须传 true 才能立刻生效）
 * @returns {Promise<number>} 当前购物车件数；未登录或请求失败时返回上次的值（首次为 0）
 */
export async function syncCartBadge(force = false) {
  if (!isLoggedIn()) {
    lastCount = 0
    applyBadge(0)
    return 0
  }
  const now = Date.now()
  if (!force && now - lastSyncAt < MIN_INTERVAL) return lastCount
  lastSyncAt = now
  try {
    const cart = await getCart()
    const list = (cart && cart.items) || (Array.isArray(cart) ? cart : [])
    const n = list.reduce((s, it) => s + (Number(it.quantity) || 0), 0)
    lastCount = n
    applyBadge(n)
    return n
  } catch (e) {
    // 角标失败不能影响页面主流程
    return lastCount
  }
}

/** 退出登录时清掉角标 */
export function clearCartBadge() {
  lastCount = 0
  applyBadge(0)
}
