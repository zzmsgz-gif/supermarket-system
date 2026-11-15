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

// 修改密码（需登录）。后端校验：原密码正确 / 新密码≠原密码 / 两次新密码一致。
// 说明：微信一键登录建的账号没有密码，这个入口主要给「账号密码登录」的用户用。
export function changePassword(payload) {
  return request('/auth/change-password', { method: 'POST', data: payload })
}

/**
 * 更新个人资料（需登录）。后端 PUT /auth/me，返回更新后的完整用户对象。
 *
 * 后端校验（前端应提前拦，别等报错）：
 * - 昵称按「视觉宽度」≤12 列（中文算 2 列 → 最多 6 个字；英文算 1 列 → 12 个字母）
 * - 生日不能晚于今天
 * - 邮箱 / 手机号不能与其他账号重复（409）
 * - **换绑手机号必须带 verifyToken** —— 项目暂无短信通道，
 *   所以资料页把手机号做成只读，不给改，免得用户点进去只能看到"需要验证"。
 */
export function updateProfile(payload) {
  return request('/auth/me', { method: 'PUT', data: payload })
}

export { getStoredUser }
