import { request } from './request'
import { setAuth, clearAuth, getStoredUser } from '@/store/auth'

// 微信登录：前端 uni.login 拿 code → 后端 /auth/wechat-login 换 JWT（复用现有 JwtAuthenticationFilter）
// 注意：后端该接口尚未实现，需要你提供 wx.secret 后才能调 code2session。
export async function wechatLogin(code) {
  const res = await request('/auth/wechat-login', { method: 'POST', data: { code }, auth: false })
  setAuth(res.token, res.user)
  return res
}

export async function fetchMe() {
  return request('/auth/me')
}

export function logout() {
  clearAuth()
}

export { getStoredUser }
