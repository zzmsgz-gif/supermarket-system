// 后端可达地址。
// - 微信小程序（开发者工具/真机）：必须用绝对地址（http://localhost:8080 或局域网IP/域名）
// - H5 浏览器预览：用相对路径 '/api'，由 vite.config.js 的 devServer.proxy 转发到 8080，避免跨域被浏览器拦截
//   判断依据：浏览器环境存在 window；小程序运行时没有 window，故回退绝对地址。
export const API_BASE = (typeof window !== 'undefined') ? '/api' : 'http://localhost:8080'

// 微信小程序 AppID（已提供）
export const APP_ID = 'wx3a6edd169faeba2e'

// 把后端返回的相对路径图片拼成可访问地址。
// 关键：图片资源分两类，不能都走 /api 代理，否则后端 SecurityConfig 未放行的路径会 401。
//  - 种子商品图 / 头像（/seed-products、/seed-avatars）：前端静态目录托管（生产 nginx、本地 vite 中间件），H5 下走同源根路径，切勿叠加 /api。
//  - 上传图（/uploads、/api/uploads）：走后端，H5 经 /api 代理，小程序走接口主机根。
export function fullUrl(u) {
  if (!u) return ''
  if (u.startsWith('http')) return u
  const isBrowser = typeof window !== 'undefined'
  // 数据里已带 /api 前缀（如后台上传的 banner /api/uploads/...），直接同源用，避免叠成 /api/api
  if (u.startsWith('/api/')) return isBrowser ? u : API_BASE + u
  // 前端静态资源前缀：同源根路径（不进 /api 代理）
  if (u.startsWith('/seed-products') || u.startsWith('/seed-avatars')) {
    return isBrowser ? u : API_BASE + u
  }
  // 其余（/uploads 等）走接口主机（H5 经 /api 代理）
  return API_BASE + u
}
