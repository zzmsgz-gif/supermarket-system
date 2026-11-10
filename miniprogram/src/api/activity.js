import { request } from './request'

// 生效中的活动（满减/折扣/纯文案）。匿名可读，游客也能看到活动宣传。
// 返回 List<ActivityResponse>：
//   id,name,type(FULL_REDUCTION|DISCOUNT|PROMOTION),
//   scope(ALL|CATEGORY|PRODUCT),categoryId,productId,
//   threshold,discount,startTime,endTime,status,priority
// ⚠️ 活动口径必须与后端结算一致，展示逻辑见 utils/activity.js 顶部注释。
export function listActiveActivities() {
  return request('/activities/public', { auth: false })
}
