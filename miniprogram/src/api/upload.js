import { API_BASE } from '@/config'
import { getToken } from '@/store/auth'

/**
 * 上传图片。必须用 uni.uploadFile（multipart），不能复用 request.js 里的 uni.request。
 *
 * 后端：POST 路径是 `files/upload`（FileUploadController 类上是 @RequestMapping("files")，
 * 方法上是 @PostMapping("upload")）。⚠️ 拼成 /upload 会 500「No static resource upload」。
 * 查询参数 type = avatar | review | product | category | banner | misc
 * - 需要登录（Authorization 头），头像/评价图普通用户可传，商品/分类图仅管理员
 * - 仅收 JPG / PNG / WEBP / GIF，单文件 ≤10MB
 * - 大图（>3.8MB）后端会自动缩到 1600 宽转 JPEG
 * - 返回 { url }，是相对路径（/api/uploads/...），展示时还要过 config.fullUrl()
 *
 * ⚠️ uni.uploadFile 的 res.data 是**字符串**，不像 uni.request 会自动解析 JSON。
 *
 * @param {string} filePath uni.chooseImage 拿到的临时路径
 * @param {string} type 上传类型，默认 avatar
 * @returns {Promise<string>} 图片相对 URL
 */
export function uploadImage(filePath, type = 'avatar') {
  return new Promise((resolve, reject) => {
    const token = getToken()
    uni.uploadFile({
      url: API_BASE + '/files/upload?type=' + encodeURIComponent(type),
      filePath,
      name: 'file',
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success: (res) => {
        if (res.statusCode === 401) {
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        let body = null
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          reject(new Error('上传返回异常，请重试'))
          return
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(new Error((body && body.message) || `上传失败(${res.statusCode})`))
          return
        }
        if (!body || body.code !== 0) {
          reject(new Error((body && body.message) || '上传失败'))
          return
        }
        const url = (body.data && body.data.url) || ''
        if (!url) {
          reject(new Error('上传成功但没有返回图片地址'))
          return
        }
        resolve(url)
      },
      fail: (err) => {
        reject(new Error((err && err.errMsg) || '上传失败，请检查网络'))
      }
    })
  })
}
