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

export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

export function isLoggedIn() {
  return !!getToken()
}
