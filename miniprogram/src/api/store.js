import { request } from './request'

// GET /stores（permitAll）→ List<StoreResponse>{ id, name, address, phone, businessHours, ... }
// 供结算页「门店自提」选择自提门店
export function listStores() {
  return request('/stores', { auth: false })
}
