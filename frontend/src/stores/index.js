// Pinia 根 store。在 main.js 中 `app.use(pinia)` 挂载后，
// 各组件即可通过 useCartStore() / useUserStore() 共享状态。
import { createPinia } from 'pinia'

export const pinia = createPinia()
