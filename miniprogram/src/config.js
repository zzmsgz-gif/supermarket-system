// 后端可达地址。
// - 微信开发者工具：在「详情 → 本地设置」勾选「不校验合法域名」，即可用 http://localhost:8080
// - 真机预览：必须填局域网 IP（如 http://192.168.1.x:8080）或已备案的 https 域名
export const API_BASE = 'http://localhost:8080'

// 微信小程序 AppID（已提供）
export const APP_ID = 'wx3a6edd169faeba2e'

// 把后端返回的相对路径图片拼成绝对地址（后端图片走 /api/uploads 或 /uploads）
export function fullUrl(u) {
  if (!u) return ''
  if (u.startsWith('http')) return u
  return API_BASE + u
}
