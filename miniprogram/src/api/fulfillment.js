import { request } from './request'

// 同城即时配送可选时段（permitAll）：List<Map>{ value, label, dayLabel, timeLabel }
export function listDeliverySlots() {
  return request('/fulfillment/delivery-slots', { auth: false })
}
