import { request } from './request'
import { setAuth, clearAuth, getStoredUser } from '@/store/auth'

// 微信登录：前端 uni.login 拿 code → 后端 /auth/wechat-login 换 JWT（复用现有 JwtAuthenticationFilter）
// 后端已实现：WechatService.code2Session + 查/建 sys_user(wx_openid) + 发 JWT（与 PC 密码登录同款）
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
