import { request } from './request'

// 消息列表（分页，type 可选）
export function listMessages(params = {}) {
  return request('/messages', { data: params })
}

// 未读数量
export function unreadCount() {
  return request('/messages/unread-count')
}

// 全部已读
export function markAllRead() {
  return request('/messages/read', { method: 'POST' })
}

// 单条已读
export function markRead(id) {
  return request('/messages/' + id + '/read', { method: 'POST' })
}
