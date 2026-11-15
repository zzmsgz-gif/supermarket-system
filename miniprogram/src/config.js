// 后端可达地址。
// - H5 浏览器预览：用相对路径 '/api'，由 vite.config.js 的 devServer.proxy 转发到 8080（本地开发用）
// - 微信小程序（开发者工具 / 真机 / 上线）：**统一访问线上环境**，用绝对地址 https://haomart.xyz
//   ⚠️ 绝不能用 localhost —— 真机上 localhost 指向手机自己，接口会全挂（2026-11-07 修正）。
//   若临时想连本机后端调试，把 PROD_API_HOST 改成 'http://<本机局域网IP>:8080' 即可（手机需同 WiFi）。
//
// ⚠️ 2026-11-15 修正：后端 context-path 是 /api，所以接口基址必须带 /api。
//   原来 API_BASE 在小程序环境直接取 PROD_API_HOST（不带 /api），
//   request('/products') 会拼成 https://haomart.xyz/products ——
//   nginx 的 SPA fallback 会返回 index.html（HTTP 200 但 body 是 HTML），
//   wx.request 解析 JSON 全失败 → 小程序里所有列表空白。
//   H5 一直走相对路径 /api 所以从没暴露，只有导入开发者工具/真机才会踩到。
//   注意：静态资源（/seed-products、/seed-avatars、/api/uploads）挂在**域名根**，不能叠 /api，
//   所以拆成 API_ORIGIN（资源用）与 API_BASE（接口用）两个常量。
export const PROD_API_HOST = 'https://haomart.xyz'

// 接口基址 / 静态资源根，用**条件编译**在编译期确定，不做运行时环境探测。
//
// 为什么不写 `typeof window !== 'undefined'`：
// 那是"猜当前平台"。一旦小程序运行时里存在 window（框架 shim、某些调试容器），
// 就会误判成 H5 → API_BASE 变成相对路径 '/api' →
// uni.request 收到非法 url → 所有接口静默失败、页面全空，且线上极难定位。
// 条件编译由 uni-app 在打包时按平台裁剪，H5 包和小程序包各自只留一份常量，行为确定。
// #ifdef H5
export const API_BASE = '/api'
// #endif
// #ifndef H5
export const API_BASE = PROD_API_HOST + '/api'
// #endif

// 静态资源根（图片等），小程序里用来拼域名
export const API_ORIGIN = PROD_API_HOST

// 微信小程序 AppID（已提供）
export const APP_ID = 'wx3a6edd169faeba2e'

// 全站品牌名：首页、登录页统一引用它。
// 2026-11-08 之前小程序叫「好玛特鲜生」、web 端叫「超市购物系统」，两端不一致（用户反馈"名字不对"）。
// ⚠️ pages.json 里的 navigationBarTitleText 是静态 JSON，改名时要一起改。
export const BRAND_NAME = '超市购物系统'

// 把后端返回的相对路径图片拼成可访问地址。
// 关键：图片资源分两类，不能都走 /api 前缀，否则后端 SecurityConfig 未放行的路径会 401。
//  - 种子商品图 / 头像（/seed-products、/seed-avatars）：前端静态目录托管（生产 nginx、本地 vite 中间件），
//    H5 下走同源根路径，小程序里走**域名根**，切勿叠加 /api。
//  - 上传图（/uploads、/api/uploads）：走后端（后端 context-path 是 /api，落盘映射 /uploads/**），
//    H5 经 /api 代理，小程序里要拼成 域名/api/uploads/...
// ⚠️ 所以这里静态资源用 API_ORIGIN（不带 /api），只有最后一条兜底才用 API_BASE（带 /api）。
export function fullUrl(u) {
  if (!u) return ''
  if (u.startsWith('http')) return u
  // #ifdef H5
  // H5：同源，原样返回即可（/api/uploads 走 vite 代理，/seed-products 走静态中间件）
  return u
  // #endif
  // #ifndef H5
  // 小程序：必须拼绝对域名。静态资源挂在域名根，不能叠 /api；
  // /uploads/** 走后端（context-path = /api）所以要带 /api。
  if (u.startsWith('/api/') || u.startsWith('/seed-products') || u.startsWith('/seed-avatars')) {
    return API_ORIGIN + u
  }
  return API_BASE + u
  // #endif
}
