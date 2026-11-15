import { isLoggedIn } from '@/store/auth'

/**
 * 「需要登录」操作的统一入口。
 *
 * 为什么不直接 `uni.navigateTo('/pages/login/login')`：
 * 用户刚打开小程序、还没决定要不要买，就被一脚踢到登录页，转化率会很难看。
 * 电商的标准做法是**游客可逛，动作发生时才要求登录** —— 所以这里弹一个可取消的确认框，
 * 用户点「再看看」就留在原地继续浏览。
 *
 * @param {string} action 动作描述，会拼进文案，如「加入购物车」「查看订单」
 * @returns {boolean} 已登录 → true（调用方继续往下走）；未登录 → false（已弹框提示）
 *
 * @example
 * async function addCart() {
 *   if (!requireLogin('加入购物车')) return
 *   await addToCart(...)
 * }
 */
export function requireLogin(action = '继续操作') {
  if (isLoggedIn()) return true
  uni.showModal({
    title: '需要登录',
    content: `登录后才能${action}`,
    confirmText: '去登录',
    cancelText: '再看看',
    success: (r) => {
      if (r.confirm) goLogin()
    }
  })
  return false
}

/** 主动跳登录页（供"去登录"按钮使用） */
export function goLogin() {
  uni.navigateTo({ url: '/pages/login/login' })
}
