// 登录态本地存储（uni 存储，等价于 PC 端的 localStorage）
const TOKEN_KEY = 'supermarket_token'
const USER_KEY = 'supermarket_user'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function getStoredUser() {
  const raw = uni.getStorageSync(USER_KEY)
  try {
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

export function setAuth(token, user) {
  uni.setStorageSync(TOKEN_KEY, token)
  uni.setStorageSync(USER_KEY, JSON.stringify(user))
}

/**
 * 局部更新本地缓存的用户对象（合并，不覆盖）。
 *
 * 为什么要它：个人中心、订单页都是读本地缓存里的 user 来渲染昵称/头像的。
 * 资料页改完昵称如果不更新这里，用户返回后会看到"改了但没变"，
 * 得等下次登录（或重新拉 /auth/me）才刷新 —— 看起来就像保存失败。
 *
 * @param {object} patch 要合并的字段，如 { nickname, avatarUrl }
 * @returns {object} 合并后的用户对象
 */
export function updateStoredUser(patch) {
  const current = getStoredUser() || {}
  const next = { ...current, ...(patch || {}) }
  uni.setStorageSync(USER_KEY, JSON.stringify(next))
  return next
}

export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

export function isLoggedIn() {
  return !!getToken()
}
