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
