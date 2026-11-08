import { request } from './request'

// 系统公告（后台「内容管理 → 公告」维护，匿名可读）。
// 返回字段：id / title / content / type(NOTICE|...) / sortOrder / publishTime / effectiveTime / expiryTime
// 后端已按「启用 + 生效中」过滤，前端拿到即可直接展示。
export function listAnnouncements() {
  return request('/announcements', { auth: false })
}
