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
  return new Promise((resolve, reject) => {
    uni.request({
      url: API_BASE + path,
      method,
      data,
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
