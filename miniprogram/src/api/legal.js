import { request } from './request'

// 协议/隐私正文（permitAll），docKey = terms | privacy
export function getLegalDoc(docKey) {
  return request('/legal-docs/' + docKey, { auth: false })
}

// 协议列表
export function listLegalDocs() {
  return request('/legal-docs', { auth: false })
}
