import { request } from './request'

// 会员等级表（裸 List<Map>，rate 是折扣率如 0.95）
export function listMemberLevels() {
  return request('/member/levels')
}

// 我的会员信息（points/memberLevel/levelName/discountRate/progressToNext...）
export function getMemberProfile() {
  return request('/member/profile')
}

// 会员日（permitAll）：enabled/days[]/multiplier/slogan/nextDate
export function listMemberDays() {
  return request('/member-days', { auth: false })
}

// 积分明细（分页）。返回 PageResponse<PointLedgerResponse>：
//   type、amount（正负）、balanceAfter、refOrderId、remark、createdAt
// 这是「积分花到哪去了」的唯一数据源，web /points 页用的也是它。
export function listPointLedger(params = {}) {
  return request('/member/ledger', { data: params })
}
