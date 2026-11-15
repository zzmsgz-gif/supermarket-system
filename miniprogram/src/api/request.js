import { API_BASE } from '@/config'
import { getToken, clearAuth } from '@/store/auth'

// 组装请求头：登录态通过 Bearer token 透传
function authHeader() {
  const header = { 'Content-Type': 'application/json' }
  const t = getToken()
  if (t) header['Authorization'] = 'Bearer ' + t
  return header
}

/**
 * 统一请求封装。
 * - 后端返回统一包装 ApiResponse { code, message, data }，这里默认剥掉外层只回 data。
 * - 401 自动清登录态并 reject（前端据此跳登录）。
 * @param {string} path  接口路径，如 '/products'
 * @param {object} options { method, data, auth, raw }
 *   raw=true 时不剥 ApiResponse，直接返回原始 body（个别接口需要）
 */
export function request(path, options = {}) {
  const { method = 'GET', data = {}, auth = true, raw = false } = options
  // GET 时把 undefined / null / '' 的键剔掉。
  // 为什么必须做：uni.request 会把 `{ categoryId: undefined }` 拼成 `categoryId=`（空值）而不是省略，
  // 后端 `Long categoryId` 收到空串 → MethodArgumentTypeMismatchException →
  // 报「请求参数类型错误」，用户看到的就是"搜索页没结果 + 这个报错"（2026-11-15 实测复现）。
  // 放在封装层统一处理，省得每个页面各自判空。
  let payload = data
  if (method === 'GET' && payload && typeof payload === 'object') {
    payload = {}
    for (const k of Object.keys(data)) {
      const v = data[k]
      if (v === undefined || v === null || v === '') continue
      payload[k] = v
    }
  }
  return new Promise((resolve, reject) => {
    uni.request({
      url: API_BASE + path,
      method,
      data: payload,
      header: auth ? authHeader() : { 'Content-Type': 'application/json' },
      success: (res) => {
        if (res.statusCode === 401) {
          clearAuth()
          reject(new Error((res.data && res.data.message) || '登录已过期，请重新登录'))
          return
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(new Error((res.data && res.data.message) || `请求失败(${res.statusCode})`))
          return
        }
        const body = res.data
        if (raw) {
          resolve(body)
          return
        }
        // 后端统一包装 ApiResponse { code, message, data }
        resolve(body && 'data' in body ? body.data : body)
      },
      fail: (err) => reject(new Error((err && err.errMsg) || '网络错误'))
    })
  })
}
